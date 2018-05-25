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
import com.duckduckgo.app.global.AppUrl.ParamValue
import com.duckduckgo.app.statistics.Variant
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.statistics.model.Atb
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import io.reactivex.schedulers.Schedulers
import timber.log.Timber


interface StatisticsUpdater {
    fun initializeAtb()
    fun refreshRetentionAtb()
}

class StatisticsRequester(
    private val store: StatisticsDataStore,
    private val service: StatisticsService,
    private val variantManager: VariantManager
) :
    StatisticsUpdater {

    @SuppressLint("CheckResult")
    override fun initializeAtb() {

        if (store.hasInstallationStatistics) {
            Timber.v("Atb already initialized")

            val storedAtb = store.atb
            if (storedAtbFormatNeedsCorrecting(storedAtb)) {
                Timber.d("Previous app version stored hardcoded `ma` variant in ATB param; we want to correct this behaviour")
                store.atb = Atb(storedAtb!!.version.removeSuffix(PREVIOUS_ATB_FORMAT_SUFFIX))
                store.variant = VariantManager.DEFAULT_VARIANT.key
            }
            return
        }

        service.atb(ParamValue.appVersion)
            .subscribeOn(Schedulers.io())
            .flatMap {
                val atb = Atb(it.version)
                store.saveAtb(atb)
                val atbWithVariant = atb.formatWithVariant(variantManager.getVariant())
                service.exti(atbWithVariant, ParamValue.appVersion)
            }
            .subscribe({
                Timber.v("Atb initialization succeeded")
            }, {
                store.clearAtb()
                Timber.w("Atb initialization failed ${it.localizedMessage}")
            })
    }

    private fun storedAtbFormatNeedsCorrecting(storedAtb: Atb?): Boolean {
        return storedAtb != null && storedAtb.version.endsWith(PREVIOUS_ATB_FORMAT_SUFFIX)
    }

    @SuppressLint("CheckResult")
    override fun refreshRetentionAtb() {

        val atb = store.atb
        val retentionAtb = store.retentionAtb

        if (atb == null || retentionAtb == null) {
            initializeAtb()
            return
        }


        val fullAtb = atb.formatWithVariant(variantManager.getVariant())

        service.updateAtb(fullAtb, retentionAtb, ParamValue.appVersion)
            .subscribeOn(Schedulers.io())
            .subscribe({
                Timber.v("Atb refresh succeeded")
                store.retentionAtb = it.version
            }, {
                Timber.v("Atb refresh failed with error ${it.localizedMessage}")
            })
    }

    private fun formatAtbWithVariant(atb: String, variant: Variant): String {
        return atb + variant.key
    }

    companion object {
        private const val PREVIOUS_ATB_FORMAT_SUFFIX = "ma"
    }

}
