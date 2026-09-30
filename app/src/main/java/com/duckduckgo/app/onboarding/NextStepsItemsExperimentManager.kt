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

package com.duckduckgo.app.onboarding

import com.duckduckgo.app.onboarding.NextStepsItemsExperimentManager.NextStepsItemsExperimentVariant
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.remote.messaging.impl.nextstepsitems.NextStepsItemsExperimentToggles
import com.duckduckgo.remote.messaging.impl.nextstepsitems.NextStepsItemsExperimentToggles.Cohorts
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface NextStepsItemsExperimentManager {
    suspend fun enroll(): NextStepsItemsExperimentVariant?

    enum class NextStepsItemsExperimentVariant {
        CONTROL,
        STACKED_CARDS,
        CHECK_LIST,
    }
}

@ContributesBinding(AppScope::class, boundType = NextStepsItemsExperimentManager::class)
@SingleInstanceIn(AppScope::class)
class NextStepsItemsExperimentManagerImpl @Inject constructor(
    private val nextStepsItemsFeatureToggles: NextStepsItemsExperimentToggles,
    private val appBuildConfig: AppBuildConfig,
    private val dispatcherProvider: DispatcherProvider,
    private val onboardingPrivacyConfigPersistedGate: OnboardingPrivacyConfigPersistedGate,
) : NextStepsItemsExperimentManager {

    override suspend fun enroll(): NextStepsItemsExperimentVariant? = withContext(dispatcherProvider.io()) {
        if (!onboardingPrivacyConfigPersistedGate.awaitPersisted()) {
            return@withContext null
        }
        if (!nextStepsItemsFeatureToggles.self().isEnabled() || appBuildConfig.isAppReinstall()) {
            return@withContext null
        }

        val toggle = nextStepsItemsFeatureToggles.nextStepsItemsExperiment()
        toggle.enroll()
        when {
            toggle.isEnrolledAndEnabled(Cohorts.STACKED_CARDS) -> NextStepsItemsExperimentVariant.STACKED_CARDS
            toggle.isEnrolledAndEnabled(Cohorts.CHECK_LIST) -> NextStepsItemsExperimentVariant.CHECK_LIST
            toggle.isEnrolledAndEnabled(Cohorts.CONTROL) -> NextStepsItemsExperimentVariant.CONTROL
            else -> null
        }
    }
}
