package io.github.jeffset.mindexer

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.jeffset.mindexer.allowlist.AllowlistExampleGroupsImpl
import io.github.jeffset.mindexer.allowlist.AllowlistFileImpl
import io.github.jeffset.mindexer.core.Indexer
import io.github.jeffset.mindexer.core.ResolvingOptions
import io.github.jeffset.mindexer.data.Artifacts
import io.github.jeffset.mindexer.data.IndexDB
import io.github.jeffset.mindexer.data.IndexDBHolder
import io.github.jeffset.mindexer.data.ListOfStringsAdapter
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.expect

class EndToEndTest {
    @Test
    fun `test default config`() = runBlocking {
        val database = openDatabaseForTest()
        Indexer(
            allowlist = AllowlistExampleGroupsImpl,
            resolveOptions = ResolvingOptions(
                resolveKmpLatestOnly = true,
            ),
            database = IndexDBHolder(database, File("")),
            logger = SilentLogger,  // Change to VerboseLogger to debug
        ).index()

        // Yeah, until someone publishes an artifact :)
        expect(29174) {
            database.indexDBQueries.artifactsCount().executeAsOne()
        }
    }

    @Test
    fun `test example config`() = runBlocking {
        val database = openDatabaseForTest()

        Indexer(
            allowlist = AllowlistFileImpl(File("data/maven-kmp-libraries.csv")),
            resolveOptions = ResolvingOptions(
                resolveKmpLatestOnly = true,
            ),
            database = IndexDBHolder(database, File("")),
            logger = SilentLogger,
        ).index()

        // Not very long-lasting test, but OK for toy example
        expect(File("data/ktor-search.golden.txt").readText().trimMargin()) {
            database.indexDBQueries.searchRanked(
                namePrompt = "ktor",
                platformPrompt = "js",
            ).executeAsList().joinToString(separator = "\n") {
                "${it.group_id}:${it.artifact_id}:${it.version}:${it.supported_kmp_platforms}"
            }
        }
    }
}

private fun openDatabaseForTest(): IndexDB {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    IndexDB.Schema.create(driver)
    return IndexDB(driver, Artifacts.Adapter(ListOfStringsAdapter))
}