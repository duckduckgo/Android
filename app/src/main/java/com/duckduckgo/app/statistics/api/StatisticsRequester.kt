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
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import io.reactivex.schedulers.Schedulers
import timber.log.Timber


interface StatisticsUpdater {
    fun initializeAtb()
    fun refreshRetentionAtb()
}

class StatisticsRequester(private val store: StatisticsDataStore, private val service: StatisticsService) :
    StatisticsUpdater {

    @SuppressLint("CheckResult")
    override fun initializeAtb() {

        if (store.hasInstallationStatistics) {
            Timber.v("Atb already initialized")
            return
        }

        service.atb()
            .subscribeOn(Schedulers.io())
            .flatMap {
                store.atb = it.versionWithVariant
                store.retentionAtb = it.version
                service.exti(it.versionWithVariant)
            }
            .subscribe({
                Timber.v("Atb initalization succeeded")
            }, {
                store.atb = null
                store.retentionAtb = null
                Timber.w("Atb initalization failed ${it.localizedMessage}")
            })
    }

    @SuppressLint("CheckResult")
    override fun refreshRetentionAtb() {

        val atb = store.atb
        val retentionAtb = store.retentionAtb

        if (atb == null || retentionAtb == null) {
            initializeAtb()
            return
        }

        service.updateAtb(atb, retentionAtb)
            .subscribeOn(Schedulers.io())
            .subscribe({
                Timber.v("Atb refresh succeeded")
                store.retentionAtb = it.version
            }, {
                Timber.v("Atb refresh failed with error ${it.localizedMessage}")
            })
    }

}
