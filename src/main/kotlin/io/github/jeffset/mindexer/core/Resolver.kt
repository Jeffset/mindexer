package io.github.jeffset.mindexer.core

import io.github.jeffset.mindexer.allowlist.Allowlist
import io.github.jeffset.mindexer.model.Artifact
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.serialization.kotlinx.xml.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import nl.adaptivity.xmlutil.serialization.XmlChildrenName
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName

private const val ATTRIBUTE_KT_PLATFORM_TYPE = "org.jetbrains.kotlin.platform.type"
private const val ATTRIBUTE_KT_NATIVE_TARGET = "org.jetbrains.kotlin.native.target"

class Resolver(
    private val allowlist: Allowlist,
    private val options: ResolvingOptions,
    private val into: MutableSharedFlow<ArtifactResolutionEvent>,
) {
    private val client = HttpClient(OkHttp) {
        followRedirects = false
        install(ContentNegotiation) {
            val gradleJsonFormat = Json {
                ignoreUnknownKeys = true
                isLenient = true
            }
            xml(
                // this is not SOAP api, but regular files
                contentType = ContentType.Text.Xml,
            )
            json(
                json = gradleJsonFormat,
                // this is not REST api, but regular files
                contentType = ContentType("application", "vnd.org.gradle.module+json")
            )
            json(
                json = gradleJsonFormat,
                // NOTE: Yeah, sometimes .module files have this metadata
                contentType = ContentType("application", "octet-stream")
            )
        }
    }

    suspend fun resolveAll() {
        try {
            coroutineScope {  // Parallel resolution
                for ((groupId, allowlistEntries) in allowlist.allowed) {
                    for (allowlistEntry in allowlistEntries) {
                        when (allowlistEntry) {
                            Allowlist.AllowlistEntry.AllInGroup -> {
                                TODO()
                            }
                            is Allowlist.AllowlistEntry.Artifact -> {
                                launch {
                                    resolveMatching(
                                        groupId = groupId,
                                        artifactName = allowlistEntry.name,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } finally {
            into.emit(ArtifactResolutionEvent.ResolutionFinished)
            client.close()
        }
    }

    private suspend fun resolveMatching(
        groupId: String,
        artifactName: String,
    ) {
        val response = client.get(
            mavenMetadataUrlFor(
                groupId = groupId,
                artifactName = artifactName,
            )
        )

        val metadata: MavenMetadata = response.takeIf { it.status != HttpStatusCode.NotFound }?.body() ?: run {
            into.emit(
                ArtifactResolutionEvent.Unresolved(
                    groupId = groupId,
                    artifactName = artifactName,
                )
            )
            return
        }

        val artifacts = metadata.versioning.versions.map { version ->
            Artifact(
                groupId = metadata.groupId,
                artifactId = metadata.artifactId,
                version = version,
                supportedPlatforms = null,
                isLatestVersion = version == metadata.versioning.latest,
            )
        }

        for (artifact in artifacts) {
            if (!options.resolveKmpLatestOnly || artifact.isLatestVersion) {
                resolveKmpAware(artifact)
            } else {
                into.emit(ArtifactResolutionEvent.Resolved(artifact))
            }
        }
    }

    private suspend fun resolveKmpAware(artifact: Artifact) {
        val moduleUrl = gradleModuleUrlFor(
            groupId = artifact.groupId,
            artifactName = artifact.artifactId,
            version = artifact.version,
        )
        val moduleResponse = client.get(moduleUrl)
        if (moduleResponse.status == HttpStatusCode.NotFound) {
            // Certainly not KMP
            into.emit(ArtifactResolutionEvent.Resolved(artifact))
        } else {
            val gradleModule = moduleResponse.body<GradleModule>()
            val platforms = buildSet {
                for (variant in gradleModule.variants) {
                    // Just record any mention of the platform assuming there's a usable artifact for that
                    val platform = variant.attributes[ATTRIBUTE_KT_PLATFORM_TYPE] ?: continue
                    if (platform == "native") {
                        val target = checkNotNull(variant.attributes[ATTRIBUTE_KT_NATIVE_TARGET]) {
                            "Inconsistent metadata"
                        }
                        add("native:$target")
                    } else {
                        add(platform)
                    }
                }
            }
            into.emit(
                ArtifactResolutionEvent.Resolved(
                    if (platforms.isEmpty()) {
                        artifact
                    } else {
                        artifact.copy(supportedPlatforms = platforms)
                    }
                )
            )
        }
    }

    private fun gradleModuleUrlFor(
        groupId: String,
        artifactName: String,
        version: String,
    ): Url = URLBuilder(
        protocol = URLProtocol.HTTPS,
        host = "repo1.maven.org",
        pathSegments = buildList {
            add("maven2")
            addAll(groupId.split('.'))
            add(artifactName)
            add(version)
            add("$artifactName-$version.module")
        },
    ).build()

    private fun mavenMetadataUrlFor(
        groupId: String,
        artifactName: String,
    ): Url {
        return URLBuilder(
            protocol = URLProtocol.HTTPS,
            host = "repo1.maven.org",
            pathSegments = buildList {
                add("maven2")
                addAll(groupId.split('.'))
                add(artifactName)
                add("maven-metadata.xml")
            },
        ).build()
    }



    @Serializable
    @XmlSerialName("metadata")
    private data class MavenMetadata(
        @XmlElement(true)
        val groupId: String,
        @XmlElement(true)
        val artifactId: String,
        @XmlElement(true)
        val versioning: Versioning,
        val modelVersion: String? = null,
    ) {
        @Serializable
        @XmlSerialName("versioning")
        data class Versioning(
            @XmlElement(true)
            val latest: String,
            @XmlElement(true)
            val release: String,
            @XmlElement(true)
            @XmlSerialName("versions")
            @XmlChildrenName("version")
            val versions: List<String>,
            @XmlElement(true)
            val lastUpdated: String,
        )
    }

    @Serializable
    private class GradleModule(
        val variants: List<Variant>,
    ) {
        @Serializable
        data class Variant(
            val name: String,  // FIXME: Do we need this?
            val attributes: Map<String, String>,
        )
    }
}
