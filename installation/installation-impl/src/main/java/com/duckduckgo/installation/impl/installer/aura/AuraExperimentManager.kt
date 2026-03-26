/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.installation.impl.installer.aura

import com.duckduckgo.anvil.annotations.PriorityKey
import com.duckduckgo.app.statistics.AtbInitializerListener
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.duckduckgo.browser.api.referrer.AppReferrer
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.installation.impl.installer.InstallSourceExtractor
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.withContext
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
@PriorityKey(AtbInitializerListener.PRIORITY_AURA_EXPERIMENT_MANAGER)
@SingleInstanceIn(AppScope::class)
class AuraExperimentManager @Inject constructor(
    private val auraExperimentFeature: AuraExperimentFeature,
    private val auraExperimentListJsonParser: AuraExperimentListJsonParser,
    private val installSourceExtractor: InstallSourceExtractor,
    private val statisticsDataStore: StatisticsDataStore,
    private val appReferrer: AppReferrer,
    private val dispatcherProvider: DispatcherProvider,
) : AtbInitializerListener {

    override suspend fun beforeAtbInit() {
        initialize()
    }

    override fun beforeAtbInitTimeoutMillis(): Long = MAX_WAIT_TIME_MS

    private suspend fun initialize() = withContext(dispatcherProvider.io()) {
        if (auraExperimentFeature.self().isEnabled()) {
            installSourceExtractor.extract()?.let { source ->
                val settings = auraExperimentFeature.self().getSettings()
                val packages = auraExperimentListJsonParser.parseJson(settings).list
                if (packages.contains(source)) {
                    statisticsDataStore.variant = VARIANT
                    appReferrer.setOriginAttributeCampaign(ORIGIN)
                }
            }
        }
    }
    companion object {
        const val VARIANT = "mq"
        const val ORIGIN = "funnel_app_aurapaid_android"
        const val MAX_WAIT_TIME_MS = 1_500L
    }
}
