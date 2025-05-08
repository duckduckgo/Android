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

package com.duckduckgo.daxprompts.impl

import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.daxprompts.impl.ReactivateUsersToggles.Cohorts.VARIANT_1
import com.duckduckgo.daxprompts.impl.ReactivateUsersToggles.Cohorts.VARIANT_2
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.MetricsPixel
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.withContext

interface ReactivateUsersExperiment {

    suspend fun fireDuckPlayerUseIfInExperiment()
    suspend fun fireSetBrowserAsDefaultIfInExperiment()
    suspend fun fireDuckPlayerClickIfInExperiment()
    suspend fun fireChooseYourBrowserClickIfInExperiment()
    suspend fun fireCloseScreenIfInExperiment()
    suspend fun fireDismissDuckPlayerIfInExperiment()
}

@ContributesBinding(
    scope = AppScope::class,
    boundType = ReactivateUsersExperiment::class,
)
@SingleInstanceIn(AppScope::class)
class ReactivateUsersExperimentImpl @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    private val reactivateUsersToggles: ReactivateUsersToggles,
    private val reactivateUsersPixelsPlugin: ReactivateUsersPixelsPlugin,
    private val pixel: Pixel,
) : ReactivateUsersExperiment {

    override suspend fun fireDuckPlayerUseIfInExperiment() {
        withContext(dispatcherProvider.io()) {
            if (reactivateUsersToggles.reactivateUsersExperimentMay25().isEnrolledAndEnabled(VARIANT_1)) {
                reactivateUsersPixelsPlugin.getDuckPlayerUseMetric()?.fire()
            }
        }
    }

    override suspend fun fireSetBrowserAsDefaultIfInExperiment() {
        withContext(dispatcherProvider.io()) {
            if (reactivateUsersToggles.reactivateUsersExperimentMay25().isEnrolledAndEnabled(VARIANT_2)) {
                reactivateUsersPixelsPlugin.getSetBrowserAsDefaultMetric()?.fire()
            }
        }
    }

    override suspend fun fireDuckPlayerClickIfInExperiment() {
        withContext(dispatcherProvider.io()) {
            if (reactivateUsersToggles.reactivateUsersExperimentMay25().isEnrolledAndEnabled(VARIANT_1)) {
                reactivateUsersPixelsPlugin.getDuckPlayerClickMetric()?.fire()
            }
        }
    }

    override suspend fun fireChooseYourBrowserClickIfInExperiment() {
        withContext(dispatcherProvider.io()) {
            if (reactivateUsersToggles.reactivateUsersExperimentMay25().isEnrolledAndEnabled(VARIANT_2)) {
                reactivateUsersPixelsPlugin.getChooseYourBrowserClickMetric()?.fire()
            }
        }
    }

    override suspend fun fireCloseScreenIfInExperiment() {
        withContext(dispatcherProvider.io()) {
            if (reactivateUsersToggles.reactivateUsersExperimentMay25().isEnrolledAndEnabled(VARIANT_1)) {
                reactivateUsersPixelsPlugin.getCloseScreenMetric()?.fire()
            }
        }
    }

    override suspend fun fireDismissDuckPlayerIfInExperiment() {
        withContext(dispatcherProvider.io()) {
            if (reactivateUsersToggles.reactivateUsersExperimentMay25().isEnrolledAndEnabled(VARIANT_1)) {
                reactivateUsersPixelsPlugin.getDismissDuckPlayerMetric()?.fire()
            }
        }
    }

    private fun MetricsPixel.fire() = getPixelDefinitions().forEach {
        pixel.fire(it.pixelName, it.params)
    }
}
