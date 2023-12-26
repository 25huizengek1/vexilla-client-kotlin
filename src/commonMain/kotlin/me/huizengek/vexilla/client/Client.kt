package me.huizengek.vexilla.client

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.errors.*
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.coroutines.cancellation.CancellationException

private val json = Json {
    isLenient = true
    prettyPrint = true
    ignoreUnknownKeys = true
    encodeDefaults = true
}

private const val manifestError = "The manifest wasn't fetched yet! You should call VexillaClient::getManifest first!"

public class VexillaClient(
    public val baseUrl: String,
    public val environmentName: String = "prod",
    public val customInstanceHash: String? = null,
    private val enableLogging: Boolean = true,
    private val logger: Logger? = null
) : Logger by logger ?: simpleLogger(name = "Vexilla", enabled = enableLogging) {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(json)
        }
        install(Logging) {
            level = if (enableLogging) LogLevel.HEADERS else LogLevel.NONE
        }
    }

    private var manifest: Manifest? = null

    private val featureCache: MutableMap<String, Map<String, String>> = mutableMapOf()
    private val environmentCache: MutableMap<String, Map<String, String>> = mutableMapOf()
    private val flagCache: MutableMap<String, PublishedGroup> = mutableMapOf()

    /**
     * Fetches the Vexilla manifest from the given [baseUrl] and when successful updates the corresponding [manifest] field
     * @return The fetched [Manifest]
     * @throws ResponseException when the server's response is invalid (e.g. non 2xx status code)
     * @throws SerializationException when there was an error parsing the JSON response
     * @throws CancellationException when the parent coroutine was cancelled
     * @throws IOException when there was an (unknown) I/O (e.g. network) error
     */
    @Throws(ResponseException::class, SerializationException::class, CancellationException::class, IOException::class)
    public suspend fun getManifest(): Manifest =
        client.get("$baseUrl/manifest.json") {
            accept(ContentType.Application.Json)
        }.body<Manifest>().also { manifest = it }

    private fun getGroupId(name: String): String {
        val manifest = manifest
        check(manifest != null) { manifestError }

        if (manifest.groups.any { it.id == name }) return name

        val groupId = manifest.lut[name.replace(".json", "")]
        check(groupId != null) { "Manifest group $name not found in the manifest, try refetching the manifest with VexillaClient::getManifest if the group really should exist." }
        return groupId
    }

    /**
     * Fetches the flags with (file) name [fileName] from the given [baseUrl] and when successful caches responses
     * @param fileName The name of the [PublishedGroup]'s file or just the [PublishedGroup]'s name
     * @return The fetched [PublishedGroup]
     * @throws ResponseException when the server's response is invalid (e.g. non 2xx status code)
     * @throws SerializationException when there was an error parsing the JSON response
     * @throws CancellationException when the parent coroutine was cancelled
     * @throws IOException when there was an (unknown) I/O (e.g. network) error
     * @throws IllegalStateException probably thrown when some state isn't fetched/cached yet
     */
    @Throws(
        ResponseException::class,
        SerializationException::class,
        CancellationException::class,
        IOException::class,
        IllegalStateException::class
    )
    public suspend fun getFlags(fileName: String): PublishedGroup {
        val groupId = getGroupId(fileName)

        return client.get("$baseUrl/$groupId.json") {
            accept(ContentType.Application.Json)
        }.body<PublishedGroup>().also {
            featureCache[groupId] = it.featureLut
            environmentCache[groupId] = it.environmentLut
            flagCache[groupId] = it
        }
    }

    /**
     * @return The [Feature] assigned to given [groupName] and [featureName], but returns `null` when the feature is not
     * found.
     * @throws IllegalStateException
     */
    @Throws(IllegalStateException::class)
    public fun getFeature(groupName: String, featureName: String): Feature? {
        require(groupName.isNotBlank()) { "Invalid group name value: $groupName" }
        require(featureName.isNotBlank()) { "Invalid feature name value: $groupName" }

        val groupId = getGroupId(groupName)
        val group = flagCache[groupId]
        if (group == null) {
            warn("VexillaClient::should got called before the flags were fetched for group $group")
            return null
        }

        val environment = environmentCache[groupId]
            ?.get(environmentName)
            ?.let { flagCache[groupId]?.environments?.get(it) }

        if (environment == null) {
            warn("Vexilla couldn't find environment $environmentName for group $group")
            return null
        }

        val feature = featureCache[groupId]?.get(featureName)?.let { environment.features[it] }
        if (feature == null) {
            warn("Feature $feature is not defined for environment $environmentName and group $group")
            return null
        }
        return feature
    }

    /**
     * @return Whether the consumer of the [VexillaClient] [should] do [featureName] for given [groupName],
     * but returns `false` when the feature is not found.
     * @throws IllegalStateException thrown when some state isn't fetched/cached yet
     */
    @Throws(IllegalStateException::class)
    public fun should(groupName: String, featureName: String, customInstanceHash: String? = null): Boolean {
        val feature = getFeature(groupName, featureName) ?: return false
        val currentHash = customInstanceHash ?: this.customInstanceHash

        return when (feature) {
            is Feature.Gradual -> {
                if (feature.seed !in (0.0..1.0)) {
                    err("seed must be a number value greater than 0 and less than or equal to 1")
                    return false
                }
                if (currentHash == null) {
                    err("customInstanceHash must be defined when using the Gradual feature type")
                    return false
                }
                currentHash.hash(feature.seed) <= feature.value * 100
            }

            is Feature.Selective -> when (feature.valueType) {
                ValueType.String -> currentHash in (feature.value.map { it.jsonPrimitive.content })
                ValueType.Number -> currentHash?.toFloatOrNull() in (feature.value.mapNotNull { it.jsonPrimitive.floatOrNull })
            }

            is Feature.Toggle -> feature.value
            is Feature.Value -> {
                warn("The $featureName feature represents a value, not a boolean!")
                false
            }
        }
    }

    /**
     * @return The value assigned to given [groupName] and [featureName]. Returns [defaultValue] when the actual value
     * is missing.
     * @throws IllegalStateException
     */
    @Throws(IllegalStateException::class)
    public inline fun <reified T : Any> value(groupName: String, featureName: String, defaultValue: T): T {
        val feature = getFeature(groupName, featureName) ?: return defaultValue
        if (feature !is Feature.Value) {
            err("Tried calling value on non-value feature.")
            return defaultValue
        }

        return runCatching {
            when (feature.valueType) {
                ValueType.Number -> when (feature.numberType) {
                    NumberType.Integer -> feature.value.toInt()
                    NumberType.Float -> feature.value.toFloat()
                    null -> return defaultValue
                }

                ValueType.String -> feature.value
            } as? T
        }.getOrNull() ?: defaultValue
    }
}
