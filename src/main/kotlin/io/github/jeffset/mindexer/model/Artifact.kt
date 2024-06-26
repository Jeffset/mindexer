package io.github.jeffset.mindexer.model

/**
 * Models the KMP-aware maven artifact data.
 */
data class Artifact(
    /**
     * Maven Group ID (namespace)
     */
    val groupId: String,

    /**
     * Maven Artifact ID.
     *
     * NOTE: For multiplatform artifacts the platform suffix is NOT present here, see [supportedPlatforms] for that.
     */
    val artifactId: String,

    /**
     * Maven artifact version.
     */
    val version: String,

    /**
     * Supported platforms in terms of KMP. `null` if the artifact is not KMP
     *
     * Examples:
     * - `jvm`
     * - `jvmAndroid`
     * - `native:linux_x64`
     * - `native:tvos_simulator_arm64`
     */
    val supportedPlatforms: Set<String>?,
)
