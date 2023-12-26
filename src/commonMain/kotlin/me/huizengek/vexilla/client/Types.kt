@file:OptIn(ExperimentalSerializationApi::class)

package me.huizengek.vexilla.client

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonClassDiscriminator

public const val latestManifestVersion: Int = 1

@Serializable
public data class Manifest(
    public val version: String,
    public val groups: List<Group>
) {
    init {
        require((version.replace("v", "").toIntOrNull() ?: 0) == latestManifestVersion) {
            "Manifest version mismatch. Current: $version - Required: $latestManifestVersion. " +
                    "You must either use an appropriate client or you must update your schema."
        }
    }

    val lut: Map<String, String> by lazy {
        buildMap {
            groups.forEach {
                put(it.id, it.id)
                put(it.name, it.id)
            }
        }
    }

    @Serializable
    public data class Group(
        @SerialName("groupId")
        public val id: String,
        public val name: String
    )
}

@Serializable
public data class PublishedGroup(
    @SerialName("groupId")
    public val id: String,
    public val name: String,
    public val features: Map<String, Feature>,
    public val environments: Map<String, PublishedEnvironment>,
    @SerialName("meta")
    public val metadata: Metadata
) {
    @Serializable
    public data class Metadata(
        public val version: String
    ) {
        init {
            require(version == "v$latestManifestVersion") {
                "Manifest version mismatch. Current: $version - Required: v$latestManifestVersion. " +
                        "You must either use an appropriate client or you must update your schema."
            }
        }
    }

    val featureLut: Map<String, String> by lazy {
        buildMap {
            features.values.forEach {
                put(it.id, it.id)
                put(it.name, it.id)
            }
        }
    }

    val environmentLut: Map<String, String> by lazy {
        buildMap {
            environments.values.forEach {
                put(it.id, it.id)
                put(it.name, it.id)
            }
        }
    }
}

@Serializable
public data class PublishedEnvironment(
    @SerialName("environmentId")
    public val id: String,
    public val name: String,
    public val features: Map<String, Feature>
)

@Serializable
@JsonClassDiscriminator("featureType")
public sealed class Feature {
    @SerialName("featureId")
    public abstract val id: String
    public abstract val name: String
    public abstract val scheduleType: Schedule.Type
    public abstract val schedule: Schedule

    @Serializable
    @SerialName("toggle")
    public data class Toggle(
        @SerialName("featureId")
        override val id: String,
        override val name: String,
        override val scheduleType: Schedule.Type,
        override val schedule: Schedule,
        public val value: Boolean
    ) : Feature()

    @Serializable
    @SerialName("gradual")
    public data class Gradual(
        @SerialName("featureId")
        override val id: String,
        override val name: String,
        override val scheduleType: Schedule.Type,
        override val schedule: Schedule,
        public val value: Double,
        public val seed: Double
    ) : Feature()

    @Serializable
    @SerialName("value")
    public data class Value(
        @SerialName("featureId")
        override val id: String,
        override val name: String,
        override val scheduleType: Schedule.Type,
        override val schedule: Schedule,
        public val value: String,
        public val valueType: ValueType,
        public val numberType: NumberType? = null
    ) : Feature()

    @Serializable
    @SerialName("selective")
    public data class Selective(
        @SerialName("featureId")
        override val id: String,
        override val name: String,
        override val scheduleType: Schedule.Type,
        override val schedule: Schedule,
        public val valueType: ValueType,
        public val value: JsonArray
    ) : Feature()

    @Serializable
    public data class Schedule(
        val start: Long? = null,
        val end: Long? = null,
        val startTime: Long? = null,
        val endTime: Long? = null,
        val timezone: String,
        val timeType: TimeType
    ) {
        @Serializable
        public enum class TimeType {
            @SerialName("daily")
            Daily,

            @SerialName("start/end")
            StartEnd,

            @SerialName("none")
            None
        }

        @Serializable
        public enum class Type {
            @SerialName("global")
            Global,

            @SerialName("environment")
            Environment,

            @SerialName("")
            None
        }
    }
}

@Serializable
public enum class ValueType {
    @SerialName("string")
    String,

    @SerialName("number")
    Number
}

@Serializable
public enum class NumberType {
    @SerialName("int")
    Integer,

    @SerialName("float")
    Float
}
