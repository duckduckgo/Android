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

import android.net.Uri
import androidx.core.net.toUri
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.subscriptions.api.Product
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.FEATURE_PAGE_QUERY_PARAM_KEY
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.PIR_QUERY_PARAM_KEY
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.TRIAL_QUERY_PARAM_KEY
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.VPN_FEATURE_PAGE
import com.duckduckgo.subscriptions.impl.SubscriptionsFeature
import com.duckduckgo.subscriptions.impl.SubscriptionsManager
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface PaywallUrlResolver {
    suspend fun resolve(url: String): String
}

@ContributesBinding(AppScope::class)
class RealPaywallUrlResolver @Inject constructor(
    private val subscriptionsUrlProvider: SubscriptionsUrlProvider,
    private val subscriptionsManager: SubscriptionsManager,
    private val subscriptionsFeature: SubscriptionsFeature,
    private val paywallPathProvider: PaywallPathProvider,
    private val dispatcherProvider: DispatcherProvider,
) : PaywallUrlResolver {

    override suspend fun resolve(url: String): String = withContext(dispatcherProvider.io()) {
        runCatching {
            optimizedUrl(url)
        }.getOrNull() ?: url
    }

    private suspend fun optimizedUrl(url: String): String? {
        if (!subscriptionsFeature.performanceOptimizedPaywalls().isEnabled()) return null
        val uri = url.toUri()
        if (!isPaywallUrl(uri)) return null
        return rewrite(uri)
    }

    private fun isPaywallUrl(uri: Uri): Boolean {
        val buyUri = subscriptionsUrlProvider.buyUrl.toUri()
        return uri.host == buyUri.host && uri.path == buyUri.path
    }

    private suspend fun rewrite(uri: Uri): String? {
        val featurePage = uri.getQueryParameter(FEATURE_PAGE_QUERY_PARAM_KEY)?.takeIf { it.isNotBlank() } ?: VPN_FEATURE_PAGE
        val path = paywallPathProvider.getPath(featurePage) ?: return null

        val offers = subscriptionsManager.getSubscriptionOffer()
        if (offers.isEmpty()) return null

        return buildUrl(
            uri = uri,
            path = path,
            isFreeTrialEligible = subscriptionsManager.isFreeTrialEligible(),
            isPirOnOffer = offers.any { Product.PIR.value in it.features },
        )
    }

    private fun buildUrl(
        uri: Uri,
        path: String,
        isFreeTrialEligible: Boolean,
        isPirOnOffer: Boolean,
    ): String {
        val builder = uri.buildUpon().encodedPath(path).clearQuery()

        uri.queryParameterNames
            .filterNot { it in REWRITTEN_QUERY_PARAM_KEYS }
            .forEach { name ->
                uri.getQueryParameters(name).forEach { value -> builder.appendQueryParameter(name, value) }
            }

        builder.appendQueryParameter(TRIAL_QUERY_PARAM_KEY, isFreeTrialEligible.toString())
        if (!isPirOnOffer) {
            builder.appendQueryParameter(PIR_QUERY_PARAM_KEY, "false")
        }
        return builder.build().toString()
    }

    private companion object {
        val REWRITTEN_QUERY_PARAM_KEYS = setOf(FEATURE_PAGE_QUERY_PARAM_KEY, TRIAL_QUERY_PARAM_KEY, PIR_QUERY_PARAM_KEY)
    }
}
