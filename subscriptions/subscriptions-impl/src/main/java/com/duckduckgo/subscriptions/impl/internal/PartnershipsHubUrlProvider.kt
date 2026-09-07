/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.subscriptions.impl.internal

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.PARTNER_BENEFITS_URL
import com.duckduckgo.subscriptions.impl.SubscriptionsFeature
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import javax.inject.Inject

interface PartnershipsHubUrlProvider {
    val partnershipsHubUrl: String
}

@ContributesBinding(AppScope::class)
class RealPartnershipsHubUrlProvider @Inject constructor(
    private val subscriptionsFeature: SubscriptionsFeature,
    moshi: Moshi,
) : PartnershipsHubUrlProvider {

    private val jsonAdapter: JsonAdapter<PartnershipsHubSettings> by lazy {
        moshi.newBuilder().add(KotlinJsonAdapterFactory()).build().adapter(PartnershipsHubSettings::class.java)
    }

    override val partnershipsHubUrl: String
        get() = parseHubUrl() ?: PARTNER_BENEFITS_URL

    private fun parseHubUrl(): String? {
        val settings = subscriptionsFeature.partnershipsHub().getSettings()?.let {
            runCatching { jsonAdapter.fromJson(it) }.getOrNull()
        }
        return settings?.url?.takeIf { it.isNotBlank() }
    }

    private data class PartnershipsHubSettings(
        val url: String?,
    )
}
