package io.github.jeffset.mindexer.data

import java.io.File

class IndexDBHolder(
    val db: IndexDB,
    val dbFile: File,
) {
    val indexDBQueries get() = db.indexDBQueries
}