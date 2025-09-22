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
import com.squareup.moshi.Json
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import javax.inject.Inject

@Entity(tableName = "wide_events")
data class WideEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "flow_entry_point")
    val flowEntryPoint: String?,

    @ColumnInfo(name = "metadata")
    val metadata: List<MetadataEntry>,

    @ColumnInfo(name = "steps")
    val steps: List<WideEventStep>,

    @ColumnInfo(name = "status")
    val status: WideEventStatus?,
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
}

@ProvidedTypeConverter
class WideEventEntityTypeConverters @Inject constructor(
    private val moshi: Moshi,
) {
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
}
