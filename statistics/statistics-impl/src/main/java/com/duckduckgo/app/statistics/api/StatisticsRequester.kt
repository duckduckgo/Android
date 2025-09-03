/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.statistics.api

import android.annotation.SuppressLint
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.statistics.model.Atb
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.duckduckgo.autofill.api.email.EmailManager
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.experiments.api.VariantManager
import com.squareup.anvil.annotations.ContributesBinding
import io.reactivex.schedulers.Schedulers
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.LogPriority.INFO
import logcat.LogPriority.VERBOSE
import logcat.LogPriority.WARN
import logcat.logcat

interface StatisticsUpdater {
    fun initializeAtb()
    fun refreshSearchRetentionAtb()
    fun refreshAppRetentionAtb()
}

@ContributesBinding(AppScope::class)
class StatisticsRequester @Inject constructor(
    private val store: StatisticsDataStore,
    private val service: StatisticsService,
    private val variantManager: VariantManager,
    private val plugins: PluginPoint<AtbLifecyclePlugin>,
    private val emailManager: EmailManager,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
) : StatisticsUpdater {

    /**
     * This should only be called after AppInstallationReferrerStateListener has had a chance to
     * consume referer data
     */
    @SuppressLint("CheckResult")
    override fun initializeAtb() {
        logcat(INFO) { "Initializing ATB" }

        if (store.hasInstallationStatistics) {
            logcat(VERBOSE) { "Atb already initialized" }

            val storedAtb = store.atb
            if (storedAtb != null && storedAtbFormatNeedsCorrecting(storedAtb)) {
                logcat {
                    "Previous app version stored hardcoded `ma` variant in ATB param; we want to correct this behaviour"
                }
                store.atb = Atb(storedAtb.version.removeSuffix(LEGACY_ATB_FORMAT_SUFFIX))
                store.variant = variantManager.defaultVariantKey()
            }
            return
        }

        service
            .atb(
                email = emailSignInState(),
            )
            .subscribeOn(Schedulers.io())
            .flatMap {
                val atb = Atb(it.version)
                logcat(INFO) { "$atb" }
                store.saveAtb(atb)
                val atbWithVariant = atb.formatWithVariant(variantManager.getVariantKey())

                logcat(INFO) { "Initialized ATB: $atbWithVariant" }
                service.exti(atbWithVariant)
            }
            .subscribe(
                {
                    logcat { "Atb initialization succeeded" }
                    plugins.getPlugins().forEach { it.onAppAtbInitialized() }
                },
                {
                    store.clearAtb()
                    logcat(WARN) { "Atb initialization failed ${it.localizedMessage}" }
                },
            )
    }

    private fun storedAtbFormatNeedsCorrecting(storedAtb: Atb): Boolean =
        storedAtb.version.endsWith(LEGACY_ATB_FORMAT_SUFFIX)

    @SuppressLint("CheckResult")
    override fun refreshSearchRetentionAtb() {
        val atb = store.atb

        if (atb == null) {
            initializeAtb()
            return
        }

        appCoroutineScope.launch(dispatchers.io()) {
            val fullAtb = atb.formatWithVariant(variantManager.getVariantKey())
            val oldSearchAtb = store.searchRetentionAtb ?: atb.version

            service
                .updateSearchAtb(
                    atb = fullAtb,
                    retentionAtb = oldSearchAtb,
                    email = emailSignInState(),
                )
                .subscribeOn(Schedulers.io())
                .subscribe(
                    {
                        logcat(VERBOSE) { "Search atb refresh succeeded, latest atb is ${it.version}" }
                        store.searchRetentionAtb = it.version
                        storeUpdateVersionIfPresent(it)
                        plugins.getPlugins().forEach { plugin -> plugin.onSearchRetentionAtbRefreshed(oldSearchAtb, it.version) }
                    },
                    { logcat(VERBOSE) { "Search atb refresh failed with error ${it.localizedMessage}" } },
                )
        }
    }

    @SuppressLint("CheckResult")
    override fun refreshAppRetentionAtb() {
        val atb = store.atb

        if (atb == null) {
            initializeAtb()
            return
        }

        val fullAtb = atb.formatWithVariant(variantManager.getVariantKey())
        val oldAppAtb = store.appRetentionAtb ?: atb.version

        service
            .updateAppAtb(
                atb = fullAtb,
                retentionAtb = oldAppAtb,
                email = emailSignInState(),
            )
            .subscribeOn(Schedulers.io())
            .subscribe(
                {
                    logcat(VERBOSE) { "App atb refresh succeeded, latest atb is ${it.version}" }
                    store.appRetentionAtb = it.version
                    storeUpdateVersionIfPresent(it)
                    plugins.getPlugins().forEach { plugin -> plugin.onAppRetentionAtbRefreshed(oldAppAtb, it.version) }
                },
                { logcat(VERBOSE) { "App atb refresh failed with error ${it.localizedMessage}" } },
            )
    }

    private fun emailSignInState(): Int =
        kotlin.runCatching { emailManager.isSignedIn().asInt() }.getOrDefault(0)

    private fun storeUpdateVersionIfPresent(retrievedAtb: Atb) {
        retrievedAtb.updateVersion?.let { updateVersion ->
            store.atb = Atb(updateVersion)
            store.variant = variantManager.defaultVariantKey()
        }
    }

    private fun Boolean.asInt() = if (this) 1 else 0

    companion object {
        private const val LEGACY_ATB_FORMAT_SUFFIX = "ma"
    }
}
