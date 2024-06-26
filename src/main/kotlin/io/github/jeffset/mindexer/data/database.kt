package io.github.jeffset.mindexer.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File

fun createDatabase(
    dropExisting: Boolean = false,
): IndexDB {
    val homePath = File(System.getProperty("user.home")).resolve(".mindexer")
    homePath.mkdirs()
    val dbPath = homePath.resolve("index.db")
    if (dropExisting) {
        dbPath.delete()
    }
    val driver = JdbcSqliteDriver("jdbc:sqlite:$dbPath")
    IndexDB.Schema.create(driver)
    return IndexDB(driver)
}
