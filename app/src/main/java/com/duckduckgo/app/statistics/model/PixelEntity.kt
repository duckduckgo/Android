/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.app.statistics.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.duckduckgo.app.di.JsonModule
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Types

@Entity(tableName = "pixel_store")
data class PixelEntity(
    @PrimaryKey(autoGenerate = true) var id: Long = 0,
    val pixelName: String,
    val atb: String,
    val additionalQueryParams: Map<String, String> = emptyMap(),
    val encodedQueryParams: Map<String, String> = emptyMap()
)

class QueryParamsTypeConverter {
    @TypeConverter
    fun toQueryParams(value: String): Map<String, String> {
        return Adapters.queryParamsAdapter.fromJson(value)!!
    }

    @TypeConverter
    fun fromQueryParams(value: Map<String, String>): String {
        return Adapters.queryParamsAdapter.toJson(value)
    }
}

class Adapters {
    companion object {
        private val moshi = JsonModule().moshi()
        private val mapStringStringType = Types.newParameterizedType(Map::class.java, String::class.java, String::class.java)
        val queryParamsAdapter: JsonAdapter<Map<String, String>> = moshi.adapter(mapStringStringType)
    }
}

