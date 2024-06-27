package io.github.jeffset.mindexer.core

import io.github.jeffset.mindexer.allowlist.Allowlist
import io.github.jeffset.mindexer.model.Artifact
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.serialization.kotlinx.xml.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import nl.adaptivity.xmlutil.ExperimentalXmlUtilApi
import nl.adaptivity.xmlutil.serialization.DefaultXmlSerializationPolicy
import nl.adaptivity.xmlutil.serialization.UnknownChildHandler
import nl.adaptivity.xmlutil.serialization.XmlChildrenName
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName

private const val ATTRIBUTE_KT_PLATFORM_TYPE = "org.jetbrains.kotlin.platform.type"
private const val ATTRIBUTE_KT_NATIVE_TARGET = "org.jetbrains.kotlin.native.target"
private const val MAVEN_CENTRAL_HOST = "repo1.maven.org"

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
                format = @OptIn(ExperimentalXmlUtilApi::class) DefaultXml.copy {
                  policy = DefaultXmlSerializationPolicy.Builder()
                      .apply {
                          pedantic = false
                          unknownChildHandler =
                              UnknownChildHandler { input, inputKind, descriptor, name, candidates ->
                                  emptyList()
                              }
                      }
                      .build()
                },
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
                    if (Allowlist.AllowlistEntry.AllInGroup in allowlistEntries) {
                        resolveAllInGroupScrapingExperimental(
                            groupId = groupId,
                        )
                    } else {
                        for (allowlistEntry in allowlistEntries) when (allowlistEntry) {
                            is Allowlist.AllowlistEntry.Artifact -> {
                                launch {
                                    resolveMatching(
                                        groupId = groupId,
                                        artifactName = allowlistEntry.name,
                                    )
                                }
                            }

                            Allowlist.AllowlistEntry.AllInGroup -> throw AssertionError()
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
        emitUnresolved: Boolean = true,
    ) {
        suspend fun emitUnresolvedIfNeeded() {
            if (emitUnresolved) {
                into.emit(
                    ArtifactResolutionEvent.Unresolved(
                        groupId = groupId,
                        artifactName = artifactName,
                    )
                )
            }
        }

        val response = client.get(
            mavenMetadataUrlFor(
                groupId = groupId,
                artifactName = artifactName,
            )
        )

        if (response.status == HttpStatusCode.NotFound) {
            emitUnresolvedIfNeeded()
            return
        }
        val metadata: MavenMetadata = response.body()
        if (metadata.versioning == null ||
            metadata.groupId == null ||
            metadata.artifactId == null) {
            emitUnresolvedIfNeeded()
            return
        }

        val versions = metadata.versioning.versions
        val latestVersion = metadata.versioning.latest ?: versions.last()
        val artifacts = versions.map { version ->
            Artifact(
                groupId = metadata.groupId,
                artifactId = metadata.artifactId,
                version = version,
                supportedPlatforms = null,
                isLatestVersion = version == latestVersion,
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

    private suspend fun resolveAllInGroupScrapingExperimental(
        groupId: String,
    ) {
        // The groupId might not be a leaf groupId, so every subfolder here we scrape
        // may not be an artifact, but for the sake of time and simplicity we do not handle that
        // here explicitly.

        val url = mavenGroupUrlFor(groupId)
        println(url)
        val response = client.get(url)
        if (response.status == HttpStatusCode.NotFound) {
            // TODO: Say the group is unresolved
            return
        }
        val listingHtml = response.bodyAsText()

        // NOTE:
        // The "good solution" would be to speculatively filter out the platforms,
        // associate them with the "canonical" artifact, do a single ".module" query for it
        // and see that all these platforms are mentioned there.
        // Every unmentioned suffix is in theory an independent artifact which we falsely
        // treated as a variant, so we need to index it separately.

        // However here for the sake of simplicity we just stick with our simple speculation for now.

        coroutineScope {
            SCRAPE_SUBDIRS_HREF_REGEX
                .findAll(listingHtml)
                .map { it.groupValues[1] }
                .filterNot {
                    it.substringAfterLast('-').lowercase() in KMP_ARTIFACT_SUFFIXES ||
                            it.lowercase().endsWith("-wasm-wasi")
                }
                .forEach { artifactName ->
                    launch {
                        resolveMatching(
                            groupId = groupId,
                            artifactName = artifactName,
                            emitUnresolved = false,
                        )
                    }
                }
        }
    }

    private fun gradleModuleUrlFor(
        groupId: String,
        artifactName: String,
        version: String,
    ): Url = URLBuilder(
        protocol = URLProtocol.HTTPS,
        host = MAVEN_CENTRAL_HOST,
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
            host = MAVEN_CENTRAL_HOST,
            pathSegments = buildList {
                add("maven2")
                addAll(groupId.split('.'))
                add(artifactName)
                add("maven-metadata.xml")
            },
        ).build()
    }

    private fun mavenGroupUrlFor(
        groupId: String,
    ): Url {
        return URLBuilder(
            protocol = URLProtocol.HTTPS,
            host = MAVEN_CENTRAL_HOST,
            pathSegments = buildList {
                add("maven2")
                addAll(groupId.split('.'))
                add("")
            },
        ).build()
    }

    @Serializable
    @XmlSerialName("metadata")
    private data class MavenMetadata(
        @XmlElement(true)
        val groupId: String? = null,
        @XmlElement(true)
        val artifactId: String? = null,
        @XmlElement(true)
        val versioning: Versioning? = null,
        val modelVersion: String? = null,
        @XmlElement(true)
        val version: String? = null,
    ) {
        @Serializable
        @XmlSerialName("versioning")
        data class Versioning(
            @XmlElement(true)
            val latest: String? = null,
            @XmlElement(true)
            val release: String? = null,
            @XmlElement(true)
            @XmlSerialName("versions")
            @XmlChildrenName("version")
            val versions: List<String>,
            @XmlElement(true)
            val lastUpdated: String? = null,
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

// Only for literal dirs, no files, no "../", without trailing '/'
private val SCRAPE_SUBDIRS_HREF_REGEX = """href="([a-z0-9-/]+)/"""".toRegex()

private val KMP_ARTIFACT_SUFFIXES = buildSet {
    val nativeArch = arrayOf(
        "arm64", "x64", "arm32", "arm64", "arm32hfp", "x86"
    )

    add("native")
    add("common")   // FIXME: unacceptable amount of false-positives, see note in use-site
    add("metadata") // FIXME: considerable amount of false-positives
    add("jvm")
    add("android")  // Meh
    add("js")
    add("jsir")
    add("wasm")
    add("wasm32")
    add("wasm64")
    for (arch in nativeArch) {
        add("ios$arch")
        add("iossimulator$arch")
        add("androidnative$arch")
        add("linux$arch")
        add("mingw$arch")
        add("watchos$arch")
        add("watchossimulator$arch")
        add("watchosdevice$arch")
        add("macos$arch")
        add("tvos$arch")
        add("tvossimulator$arch")
        add("mingw$arch")
        add("windows$arch")
    }
}
