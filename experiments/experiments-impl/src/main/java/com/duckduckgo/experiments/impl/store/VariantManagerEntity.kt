/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.experiments.impl.store

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

@Entity(tableName = "experiment_variants")
data class ExperimentVariantEntity(
    @PrimaryKey val key: String,
    val weight: Double?,
    val localeFilter: List<String> = emptyList(),
    val androidVersionFilter: List<String> = emptyList(),
)

class StringListConverter {

    @TypeConverter
    fun toStringList(value: String): List<String> {
        return Adapters.stringListAdapter.fromJson(value)!!
    }

    @TypeConverter
    fun fromStringList(value: List<String>): String {
        return Adapters.stringListAdapter.toJson(value)
    }
}

class Adapters {
    companion object {
        private val moshi = Moshi.Builder().build()
        private val stringListType = Types.newParameterizedType(List::class.java, String::class.java)
        val stringListAdapter: JsonAdapter<List<String>> = moshi.adapter(stringListType)
    }
}
