/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.app.statistics

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.app.statistics.api.StatisticsUpdater
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.DaggerSet
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.config.api.PrivacyConfigCallbackPlugin
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = MainProcessLifecycleObserver::class,
)
@ContributesMultibinding(
    scope = AppScope::class,
    boundType = PrivacyConfigCallbackPlugin::class,
)
@SingleInstanceIn(AppScope::class)
class AtbInitializer @Inject constructor(
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val statisticsDataStore: StatisticsDataStore,
    private val statisticsUpdater: StatisticsUpdater,
    private val listeners: DaggerSet<AtbInitializerListener>,
    private val dispatcherProvider: DispatcherProvider,
) : MainProcessLifecycleObserver, PrivacyConfigCallbackPlugin {

    override fun onResume(owner: LifecycleOwner) {
        appCoroutineScope.launch(dispatcherProvider.io()) { initialize() }
    }

    @VisibleForTesting
    suspend fun initialize() {
        Timber.v("Initialize ATB")
        listeners.forEach {
            withTimeoutOrNull(it.beforeAtbInitTimeoutMillis()) { it.beforeAtbInit() }
        }

        initializeAtb()
    }

    private fun initializeAtb() {
        if (statisticsDataStore.hasInstallationStatistics) {
            statisticsUpdater.refreshAppRetentionAtb()
        }
    }

    override fun onPrivacyConfigDownloaded() {
        if (!statisticsDataStore.hasInstallationStatistics) {
            // First time we initializeAtb
            statisticsUpdater.initializeAtb()
        }
    }
}
