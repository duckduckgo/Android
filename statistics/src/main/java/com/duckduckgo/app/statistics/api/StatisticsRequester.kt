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
import com.duckduckgo.app.global.plugins.PluginPoint
import com.duckduckgo.app.statistics.model.Atb
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.duckduckgo.autofill.api.email.EmailManager
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.experiments.api.VariantManager
import com.squareup.anvil.annotations.ContributesBinding
import io.reactivex.schedulers.Schedulers
import javax.inject.Inject
import timber.log.Timber

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
    private val plugins: PluginPoint<RefreshRetentionAtbPlugin>,
    private val emailManager: EmailManager,
) : StatisticsUpdater {

    /**
     * This should only be called after AppInstallationReferrerStateListener has had a chance to
     * consume referer data
     */
    @SuppressLint("CheckResult")
    override fun initializeAtb() {
        Timber.i("Initializing ATB")

        if (store.hasInstallationStatistics) {
            Timber.v("Atb already initialized")

            val storedAtb = store.atb
            if (storedAtb != null && storedAtbFormatNeedsCorrecting(storedAtb)) {
                Timber.d(
                    "Previous app version stored hardcoded `ma` variant in ATB param; we want to correct this behaviour",
                )
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
                Timber.i("$atb")
                store.saveAtb(atb)
                val atbWithVariant = atb.formatWithVariant(variantManager.getVariantKey())

                Timber.i("Initialized ATB: $atbWithVariant")
                service.exti(atbWithVariant)
            }
            .subscribe(
                { Timber.d("Atb initialization succeeded") },
                {
                    store.clearAtb()
                    Timber.w("Atb initialization failed ${it.localizedMessage}")
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

        val fullAtb = atb.formatWithVariant(variantManager.getVariantKey())
        val retentionAtb = store.searchRetentionAtb ?: atb.version

        service
            .updateSearchAtb(
                atb = fullAtb,
                retentionAtb = retentionAtb,
                email = emailSignInState(),
            )
            .subscribeOn(Schedulers.io())
            .subscribe(
                {
                    Timber.v("Search atb refresh succeeded, latest atb is ${it.version}")
                    store.searchRetentionAtb = it.version
                    storeUpdateVersionIfPresent(it)
                    plugins.getPlugins().forEach { plugin -> plugin.onSearchRetentionAtbRefreshed() }
                },
                { Timber.v("Search atb refresh failed with error ${it.localizedMessage}") },
            )
    }

    @SuppressLint("CheckResult")
    override fun refreshAppRetentionAtb() {
        val atb = store.atb

        if (atb == null) {
            initializeAtb()
            return
        }

        val fullAtb = atb.formatWithVariant(variantManager.getVariantKey())
        val retentionAtb = store.appRetentionAtb ?: atb.version

        service
            .updateAppAtb(
                atb = fullAtb,
                retentionAtb = retentionAtb,
                email = emailSignInState(),
            )
            .subscribeOn(Schedulers.io())
            .subscribe(
                {
                    Timber.v("App atb refresh succeeded, latest atb is ${it.version}")
                    store.appRetentionAtb = it.version
                    storeUpdateVersionIfPresent(it)
                    plugins.getPlugins().forEach { plugin -> plugin.onAppRetentionAtbRefreshed() }
                },
                { Timber.v("App atb refresh failed with error ${it.localizedMessage}") },
            )
    }

    private fun emailSignInState(): Int =
        kotlin.runCatching { emailManager.isSignedIn().asInt() }.getOrDefault(0)

    private fun storeUpdateVersionIfPresent(retrievedAtb: Atb) {
        if (retrievedAtb.updateVersion != null) {
            store.atb = Atb(retrievedAtb.updateVersion)
        }
    }

    private fun Boolean.asInt() = if (this) 1 else 0

    companion object {
        private const val LEGACY_ATB_FORMAT_SUFFIX = "ma"
    }
}
