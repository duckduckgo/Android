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

package com.duckduckgo.installation.impl.installer.samsungstore

import com.duckduckgo.anvil.annotations.PriorityKey
import com.duckduckgo.app.statistics.AtbInitializerListener
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.experiments.api.VariantManager
import com.duckduckgo.installation.impl.installer.InstallSourceExtractor
import com.duckduckgo.referral.api.AppReferrer
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.withContext
import logcat.LogPriority.INFO
import logcat.logcat
import javax.inject.Inject

// Runs after Aura: its remote installer list has included the Galaxy Store package, and its direct variant write
// would otherwise replace this tag. The reinstall listener (10) is not a dependency; returning users are detected directly.
internal const val PRIORITY_SAMSUNG_STORE_INSTALL_ATTRIBUTION = 30

/**
 * Tags a first-run install that came through the Samsung Galaxy Store with a reserved ATB variant and an origin,
 * so the server-side install and retention counts for that store can be split out of the totals. Returning users get
 * their own variant so the store's new installs and reinstalls stay separable.
 */
@ContributesMultibinding(AppScope::class)
@PriorityKey(PRIORITY_SAMSUNG_STORE_INSTALL_ATTRIBUTION)
@SingleInstanceIn(AppScope::class)
class SamsungStoreInstallAttribution @Inject constructor(
    private val feature: SamsungStoreInstallAttributionFeature,
    private val installSourceExtractor: InstallSourceExtractor,
    private val appBuildConfig: AppBuildConfig,
    private val variantManager: VariantManager,
    private val appReferrer: AppReferrer,
    private val dispatcherProvider: DispatcherProvider,
) : AtbInitializerListener {

    override suspend fun beforeAtbInit() {
        tagIfInstalledFromGalaxyStore()
    }

    override fun beforeAtbInitTimeoutMillis(): Long = MAX_WAIT_TIME_MS

    private suspend fun tagIfInstalledFromGalaxyStore() = withContext(dispatcherProvider.io()) {
        if (!feature.self().isEnabled()) return@withContext

        val source = runCatching { installSourceExtractor.extract() }.getOrNull()
        if (source != SAMSUNG_STORE_PACKAGE) return@withContext

        // Asking the build config directly, rather than checking for the `ru` variant, keeps this independent of
        // whatever other listeners wrote before us.
        val variant = if (appBuildConfig.isAppReinstall()) REINSTALL_VARIANT else VARIANT

        // The referrer-variant path also records the key as protected, so later remote variant updates cannot
        // reset it. A direct write to StatisticsDataStore.variant would not be protected.
        variantManager.updateAppReferrerVariant(variant)
        appReferrer.setOriginAttributeCampaign(ORIGIN)
        logcat(INFO) { "Galaxy Store install tagged with variant $variant" }
    }

    companion object {
        const val SAMSUNG_STORE_PACKAGE = "com.sec.android.app.samsungapps"
        const val VARIANT = "sg"
        const val REINSTALL_VARIANT = "sr"
        const val ORIGIN = "funnel_app_samsung_android"
        private const val MAX_WAIT_TIME_MS = 1_500L
    }
}
