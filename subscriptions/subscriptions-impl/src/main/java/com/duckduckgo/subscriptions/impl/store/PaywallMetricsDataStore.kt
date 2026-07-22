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

package com.duckduckgo.subscriptions.impl.store

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.di.scopes.AppScope
import dagger.SingleInstanceIn
import javax.inject.Inject

@SingleInstanceIn(AppScope::class)
class PaywallMetricsDataStore @Inject constructor(
    private val context: Context,
    private val sharedPreferencesProvider: SharedPreferencesProvider,
) {
    private val preferences: SharedPreferences by lazy {
        sharedPreferencesProvider.getSharedPreferences(FILENAME)
    }

    var paywallEverSeen: Boolean
        get() = preferences.getBoolean(KEY_PAYWALL_EVER_SEEN, false)
        set(value) { preferences.edit { putBoolean(KEY_PAYWALL_EVER_SEEN, value) } }

    var privacyDashboardEverOpened: Boolean
        get() = preferences.getBoolean(KEY_PRIVACY_DASHBOARD_EVER_OPENED, false)
        set(value) { preferences.edit { putBoolean(KEY_PRIVACY_DASHBOARD_EVER_OPENED, value) } }

    var subscriptionPromoShown: Boolean
        get() = preferences.getBoolean(KEY_SUBSCRIPTION_PROMO_SHOWN, false)
        set(value) { preferences.edit { putBoolean(KEY_SUBSCRIPTION_PROMO_SHOWN, value) } }

    val firstInstallTimestamp: Long by lazy {
        context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
    }

    fun markNotSeenDayFired(dayBucket: String) {
        val current = preferences.getStringSet(KEY_NOT_SEEN_FIRED_DAYS, emptySet()) ?: emptySet()
        preferences.edit { putStringSet(KEY_NOT_SEEN_FIRED_DAYS, current + dayBucket) }
    }

    fun isNotSeenDayFired(dayBucket: String): Boolean {
        return preferences.getStringSet(KEY_NOT_SEEN_FIRED_DAYS, emptySet())?.contains(dayBucket) == true
    }

    companion object {
        const val FILENAME = "com.duckduckgo.subscriptions.paywall.metrics"
        const val KEY_PAYWALL_EVER_SEEN = "KEY_PAYWALL_EVER_SEEN"
        const val KEY_NOT_SEEN_FIRED_DAYS = "KEY_NOT_SEEN_FIRED_DAYS"
        const val KEY_PRIVACY_DASHBOARD_EVER_OPENED = "KEY_PRIVACY_DASHBOARD_EVER_OPENED"
        const val KEY_SUBSCRIPTION_PROMO_SHOWN = "KEY_SUBSCRIPTION_PROMO_SHOWN"
    }
}
