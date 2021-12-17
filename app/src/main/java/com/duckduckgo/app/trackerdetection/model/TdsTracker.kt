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
    @PrimaryKey val domain: String,
    val defaultAction: Action,
    val ownerName: String,
    val categories: List<String>,
    val rules: List<Rule>
)

enum class Action {
    BLOCK,
    IGNORE
}

class Rule(
    val rule: String,
    val action: Action?,
    val exceptions: RuleExceptions?,
    val surrogate: String?
)

class RuleExceptions(val domains: List<String>?, val types: List<String>?)

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

class RuleTypeConverter {

    @TypeConverter
    fun toRules(value: String): List<Rule> {
        return Adapters.ruleListAdapter.fromJson(value)!!
    }

    @TypeConverter
    fun fromRules(value: List<Rule>): String {
        return Adapters.ruleListAdapter.toJson(value)
    }
}

class CategoriesTypeConverter {

    @TypeConverter
    fun toCategories(value: String): List<String> {
        return Adapters.stringListAdapter.fromJson(value)!!
    }

    @TypeConverter
    fun fromCategories(value: List<String>): String {
        return Adapters.stringListAdapter.toJson(value)
    }
}

class Adapters {
    companion object {
        private val moshi = JsonModule().moshi()
        private val ruleListType = Types.newParameterizedType(List::class.java, Rule::class.java)
        private val stringListType =
            Types.newParameterizedType(List::class.java, String::class.java)
        val ruleListAdapter: JsonAdapter<List<Rule>> = moshi.adapter(ruleListType)
        val stringListAdapter: JsonAdapter<List<String>> = moshi.adapter(stringListType)
    }
}
