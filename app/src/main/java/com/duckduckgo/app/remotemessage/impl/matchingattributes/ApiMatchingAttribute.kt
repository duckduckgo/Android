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

package com.duckduckgo.app.remotemessage.impl.matchingattributes

import com.duckduckgo.app.remotemessage.impl.MatchingAttributePlugin
import com.duckduckgo.di.scopes.AppObjectGraph
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import timber.log.Timber
import javax.inject.Inject

@ContributesMultibinding(AppObjectGraph::class)
class ApiMatchingAttribute @Inject constructor() : MatchingAttributePlugin {

    private val featureName = MatchingAttributeName.Api

    override fun parse(key: String, json: String): MatchingAttribute? {
        if (key == featureName.key) {
            Timber.i("RMF: plugin found for $key")
            val moshi = Moshi.Builder().build()
            val jsonAdapter: JsonAdapter<MatchingAttribute.Api> = moshi.adapter(MatchingAttribute.Api::class.java)
            return jsonAdapter.fromJson(json)
        }
        return null
    }
}
