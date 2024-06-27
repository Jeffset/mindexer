package io.github.jeffset.mindexer.allowlist

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.csv.Csv
import kotlinx.serialization.decodeFromString
import java.io.File
import java.io.IOException

/**
 * Example file:
 * ```csv
 * name,namespace
 * org.kodein.mock,mockmp-test-helper
 * org.jetbrains.kotlinx,*
 * ```
 */
class AllowlistFileImpl(
    allowlistFile: File,
) : Allowlist {
    override val allowed: Map<String, List<Allowlist.AllowlistEntry>>

    override val description: String = "Loaded from $allowlistFile"

    init {
        check(allowlistFile.isFile) { "$allowlistFile doesn't exist or is not readable" }

        val entries = try {
            @OptIn(ExperimentalSerializationApi::class)
            Csv (Csv.Rfc4180) {
                hasHeaderRecord = true
            }.decodeFromString<List<AllowlistEntry>>(allowlistFile.readText())
        } catch (e: IOException) {
            throw IllegalStateException("Unable to read allowlist from $allowlistFile", e)
        } catch (e: SerializationException) {
            throw IllegalStateException("Invalid CSV file: $allowlistFile", e)
        }

        allowed = entries.distinct().groupBy(
            keySelector = AllowlistEntry::namespace,
            valueTransform = {
                when(val name = it.name) {
                    "*" -> Allowlist.AllowlistEntry.AllInGroup
                    else -> Allowlist.AllowlistEntry.Artifact(name)
                }
            }
        )
    }

    @Serializable
    private data class AllowlistEntry(
        val namespace: String,
        val name: String,
    )
}
