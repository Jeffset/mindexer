package io.github.jeffset.mindexer.core

import io.github.jeffset.mindexer.model.Artifact

sealed interface ArtifactResolutionEvent {
    data class Resolved(
        val artifact: Artifact,
    ) : ArtifactResolutionEvent

    data class Unresolved(
        val groupId: String,
        val artifactName: String,
    ) : ArtifactResolutionEvent

    data object ResolutionFinished : ArtifactResolutionEvent
}