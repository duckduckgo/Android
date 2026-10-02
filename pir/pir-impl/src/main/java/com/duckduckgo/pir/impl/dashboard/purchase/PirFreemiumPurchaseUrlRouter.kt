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

package com.duckduckgo.pir.impl.dashboard.purchase

import android.net.Uri
import com.duckduckgo.common.utils.extensions.toTldPlusOne
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.subscriptions.api.Subscriptions
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject

/**
 * How a URL the PIR dashboard web UI asks to navigate to should be handled.
 */
sealed interface PirPurchaseRoute {

    /**
     * The URL is the subscription purchase flow. Carries the parameters the native flow is started
     * with, which are this module's own constants — never values read off the intercepted URL.
     */
    data class NativePurchaseFlow(
        val origin: String,
        val featurePage: String,
    ) : PirPurchaseRoute

    /** Not a purchase URL. The dashboard WebView loads it as it would have before. */
    data object NotHandled : PirPurchaseRoute
}

interface PirFreemiumPurchaseUrlRouter {

    /**
     * Decides how a navigation requested by the dashboard web UI should be handled.
     *
     * Pure and side-effect free: the caller owns the navigation, so the decision can be tested on
     * its own. Never throws.
     */
    fun route(uri: Uri): PirPurchaseRoute
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealPirFreemiumPurchaseUrlRouter @Inject constructor(
    private val subscriptions: Subscriptions,
) : PirFreemiumPurchaseUrlRouter {

    override fun route(uri: Uri): PirPurchaseRoute =
        if (isPurchaseUrl(uri)) {
            PirPurchaseRoute.NativePurchaseFlow(origin = FREE_SCAN_ORIGIN, featurePage = PIR_FEATURE_PAGE)
        } else {
            PirPurchaseRoute.NotHandled
        }

    private fun isPurchaseUrl(uri: Uri): Boolean = runCatching {
        // The host is checked first and for every path, so a purchase path on another host can never
        // reach the native flow. iOS applies this check on only one of its two interception paths.
        if (uri.host?.toTldPlusOne() != SUBSCRIPTIONS_ETLD) return@runCatching false

        // Subscriptions.isSubscriptionUrl matches single-segment paths only, so on its own it misses
        // /subscriptions/plans and /pro/plans. It is still consulted for remote-config paywall paths.
        uri.path?.trimEnd('/') in PURCHASE_PATHS || subscriptions.isSubscriptionUrl(uri)
    }.getOrDefault(false)

    private companion object {
        const val SUBSCRIPTIONS_ETLD = "duckduckgo.com"
        const val FREE_SCAN_ORIGIN = "funnel_freescan_android"
        const val PIR_FEATURE_PAGE = "pir"
        val PURCHASE_PATHS = setOf("/subscriptions", "/subscriptions/plans", "/pro", "/pro/plans")
    }
}
