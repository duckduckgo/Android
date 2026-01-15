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
import dagger.SingleInstanceIn
import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.LogPriority.INFO
import logcat.LogPriority.VERBOSE
import logcat.LogPriority.WARN
import logcat.logcat
import javax.inject.Inject

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
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

    override fun refreshSearchRetentionAtb() {
        val atb = store.atb

        if (atb == null) {
            initializeAtb()
            return
        }

        appCoroutineScope.launch(dispatchers.io()) {
            val oldSearchAtb = store.searchRetentionAtb ?: atb.version

            refreshRetentionAtb(
                atb = atb,
                oldRetentionAtb = oldSearchAtb,
                logLabel = "Search",
                updateCall = { fullAtb, retentionAtb, email ->
                    service.updateSearchAtb(
                        atb = fullAtb,
                        retentionAtb = retentionAtb,
                        email = email,
                    )
                },
                onSuccess = { updatedAtb ->
                    store.searchRetentionAtb = updatedAtb.version
                    plugins.getPlugins().forEach { plugin -> plugin.onSearchRetentionAtbRefreshed(oldSearchAtb, updatedAtb.version) }
                },
            )
        }
    }

    override fun refreshDuckAiRetentionAtb() {
        val atb = store.atb

        if (atb == null) {
            initializeAtb()
            return
        }

        appCoroutineScope.launch(dispatchers.io()) {
            val oldDuckAiAtb = store.duckaiRetentionAtb ?: atb.version

            refreshRetentionAtb(
                atb = atb,
                oldRetentionAtb = oldDuckAiAtb,
                logLabel = "Duck.ai",
                updateCall = { fullAtb, retentionAtb, email ->
                    service.updateDuckAiAtb(
                        atb = fullAtb,
                        retentionAtb = retentionAtb,
                        email = email,
                    )
                },
                onSuccess = { updatedAtb ->
                    store.duckaiRetentionAtb = updatedAtb.version
                    plugins.getPlugins().forEach { plugin -> plugin.onDuckAiRetentionAtbRefreshed(oldDuckAiAtb, updatedAtb.version) }
                },
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

        val oldAppAtb = store.appRetentionAtb ?: atb.version

        refreshRetentionAtb(
            atb = atb,
            oldRetentionAtb = oldAppAtb,
            logLabel = "App",
            updateCall = { fullAtb, retentionAtb, email ->
                service.updateAppAtb(
                    atb = fullAtb,
                    retentionAtb = retentionAtb,
                    email = email,
                )
            },
            onSuccess = { updatedAtb ->
                store.appRetentionAtb = updatedAtb.version
                plugins.getPlugins().forEach { plugin -> plugin.onAppRetentionAtbRefreshed(oldAppAtb, updatedAtb.version) }
            },
        )
    }

    private fun emailSignInState(): Int =
        kotlin.runCatching { emailManager.isSignedIn().asInt() }.getOrDefault(0)

    @SuppressLint("CheckResult")
    private fun refreshRetentionAtb(
        atb: Atb,
        oldRetentionAtb: String,
        logLabel: String,
        updateCall: (String, String, Int) -> Observable<Atb>,
        onSuccess: (Atb) -> Unit,
    ) {
        val fullAtb = atb.formatWithVariant(variantManager.getVariantKey())

        updateCall(fullAtb, oldRetentionAtb, emailSignInState())
            .subscribeOn(Schedulers.io())
            .subscribe(
                {
                    logcat(VERBOSE) { "$logLabel atb refresh succeeded, latest atb is ${it.version}" }
                    onSuccess(it)
                    storeUpdateVersionIfPresent(it)
                },
                { logcat(VERBOSE) { "$logLabel atb refresh failed with error ${it.localizedMessage}" } },
            )
    }

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
