/*
 * Copyright (c) 2017 DuckDuckGo
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

package com.duckduckgo.app.privacymonitor.store

import android.content.Context
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.UriString
import com.duckduckgo.app.privacymonitor.model.TermsOfService
import com.duckduckgo.app.trackerdetection.model.TrackerNetworks
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import javax.inject.Inject
import javax.inject.Singleton


/**
 * This raw file store is temporary. Once we move to an api call to retrieve the json
 * we'll store the content in a db rather than a raw file.
 */
@Singleton
class TermsOfServiceRawStore @Inject constructor(private val moshi: Moshi,
                                                 private val context: Context,
                                                 private val trackerNetworks: TrackerNetworks) : TermsOfServiceStore {

    private val data: List<TermsOfService> by lazy {
        val json = context.resources.openRawResource(R.raw.tosdr).bufferedReader().use { it.readText() }
        val type = Types.newParameterizedType(List::class.java, TermsOfService::class.java)
        val adapter: JsonAdapter<List<TermsOfService>> = moshi.adapter(type)
        adapter.fromJson(json)
    }

    override fun retrieveTerms(url: String): TermsOfService? {
        val entry = data.find { it.name != null && UriString.sameOrSubdomain(url, it.name) }
        if (entry != null) {
            return entry
        }

        val network = trackerNetworks.network(url)
        if (network != null) {
            return data.find { it.name != null && UriString.sameOrSubdomain(network.url, it.name) }
        }

        return null
    }
}