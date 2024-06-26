package io.github.jeffset.mindexer.allowlist

interface Allowlist {
    /**
     * Key is a valid groupId, value is a list of [AllowlistEntry].
     */
    val allowed: Map<String, List<AllowlistEntry>>

    sealed interface AllowlistEntry {
        /**
         * An artifact with the specified [name] is allowed.
         */
        data class Artifact(
            val name: String,
        ) : AllowlistEntry

        /**
         * All artifacts in the group are allowed.
         */
        data object AllInGroup : AllowlistEntry
    }
}