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

package com.duckduckgo.pir.impl.freemium

import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.isInternalBuild
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pir.api.freemium.PirFreemium
import com.duckduckgo.pir.api.freemium.PirFreemiumEntryPoint
import com.duckduckgo.pir.impl.PirRemoteFeatures
import com.duckduckgo.pir.impl.store.PirFreemiumDataStore
import com.duckduckgo.subscriptions.api.Subscriptions
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import logcat.LogPriority.ERROR
import logcat.logcat
import javax.inject.Inject

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealPirFreemium @Inject constructor(
    private val pirRemoteFeatures: PirRemoteFeatures,
    private val subscriptions: Subscriptions,
    private val pirFreemiumDataStore: PirFreemiumDataStore,
    private val appBuildConfig: AppBuildConfig,
    private val dispatcherProvider: DispatcherProvider,
) : PirFreemium {

    override suspend fun getSettingsEntryPoint(): PirFreemiumEntryPoint = withContext(dispatcherProvider.io()) {
        // Settings is opened constantly: a failure anywhere in resolution hides the promo rather than taking the screen down.
        try {
            resolveEntryPoint()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(ERROR) { "PIR-FREEMIUM: Failed to resolve entry point eligibility: $e" }
            PirFreemiumEntryPoint.HIDDEN
        }
    }

    private suspend fun resolveEntryPoint(): PirFreemiumEntryPoint = when {
        !canShowEntryPoint() -> PirFreemiumEntryPoint.HIDDEN
        pirFreemiumDataStore.firstScanResult == null -> PirFreemiumEntryPoint.START_FREE_SCAN
        else -> PirFreemiumEntryPoint.VIEW_SCAN_RESULTS
    }

    private suspend fun canShowEntryPoint(): Boolean {
        if (!pirRemoteFeatures.pirBeta().isEnabled()) return false

        // Freemium is for non-subscribers only: a signed-in user always gets paid PIR, and this is also
        // what keeps the promo and the paid PIR row mutually exclusive.
        if (subscriptions.isSignedIn()) return false

        return pirRemoteFeatures.freemium().isEnabled() && meetsLocaleRequirement()
    }

    private fun meetsLocaleRequirement(): Boolean =
        appBuildConfig.deviceLocale.country.equals(US_COUNTRY_CODE, ignoreCase = true) || appBuildConfig.isInternalBuild()

    private companion object {
        private const val US_COUNTRY_CODE = "US"
    }
}
