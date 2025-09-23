/*
 * Copyright (c) 2025 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.app.statistics.wideevents.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ProvidedTypeConverter
import androidx.room.TypeConverter
import com.squareup.moshi.FromJson
import com.squareup.moshi.Json
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import com.squareup.moshi.Types
import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

@Entity(tableName = "wide_events")
data class WideEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Instant,

    @ColumnInfo(name = "flow_entry_point")
    val flowEntryPoint: String?,

    @ColumnInfo(name = "metadata")
    val metadata: List<MetadataEntry>,

    @ColumnInfo(name = "steps")
    val steps: List<WideEventStep>,

    @ColumnInfo(name = "status")
    val status: WideEventStatus?,

    @ColumnInfo(name = "cleanup_policy")
    val cleanupPolicy: CleanupPolicy?,

    @ColumnInfo(name = "active_intervals")
    val activeIntervals: List<WideEventInterval>,
) {
    data class MetadataEntry(
        @Json(name = "key")
        val key: String,

        @Json(name = "value")
        val value: String?,
    )

    data class WideEventStep(
        @Json(name = "name")
        val name: String,

        @Json(name = "success")
        val success: Boolean,
    )

    enum class WideEventStatus(val statusCode: String) {
        SUCCESS("success"),
        FAILURE("failure"),
        CANCELLED("cancelled"),
        UNKNOWN("unknown"),
    }

    sealed class CleanupPolicy {
        abstract val status: WideEventStatus
        abstract val metadata: Map<String, String>

        data class OnProcessStart(
            @Json(name = "ignore_if_interval_timeout_present")
            val ignoreIfIntervalTimeoutPresent: Boolean,

            @Json(name = "status")
            override val status: WideEventStatus,

            @Json(name = "metadata")
            override val metadata: Map<String, String> = emptyMap(),
        ) : CleanupPolicy()

        data class OnTimeout(
            @Json(name = "duration")
            val duration: Duration,

            @Json(name = "status")
            override val status: WideEventStatus,

            @Json(name = "metadata")
            override val metadata: Map<String, String> = emptyMap(),
        ) : CleanupPolicy()
    }

    data class WideEventInterval(
        @Json(name = "name")
        val name: String,

        @Json(name = "started_at")
        val startedAt: Instant,

        @Json(name = "timeout")
        val timeout: Duration?,
    )
}

@ProvidedTypeConverter
class WideEventEntityTypeConverters @Inject constructor(
    globalMoshi: Moshi,
) {
    private val moshi: Moshi by lazy {
        globalMoshi.newBuilder()
            .add(
                PolymorphicJsonAdapterFactory.of(WideEventEntity.CleanupPolicy::class.java, "type")
                    .withSubtype(
                        WideEventEntity.CleanupPolicy.OnProcessStart::class.java,
                        "OnProcessStart",
                    )
                    .withSubtype(
                        WideEventEntity.CleanupPolicy.OnTimeout::class.java,
                        "OnTimeout",
                    ),
            )
            .add(WideEventStatusJsonAdapter())
            .add(InstantMillisAdapter())
            .add(DurationMillisAdapter())
            .build()
    }

    private val metadataEntryListAdapter: JsonAdapter<List<WideEventEntity.MetadataEntry>> by lazy {
        moshi.adapter(
            Types.newParameterizedType(
                List::class.java,
                WideEventEntity.MetadataEntry::class.java,
            ),
        )
    }

    private val wideEventStepListAdapter: JsonAdapter<List<WideEventEntity.WideEventStep>> by lazy {
        moshi.adapter(
            Types.newParameterizedType(
                List::class.java,
                WideEventEntity.WideEventStep::class.java,
            ),
        )
    }

    private val cleanupPolicyAdapter: JsonAdapter<WideEventEntity.CleanupPolicy> by lazy {
        moshi.adapter(WideEventEntity.CleanupPolicy::class.java)
    }

    private val wideEventIntervalListAdapter: JsonAdapter<List<WideEventEntity.WideEventInterval>> by lazy {
        moshi.adapter(
            Types.newParameterizedType(
                List::class.java,
                WideEventEntity.WideEventInterval::class.java,
            ),
        )
    }

    @TypeConverter
    fun fromMetadataEntryList(value: List<WideEventEntity.MetadataEntry>): String =
        metadataEntryListAdapter.toJson(value)

    @TypeConverter
    fun toMetadataEntryList(value: String): List<WideEventEntity.MetadataEntry> =
        metadataEntryListAdapter.fromJson(value) ?: emptyList()

    @TypeConverter
    fun fromWideEventStepList(value: List<WideEventEntity.WideEventStep>): String =
        wideEventStepListAdapter.toJson(value)

    @TypeConverter
    fun toWideEventStepList(value: String): List<WideEventEntity.WideEventStep> =
        wideEventStepListAdapter.fromJson(value) ?: emptyList()

    @TypeConverter
    fun fromWideEventStatus(status: WideEventEntity.WideEventStatus?): String? =
        status?.statusCode

    @TypeConverter
    fun toWideEventStatus(statusCode: String?): WideEventEntity.WideEventStatus? =
        statusCode?.let { statusCode ->
            WideEventEntity.WideEventStatus.entries.first { it.statusCode == statusCode }
        }

    @TypeConverter
    fun fromCleanupPolicy(value: WideEventEntity.CleanupPolicy?): String? =
        value?.let { cleanupPolicyAdapter.toJson(it) }

    @TypeConverter
    fun toCleanupPolicy(value: String?): WideEventEntity.CleanupPolicy? =
        value?.let { cleanupPolicyAdapter.fromJson(it) }

    @TypeConverter
    fun fromWideEventIntervalList(value: List<WideEventEntity.WideEventInterval>): String =
        wideEventIntervalListAdapter.toJson(value)

    @TypeConverter
    fun toWideEventIntervalList(value: String): List<WideEventEntity.WideEventInterval> =
        wideEventIntervalListAdapter.fromJson(value) ?: emptyList()
}

private class WideEventStatusJsonAdapter {
    @FromJson
    fun fromJson(json: String): WideEventEntity.WideEventStatus =
        WideEventEntity.WideEventStatus.entries.first { it.statusCode == json }

    @ToJson
    fun toJson(status: WideEventEntity.WideEventStatus): String = status.statusCode
}

private class DurationMillisAdapter {
    @ToJson
    fun toJson(value: Duration?): Long? = value?.toMillis()

    @FromJson
    fun fromJson(value: Long?): Duration? = value?.let { Duration.ofMillis(it) }
}

private class InstantMillisAdapter {
    @ToJson
    fun toJson(value: Instant?): Long? = value?.toEpochMilli()

    @FromJson
    fun fromJson(value: Long?): Instant? = value?.let { Instant.ofEpochMilli(it) }
}
