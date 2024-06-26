package io.github.jeffset.mindexer.core

import io.github.jeffset.mindexer.Logger
import io.github.jeffset.mindexer.allowlist.Allowlist
import io.github.jeffset.mindexer.chunked
import io.github.jeffset.mindexer.data.openDatabase
import io.github.jeffset.mindexer.model.Artifact
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

class Indexer(
    allowlist: Allowlist,
    resolveOptions: ResolvingOptions,
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
        check(!dbExecutor.isShutdown) { "single-use object" }

        val db = openDatabase(dropExisting = true)
        val dbDispatcher = dbExecutor.asCoroutineDispatcher()
        coroutineScope {
            launch {
                resolver.resolveAll()
            }
            resolveEvents
                .takeWhile { it != ArtifactResolutionEvent.ResolutionFinished }
                .chunked(maxCount = 64)
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
                            db.indexDBQueries.transaction {
                                resolvedArtifacts.forEach { artifact ->
                                    db.indexDBQueries.addArtifact(
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
            logger.verbose("Indexing finished")
            dbExecutor.shutdown()
        }
    }
}