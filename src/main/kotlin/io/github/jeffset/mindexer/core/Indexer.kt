package io.github.jeffset.mindexer.core

import io.github.jeffset.mindexer.Logger
import io.github.jeffset.mindexer.allowlist.Allowlist
import io.github.jeffset.mindexer.chunked
import io.github.jeffset.mindexer.data.IndexDBHolder
import io.github.jeffset.mindexer.model.Artifact
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/**
 * Uses [Resolver] to resolve the artifacts in accordance with the requests from the [allowlist].
 * Stores the results into the [database].
 *
 * Single use object. Call [index] to do the work.
 */
class Indexer(
    private val allowlist: Allowlist,
    resolveOptions: ResolvingOptions,
    private val database: IndexDBHolder,
    private val logger: Logger,
) {
    private val resolveEvents = MutableSharedFlow<ArtifactResolutionEvent>()

    // We use separate single-thread executor for DB batched transactions as IO is full of network.
    private val dbExecutor = Executors.newSingleThreadExecutor()

    private val resolver = Resolver(
        allowlist = allowlist,
        options = resolveOptions,
        into = resolveEvents,
    )

    suspend fun index() {
        val start = System.currentTimeMillis()
        check(!dbExecutor.isShutdown) { "single-use object" }
        logger.output("Starting indexing using allowlist: ${allowlist.description}")

        val dbDispatcher = dbExecutor.asCoroutineDispatcher()
        coroutineScope {
            launch {
                database.indexDBQueries.truncate()
                resolver.resolveAll()
            }
            resolveEvents
                .takeWhile { it != ArtifactResolutionEvent.ResolutionFinished }
                .chunked(maxCount = 512)
                .collect { events ->
                    val resolvedArtifacts = arrayListOf<Artifact>()
                    for (event in events) {
                        when(event) {
                            is ArtifactResolutionEvent.Resolved -> {
                                logger.verbose("Resolved: ${event.artifact}")
                                resolvedArtifacts.add(event.artifact)
                            }
                            is ArtifactResolutionEvent.Unresolved -> {
                                logger.error("Unresolved: ${event.groupId}:${event.artifactName}")
                            }
                            ArtifactResolutionEvent.ResolutionFinished -> throw AssertionError()
                        }
                    }
                    if (resolvedArtifacts.isNotEmpty()) {
                        launch(dbDispatcher) {
                            database.indexDBQueries.transaction {
                                resolvedArtifacts.forEach { artifact ->
                                    database.indexDBQueries.addArtifact(
                                        group_id = artifact.groupId,
                                        artifact_id = artifact.artifactId,
                                        version = artifact.version,
                                        supported_kmp_platforms = artifact.supportedPlatforms?.toList(),
                                        is_latest = artifact.isLatestVersion,
                                    )
                                }
                                logger.verbose("Written ${resolvedArtifacts.size} entries to DB")
                            }
                        }
                    }
                }
            dbExecutor.shutdown()
        }
        val duration = System.currentTimeMillis() - start
        val seconds = duration / 1000
        val ms = duration % 1000
        logger.output("Indexing finished in $seconds sec $ms ms.")
        logger.output("Index data is saved at ${database.dbFile.absolutePath} and will be used for the 'search' command")
    }
}