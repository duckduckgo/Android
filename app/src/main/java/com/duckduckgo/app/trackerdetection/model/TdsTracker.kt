/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.app.trackerdetection.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.duckduckgo.app.di.JsonModule
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Types

@Entity(tableName = "tds_tracker")
data class TdsTracker(
    @PrimaryKey
    val domain: String,

    val defaultAction: Action,

    val ownerName: String,

    val rules: List<Rule>
)

enum class Action {
    BLOCK,
    IGNORE;
}

class Rule(
    val rule: String,
    val action: Action?,
    val exceptions: RuleExceptions?
)

class RuleExceptions(
    val domains: List<String>?,
    val types: List<String>?
)

class ActionTypeConverter {

    @TypeConverter
    fun toAction(value: String): Action {
        return Action.valueOf(value)
    }

    @TypeConverter
    fun fromAction(value: Action): String {
        return value.name
    }
}

class RuleTypeConverter() {

    @TypeConverter
    fun toRules(value: String): List<Rule> {
        return jsonAdapter.fromJson(value)
    }

    @TypeConverter
    fun fromRules(value: List<Rule>): String {
        return jsonAdapter.toJson(value)
    }

    companion object {
        private val type = Types.newParameterizedType(List::class.java, Rule::class.java)
        private val jsonAdapter: JsonAdapter<List<Rule>> = JsonModule().moshi().adapter(type)
    }
}

