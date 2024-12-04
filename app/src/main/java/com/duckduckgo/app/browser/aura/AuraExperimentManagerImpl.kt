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

package com.duckduckgo.app.browser.aura

import com.duckduckgo.app.aura.AuraExperimentManager
import com.duckduckgo.app.referral.AppReferrerDataStore
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.installation.impl.installer.InstallSourceExtractor
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class AuraExperimentManagerImpl @Inject constructor(
    private val auraExperimentFeature: AuraExperimentFeature,
    private val auraExperimentListJsonParser: AuraExperimentListJsonParser,
    private val installSourceExtractor: InstallSourceExtractor,
    private val statisticsDataStore: StatisticsDataStore,
    private val appReferrerDataStore: AppReferrerDataStore,
) : AuraExperimentManager {

    override suspend fun initialize() {
        if (auraExperimentFeature.self().isEnabled()) {
            installSourceExtractor.extract()?.let { source ->
                val settings = auraExperimentFeature.self().getSettings()
                val packages = auraExperimentListJsonParser.parseJson(settings).list
                if (packages.contains(source)) {
                    if (statisticsDataStore.variant == RETURNING_USER) {
                        appReferrerDataStore.returningUser = true
                    }
                    statisticsDataStore.variant = VARIANT
                    appReferrerDataStore.utmOriginAttributeCampaign = ORIGIN
                }
            }
        }
    }

    companion object {
        const val VARIANT = "mq"
        const val RETURNING_USER = "ru"
        const val ORIGIN = "funnel_app_aurapaid_android"
    }
}
