package io.github.jeffset.mindexer

import io.github.jeffset.mindexer.allowlist.AllowlistFileImpl
import io.github.jeffset.mindexer.core.ArtifactResolutionEvent
import io.github.jeffset.mindexer.core.index
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File

@OptIn(ExperimentalCli::class)
fun run(args: Array<String>) {
    val parser = ArgParser(
        programName = "mindexer",
        strictSubcommandOptionsOrder = true,
    )

    class IndexCommand : Subcommand(
        name = "index",
        actionDescription = "Indexes the remote repository",
    ) {
        val artifactAllowlistFile by option(ArgType.String, fullName = "artifact-allowlist-file")

        override fun execute() {
            val allowlist = AllowlistFileImpl(File(artifactAllowlistFile!!))
            runBlocking {
                val flow = MutableSharedFlow<ArtifactResolutionEvent>()
                launch {
                    index(
                        allowlist = allowlist,
                        into = flow,
                    )
                }
                flow.takeWhile { it != ArtifactResolutionEvent.ResolutionFinished }.collect { event ->
                    when(event) {
                        is ArtifactResolutionEvent.Resolved -> {
                            println("Resolved: ${event.artifact}")
                        }
                        is ArtifactResolutionEvent.Unresolved -> {
                            println("Unresolved: ${event.groupId}:${event.artifactName}")
                        }
                        ArtifactResolutionEvent.ResolutionFinished -> throw AssertionError()
                    }
                }
            }
        }
    }

    class SearchCommand : Subcommand(
        name = "search",
        actionDescription = "Searches Maven Central using the built index",
    ) {
        override fun execute() {
            TODO("Not yet implemented")
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