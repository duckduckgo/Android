/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.app.remotemessage.impl.messages

import com.duckduckgo.app.remotemessage.impl.ActionJson
import com.duckduckgo.app.remotemessage.impl.MessagePlugin
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.moshi.*
import timber.log.Timber
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class SmallMessage @Inject constructor() : MessagePlugin {
    private val featureName = "small"

    override fun parse(key: String, json: String) = parse<Content.Small>(key, json, featureName)
}

@ContributesMultibinding(AppScope::class)
class MediumMessage @Inject constructor() : MessagePlugin {
    private val featureName = "medium"

    override fun parse(key: String, json: String) = parse<Content.Medium>(key, json, featureName)
}

@ContributesMultibinding(AppScope::class)
class BigSingleActionMessage @Inject constructor() : MessagePlugin {
    private val featureName = "big_single_action"

    override fun parse(key: String, json: String) = parse<Content.BigSingleAction>(key, json, featureName)
}

@ContributesMultibinding(AppScope::class)
class BigTwoActionsMessage @Inject constructor() : MessagePlugin {
    private val featureName = "big_two_action"

    override fun parse(key: String, json: String) = parse<Content.BigTwoActions>(key, json, featureName)
}

private inline fun <reified T : Content> parse(key: String, json: String, featureName: String): T? {
    if (key == featureName) {
        val moshi = Moshi.Builder().build()
        val jsonAdapter: JsonAdapter<T> = moshi.adapter(T::class.java)
        return jsonAdapter.fromJson(json)
    }
    return null
}

class ActionAdapter {
    @ToJson
    fun toJson(action: Action): ActionJson {
        Timber.i("RMF: toJson $action")
        return ActionJson("", "")
    }

    @FromJson
    fun fromJson(action: ActionJson): Action {
        Timber.i("RMF: fromJson $action")
        return when (action.type) {
            "url" -> Action.Url(value = action.value)
            "playstore" -> Action.PlayStore(value = action.value)
            else -> throw JsonDataException("unknown suit: ${action.type}")
        }
    }
}
