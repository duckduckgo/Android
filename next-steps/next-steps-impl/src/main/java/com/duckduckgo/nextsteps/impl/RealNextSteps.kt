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
package com.duckduckgo.nextsteps.impl

import android.content.Context
import android.view.View
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.nextsteps.api.NextSteps
import com.duckduckgo.nextsteps.impl.NextStepsItemsExperimentToggles.Cohorts
import com.duckduckgo.nextsteps.impl.ui.NextStepsStackedCardsView
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.withContext
import javax.inject.Inject

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealNextSteps @Inject constructor(
    private val nextStepsItemsFeatureToggles: NextStepsItemsExperimentToggles,
    private val appBuildConfig: AppBuildConfig,
    private val dispatcherProvider: DispatcherProvider,
) : NextSteps {

    override suspend fun enroll() = withContext(dispatcherProvider.io()) {
        if (!nextStepsItemsFeatureToggles.self().isEnabled() || appBuildConfig.isAppReinstall()) {
            return@withContext
        }
        nextStepsItemsFeatureToggles.nextStepsItemsExperiment().enroll()
    }

    override suspend fun provideSectionView(context: Context): View? {
        val showStackedCards = withContext(dispatcherProvider.io()) {
            nextStepsItemsFeatureToggles.self().isEnabled() &&
                nextStepsItemsFeatureToggles.nextStepsItemsExperiment().isEnrolledAndEnabled(Cohorts.STACKED_CARDS)
        }
        return if (showStackedCards) NextStepsStackedCardsView(context) else null
    }
}
