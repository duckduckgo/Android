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

package com.duckduckgo.subscriptions.impl.pixels

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.dashboard.api.PrivacyDashboardOpenedPlugin
import com.duckduckgo.subscriptions.impl.store.PaywallMetricsDataStore
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Owns all business logic related to paywall visibility metrics.
 * [com.duckduckgo.subscriptions.impl.store.PaywallMetricsDataStore] is the dumb storage layer; this class is the single entry
 * point for the PixelSender, the scheduler, and the workers.
 */
@SingleInstanceIn(AppScope::class)
@ContributesMultibinding(
    AppScope::class,
    boundType = PrivacyDashboardOpenedPlugin::class,
)
class PaywallMetricsManager @Inject constructor(
    private val paywallMetricsStore: PaywallMetricsDataStore,
) : PrivacyDashboardOpenedPlugin {
    val paywallEverSeen: Boolean get() = paywallMetricsStore.paywallEverSeen

    val privacyDashboardEverOpened: Boolean get() = paywallMetricsStore.privacyDashboardEverOpened

    override suspend fun onPrivacyDashboardOpened() {
        paywallMetricsStore.privacyDashboardEverOpened = true
    }

    fun recordFirstPaywallSeen(): String? {
        if (paywallMetricsStore.paywallEverSeen) return null
        paywallMetricsStore.paywallEverSeen = true
        return getDayBucket(paywallMetricsStore.firstInstallTimestamp)
    }

    fun isNotSeenDayFired(dayBucket: String): Boolean = paywallMetricsStore.isNotSeenDayFired(dayBucket)

    fun markNotSeenDayFired(dayBucket: String) = paywallMetricsStore.markNotSeenDayFired(dayBucket)

    /**
     * Returns the delay in milliseconds until [checkAfterDays] have elapsed since install.
     * Returns 0 if that point is already in the past.
     */
    fun delayUntilMilestone(checkAfterDays: Long): Long {
        val checkAtMillis = paywallMetricsStore.firstInstallTimestamp + TimeUnit.DAYS.toMillis(checkAfterDays)
        return maxOf(0L, checkAtMillis - System.currentTimeMillis())
    }

    private fun getDayBucket(firstInstallTimestamp: Long): String {
        val days = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - firstInstallTimestamp)
        return when {
            days == 0L -> "d0"
            days <= 3L -> "d1_to_d3"
            days <= 7L -> "d4_to_d7"
            days <= 14L -> "d8_to_d14"
            days <= 30L -> "d15_to_d30"
            else -> "d30_plus"
        }
    }
}
