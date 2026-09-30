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

import com.duckduckgo.app.onboarding.NextStepsCardsExperimentManager.NextStepsCardsExperimentVariant
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.remote.messaging.impl.nextstepscards.NextStepsCardsExperimentToggles
import com.duckduckgo.remote.messaging.impl.nextstepscards.NextStepsCardsExperimentToggles.Cohorts
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface NextStepsCardsExperimentManager {
    suspend fun enroll(): NextStepsCardsExperimentVariant?

    enum class NextStepsCardsExperimentVariant {
        CONTROL,
        STACKED_CARDS,
        CHECK_LIST,
    }
}

@ContributesBinding(AppScope::class, boundType = NextStepsCardsExperimentManager::class)
@SingleInstanceIn(AppScope::class)
class NextStepsCardsExperimentManagerImpl @Inject constructor(
    private val nextStepsCardsFeatureToggles: NextStepsCardsExperimentToggles,
    private val appBuildConfig: AppBuildConfig,
    private val dispatcherProvider: DispatcherProvider,
    private val onboardingPrivacyConfigPersistedGate: OnboardingPrivacyConfigPersistedGate,
) : NextStepsCardsExperimentManager {

    override suspend fun enroll(): NextStepsCardsExperimentVariant? = withContext(dispatcherProvider.io()) {
        if (!onboardingPrivacyConfigPersistedGate.awaitPersisted()) {
            return@withContext null
        }
        if (!nextStepsCardsFeatureToggles.self().isEnabled() || appBuildConfig.isAppReinstall()) {
            return@withContext null
        }

        val toggle = nextStepsCardsFeatureToggles.nextStepsCardsExperiment()
        toggle.enroll()
        when {
            toggle.isEnrolledAndEnabled(Cohorts.STACKED_CARDS) -> NextStepsCardsExperimentVariant.STACKED_CARDS
            toggle.isEnrolledAndEnabled(Cohorts.CHECK_LIST) -> NextStepsCardsExperimentVariant.CHECK_LIST
            toggle.isEnrolledAndEnabled(Cohorts.CONTROL) -> NextStepsCardsExperimentVariant.CONTROL
            else -> null
        }
    }
}
