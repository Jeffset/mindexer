package io.github.jeffset.mindexer.allowlist

interface Allowlist {
    /**
     * Key is a valid groupId, value is a list of [AllowlistEntry].
     */
    val allowed: Map<String, List<AllowlistEntry>>

    /**
     * Allowlist user friendly description.
     */
    val description: String

    sealed interface AllowlistEntry {
        /**
         * An artifact with the specified [name] is requested.
         */
        data class Artifact(
            val name: String,
        ) : AllowlistEntry

        /**
         * All artifacts in the group are listed and resolved.
         *
         * NOTE: Nested groups are not resolved.
         */
        data object AllInGroup : AllowlistEntry
    }
}