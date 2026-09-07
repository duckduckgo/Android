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
import com.duckduckgo.subscriptions.impl.SubscriptionsFeature
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import javax.inject.Inject

interface PaywallPathProvider {
    fun getPath(featurePage: String): String?
}

@ContributesBinding(AppScope::class)
class RealPaywallPathProvider @Inject constructor(
    private val subscriptionsFeature: SubscriptionsFeature,
    moshi: Moshi,
) : PaywallPathProvider {

    private val jsonAdapter: JsonAdapter<PaywallsSettings> by lazy {
        moshi.newBuilder().add(KotlinJsonAdapterFactory()).build().adapter(PaywallsSettings::class.java)
    }

    override fun getPath(featurePage: String): String? {
        val entryPoints = parseSettings()?.entryPoints ?: return null
        val path = entryPoints[featurePage]?.path ?: return null
        return path.takeIf { it.isNotBlank() }
    }

    private fun parseSettings(): PaywallsSettings? =
        subscriptionsFeature.performanceOptimizedPaywalls().getSettings()?.let {
            runCatching { jsonAdapter.fromJson(it) }.getOrNull()
        }

    private class PaywallsSettings(
        val entryPoints: Map<String, EntryPoint>?,
    )

    private class EntryPoint(
        val path: String?,
    )
}
