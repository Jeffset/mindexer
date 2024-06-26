package io.github.jeffset.mindexer.data

import app.cash.sqldelight.ColumnAdapter
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File

fun openDatabase(
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
    return IndexDB(driver, Artifacts.Adapter(ListOfStringsAdapter))
}

private object ListOfStringsAdapter : ColumnAdapter<List<String>, String> {
    override fun decode(databaseValue: String) =
        if (databaseValue.isEmpty()) {
            listOf()
        } else {
            databaseValue.split(",")
        }
    override fun encode(value: List<String>) = value.joinToString(separator = ",")
}
