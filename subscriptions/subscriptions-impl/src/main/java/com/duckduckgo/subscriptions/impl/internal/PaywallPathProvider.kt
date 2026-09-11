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
import dagger.SingleInstanceIn
import javax.inject.Inject

interface PaywallPathProvider {
    fun getPath(featurePage: String): String?

    fun getFeaturePage(path: String): String?
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealPaywallPathProvider @Inject constructor(
    private val subscriptionsFeature: SubscriptionsFeature,
    moshi: Moshi,
) : PaywallPathProvider {

    private val jsonAdapter: JsonAdapter<PaywallsSettings> by lazy {
        moshi.newBuilder().add(KotlinJsonAdapterFactory()).build().adapter(PaywallsSettings::class.java)
    }

    @Volatile
    private var cache: CachedPaths? = null

    override fun getPath(featurePage: String): String? = entryPoints()?.pathByFeaturePage?.get(featurePage)

    override fun getFeaturePage(path: String): String? = entryPoints()?.featurePageByPath?.get(path.trimEnd('/'))

    private fun entryPoints(): CachedPaths? {
        val settings = subscriptionsFeature.performanceOptimizedPaywalls().getSettings() ?: return null

        val cached = cache
        if (cached != null && cached.settings == settings) return cached

        val parsed = parse(settings)
        cache = parsed
        return parsed
    }

    private fun parse(settings: String): CachedPaths {
        val entryPoints = runCatching { jsonAdapter.fromJson(settings) }.getOrNull()?.entryPoints
        val pathByFeaturePage = pathsByFeaturePage(entryPoints)
        val featurePageByPath = pathByFeaturePage.entries.associate { (featurePage, path) -> path to featurePage }
        return CachedPaths(
            settings = settings,
            pathByFeaturePage = pathByFeaturePage,
            featurePageByPath = featurePageByPath,
        )
    }

    private fun pathsByFeaturePage(entryPoints: Map<String, EntryPoint>?): Map<String, String> = buildMap {
        entryPoints?.forEach { (featurePage, entryPoint) ->
            val path = entryPoint.path?.trimEnd('/')
            if (!path.isNullOrBlank()) {
                put(featurePage, path)
            }
        }
    }

    private class CachedPaths(
        val settings: String,
        val pathByFeaturePage: Map<String, String>,
        val featurePageByPath: Map<String, String>,
    )

    private class PaywallsSettings(
        val entryPoints: Map<String, EntryPoint>?,
    )

    private class EntryPoint(
        val path: String?,
    )
}
