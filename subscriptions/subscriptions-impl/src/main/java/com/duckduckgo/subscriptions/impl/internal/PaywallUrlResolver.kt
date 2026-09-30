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
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.PERFORMANCE_OPTIMIZED_PAYWALLS_COHORT_QUERY_PARAM_KEY
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.PIR_QUERY_PARAM_KEY
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.TRIAL_QUERY_PARAM_KEY
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.VPN_FEATURE_PAGE
import com.duckduckgo.subscriptions.impl.SubscriptionsFeature
import com.duckduckgo.subscriptions.impl.SubscriptionsFeature.PerformanceOptimizedPaywallsCohorts
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
        val uri = url.toUri()
        if (!isPaywallUrl(uri)) return null
        val toggle = subscriptionsFeature.performanceOptimizedPaywalls()
        if (!toggle.isEnabled()) return null
        // Entry points without a faster page, or with no offers, return before enrolling, so both cohorts only
        // contain users who could be shown either paywall.
        val fasterPaywallUri = rewrite(uri)?.toUri() ?: return null

        toggle.enroll()
        return when {
            toggle.isEnrolledAndEnabled(PerformanceOptimizedPaywallsCohorts.TREATMENT) ->
                fasterPaywallUri.withCohort(PerformanceOptimizedPaywallsCohorts.TREATMENT)
            toggle.isEnrolledAndEnabled(PerformanceOptimizedPaywallsCohorts.CONTROL) -> uri.withCohort(PerformanceOptimizedPaywallsCohorts.CONTROL)
            else -> null
        }
    }

    private fun Uri.withCohort(cohort: PerformanceOptimizedPaywallsCohorts): String =
        buildUpon()
            .clearQuery()
            .appendQueryParametersOf(this, excluding = setOf(PERFORMANCE_OPTIMIZED_PAYWALLS_COHORT_QUERY_PARAM_KEY))
            .appendQueryParameter(PERFORMANCE_OPTIMIZED_PAYWALLS_COHORT_QUERY_PARAM_KEY, cohort.cohortName)
            .build()
            .toString()

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
        val builder = uri.buildUpon().encodedPath(path).clearQuery().appendQueryParametersOf(uri, excluding = REWRITTEN_QUERY_PARAM_KEYS)

        builder.appendQueryParameter(TRIAL_QUERY_PARAM_KEY, isFreeTrialEligible.toString())
        if (!isPirOnOffer) {
            builder.appendQueryParameter(PIR_QUERY_PARAM_KEY, "false")
        }
        return builder.build().toString()
    }

    private fun Uri.Builder.appendQueryParametersOf(uri: Uri, excluding: Set<String>): Uri.Builder = apply {
        uri.queryParameterNames
            .filterNot { it in excluding }
            .forEach { name ->
                uri.getQueryParameters(name).forEach { value -> appendQueryParameter(name, value) }
            }
    }

    private companion object {
        val REWRITTEN_QUERY_PARAM_KEYS = setOf(FEATURE_PAGE_QUERY_PARAM_KEY, TRIAL_QUERY_PARAM_KEY, PIR_QUERY_PARAM_KEY)
    }
}
