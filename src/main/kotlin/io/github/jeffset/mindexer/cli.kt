package io.github.jeffset.mindexer

import io.github.jeffset.mindexer.allowlist.AllowlistFileImpl
import io.github.jeffset.mindexer.core.Indexer
import io.github.jeffset.mindexer.core.ResolvingOptions
import io.github.jeffset.mindexer.data.openDatabase
import io.github.jeffset.mindexer.ui.runUi
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand
import kotlinx.cli.default
import kotlinx.cli.required
import kotlinx.coroutines.runBlocking
import java.io.File
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
        actionDescription = "Indexes the remote repository based on allowlist",
    ) {
        val artifactAllowlistFile by option(
            ArgType.String,
            fullName = "artifact-allowlist-file",
            description = """
                Path to the .csv file with the `namespace,name` header
            """.trimIndent()
        ).required()

        val indexKmpAllVersions by option(ArgType.Boolean, fullName = "index-kmp-all-versions",
            description = """
                Whether to resolve KMP platforms for all versions, not only the latest.
                Can significantly increase indexing time.
            """.trimIndent()
        ).default(false)

        override fun execute() {
            runBlocking {
                Indexer(
                    allowlist = AllowlistFileImpl(File(artifactAllowlistFile)),
                    resolveOptions = ResolvingOptions(
                        resolveKmpLatestOnly = !indexKmpAllVersions,
                    ),
                    database = openDatabase(dropExisting = true),
                    logger = if (verbose) VerboseLogger else SilentLogger,
                ).index()
            }
        }
    }

    class SearchCommand : Subcommand(
        name = "search",
        actionDescription = "Searches Maven Central using the built index",
    ) {
        val text by argument(ArgType.String, fullName = "search-text", description = "Search prompt")

        val displayFullNativeTargets by option(
            ArgType.Boolean,
            fullName = "display-full-native-platforms",
            description = """
                Whether to display the complete list of available native targets, instead of just "native".
            """.trimIndent()
        ).default(false)

        override fun execute() {
            val db = openDatabase(dropExisting = false)
            val results = db.indexDBQueries.searchRanked(text).executeAsList()
            if (results.isEmpty()) {
                println("No artifacts match the '$text' prompt")
                exitProcess(1)
            } else {
                println("Found the matching artifacts:")
            }
            results.forEachIndexed { index, result ->
                println("$index) ${result.group_id}:${result.artifact_id}:${result.version}")
                if (result.supported_kmp_platforms != null) {
                    val formatted = result.supported_kmp_platforms
                        .run {
                            if (!displayFullNativeTargets) {
                                // Remove native platform info
                                map { it.substringBefore(':') }.distinct()
                            } else this
                        }
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
            runUi()
        }
        else -> throw AssertionError("Handle command $cmd properly")
    }
}