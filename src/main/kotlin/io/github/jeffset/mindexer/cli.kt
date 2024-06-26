package io.github.jeffset.mindexer

import io.github.jeffset.mindexer.allowlist.AllowlistFileImpl
import io.github.jeffset.mindexer.core.ArtifactResolutionEvent
import io.github.jeffset.mindexer.core.IndexingOptions
import io.github.jeffset.mindexer.core.index
import io.github.jeffset.mindexer.data.createDatabase
import io.github.jeffset.mindexer.model.Artifact
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand
import kotlinx.cli.default
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.concurrent.Executors
import kotlin.system.exitProcess

@OptIn(ExperimentalCli::class)
fun run(args: Array<String>) {
    val parser = ArgParser(
        programName = "mindexer",
        strictSubcommandOptionsOrder = true,
    )
    val verbose by parser.option(ArgType.Boolean, fullName = "verbose", shortName = "v").default(false)

    class IndexCommand : Subcommand(
        name = "index",
        actionDescription = "Indexes the remote repository",
    ) {
        val artifactAllowlistFile by option(ArgType.String, fullName = "artifact-allowlist-file")

        override fun execute() {
            val allowlist = AllowlistFileImpl(File(artifactAllowlistFile!!))
            val options = IndexingOptions(
                indexKmpLatestOnly = true,
            )
            runBlocking {
                val db = createDatabase(dropExisting = true)
                val queries = db.indexDBQueries
                val flow = MutableSharedFlow<ArtifactResolutionEvent>()
                launch {
                    index(
                        allowlist = allowlist,
                        into = flow,
                        options = options,
                    )
                }
                val dbExecutor = Executors.newSingleThreadExecutor()
                val dbDispatcher = dbExecutor.asCoroutineDispatcher()
                flow.takeWhile { it != ArtifactResolutionEvent.ResolutionFinished }
                    .chunked(maxCount = 64)  // Enhance DB performance
                    .collect { events ->
                        val toAdd = arrayListOf<Artifact>()
                        for (event in events) {
                            when(event) {
                                is ArtifactResolutionEvent.Resolved -> {
                                    if (verbose) {
                                        println("Resolved: ${event.artifact}")
                                    }
                                    toAdd.add(event.artifact)
                                }
                                is ArtifactResolutionEvent.Unresolved -> {
                                    System.err.println("Unresolved: ${event.groupId}:${event.artifactName}")
                                }
                                ArtifactResolutionEvent.ResolutionFinished -> throw AssertionError()
                            }
                        }

                        if (toAdd.isNotEmpty()) {
                            launch(dbDispatcher) {
                                queries.transaction {
                                    toAdd.forEach { artifact ->
                                        queries.addArtifact(
                                            group_id = artifact.groupId,
                                            artifact_id = artifact.artifactId,
                                            version = artifact.version,
                                            supported_kmp_platforms = artifact.supportedPlatforms?.joinToString(","),
                                            is_latest = artifact.isLatestVersion,
                                        )
                                    }
                                    println("Added ${toAdd.size} entries to DB")
                                }
                            }
                        }
                        events.asSequence().filterIsInstance<ArtifactResolutionEvent.Unresolved>()
                    }
                dbExecutor.shutdown()
            }
        }
    }

    class SearchCommand : Subcommand(
        name = "search",
        actionDescription = "Searches Maven Central using the built index",
    ) {
        val text by argument(ArgType.String, fullName = "search-text")

        override fun execute() {
            val db = createDatabase(dropExisting = false)
            val results = db.indexDBQueries.searchRanked(text).executeAsList()
            if (results.isEmpty()) {
                println("Not artifacts match the '$text' prompt")
                exitProcess(1)
            } else {
                println("Found the matching artifacts:")
            }
            for (result in results) {
                println("- ${result.group_id}:${result.artifact_id}:${result.version}")
                if (result.supported_kmp_platforms != null) {
                    val formatted = result.supported_kmp_platforms
                        .splitToSequence(',')
                        .map { it.substringBefore(':') }
                        .distinct()
                        .sorted()
                        .joinToString(", ", prefix = "\t KMP: ")
                    println(formatted)
                }
            }
        }
    }

    parser.subcommands(IndexCommand(), SearchCommand())
    val result = parser.parse(args)

    when(val cmd = result.commandName) {
        "index", "search" -> Unit  // Already executed
        "mindexer" -> {
            // Run GUI here
            TODO("Not yet implemented")
        }
        else -> throw AssertionError("Handle command $cmd properly")
    }
}