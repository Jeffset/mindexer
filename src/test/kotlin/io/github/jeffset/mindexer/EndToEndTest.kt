package io.github.jeffset.mindexer

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.jeffset.mindexer.allowlist.AllowlistExampleGroupsImpl
import io.github.jeffset.mindexer.core.Indexer
import io.github.jeffset.mindexer.core.ResolvingOptions
import io.github.jeffset.mindexer.data.Artifacts
import io.github.jeffset.mindexer.data.IndexDB
import io.github.jeffset.mindexer.data.ListOfStringsAdapter
import kotlinx.coroutines.runBlocking
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
            database = database,
            logger = VerboseLogger,
        ).index()

        // Yeah, until someone publishes an artifact :)
        expect(29174) {
            database.indexDBQueries.artifactsCount().executeAsOne()
        }
    }
}

private fun openDatabaseForTest(): IndexDB {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    IndexDB.Schema.create(driver)
    return IndexDB(driver, Artifacts.Adapter(ListOfStringsAdapter))
}