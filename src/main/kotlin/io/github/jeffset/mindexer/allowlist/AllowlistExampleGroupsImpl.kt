package io.github.jeffset.mindexer.allowlist

import io.github.jeffset.mindexer.allowlist.Allowlist.AllowlistEntry.AllInGroup

object AllowlistExampleGroupsImpl : Allowlist {
    override val allowed: Map<String, List<Allowlist.AllowlistEntry>> = mapOf(
        "io.ktor" to listOf(AllInGroup),
        "org.jetbrains.kotlinx" to listOf(AllInGroup),
        "org.apache.commons" to listOf(AllInGroup),
        "org.apache.tomcat" to listOf(AllInGroup),
        "com.google.dagger" to listOf(AllInGroup),
    )

    override val description: String
        get() = "Builtin Sample Groups: $allowed"
}
