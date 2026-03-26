/*
 * Copyright (c) 2025 DuckDuckGo
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

import com.duckduckgo.app.cta.db.DismissedCtaDao
import com.duckduckgo.app.cta.model.CtaId
import com.duckduckgo.app.onboarding.store.AppStage
import com.duckduckgo.app.onboarding.store.UserStageStore
import com.duckduckgo.app.onboarding.ui.page.extendedonboarding.ExtendedOnboardingFeatureToggles
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.withContext
import javax.inject.Inject

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class OnboardingFlowCheckerImpl @Inject constructor(
    private val dismissedCtaDao: DismissedCtaDao,
    private val extendedOnboardingFeatureToggles: ExtendedOnboardingFeatureToggles,
    private val settingsDataStore: SettingsDataStore,
    private val userStageStore: UserStageStore,
    private val dispatcher: DispatcherProvider,
) : OnboardingFlowChecker {

    override suspend fun isOnboardingComplete(): Boolean {
        // TODO Consider adding allOnboardingCtasShown() in the future.
        //  See https://app.asana.com/1/137249556945/project/414730916066338/task/1212406513605392
        return withContext(dispatcher.io()) {
            val noBrowserCtaExperiment = extendedOnboardingFeatureToggles.noBrowserCtas().isEnabled()
            noBrowserCtaExperiment ||
                settingsDataStore.hideTips ||
                dismissedCtaDao.exists(CtaId.ADD_WIDGET) ||
                userStageStore.getUserAppStage() == AppStage.ESTABLISHED
        }
    }
}
