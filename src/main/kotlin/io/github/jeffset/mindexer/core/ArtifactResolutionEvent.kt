package io.github.jeffset.mindexer.core

import io.github.jeffset.mindexer.model.Artifact

/**
 * Used within event flow in [Resolver].
 */
sealed interface ArtifactResolutionEvent {
    /**
     * Artifact is successfully resolved.
     */
    data class Resolved(
        val artifact: Artifact,
    ) : ArtifactResolutionEvent

    /**
     * Artifact was explicitly requested and was not resolved.
     * Is not issued as a part of [io.github.jeffset.mindexer.allowlist.Allowlist.AllowlistEntry.AllInGroup] request.
     */
    data class Unresolved(
        val groupId: String,
        val artifactName: String,
    ) : ArtifactResolutionEvent

    /**
     * Special signal object to interrupt the flow.
     */
    data object ResolutionFinished : ArtifactResolutionEvent
}