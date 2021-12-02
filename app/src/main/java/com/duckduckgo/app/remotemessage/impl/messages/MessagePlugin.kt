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

import com.duckduckgo.app.remotemessage.impl.MessagePlugin
import com.duckduckgo.di.scopes.AppObjectGraph
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import javax.inject.Inject

@ContributesMultibinding(AppObjectGraph::class)
class SmallMessage @Inject constructor() : MessagePlugin {
    private val featureName = "small"

    override fun parse(key: String, json: String) = parse<Content.Small>(key, json, featureName)
}

private inline fun <reified T : Content> parse(key: String, json: String, featureName: String): T? {
    if (key == featureName) {
        val moshi = Moshi.Builder().build()
        val jsonAdapter: JsonAdapter<T> = moshi.adapter(T::class.java)
        return jsonAdapter.fromJson(json)
    }
    return null
}
