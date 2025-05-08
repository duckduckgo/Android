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
import com.duckduckgo.daxprompts.impl.ReactivateUsersToggles.Cohorts.VARIANT_DUCKPLAYER_PROMPT
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.MetricsPixel
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.withContext

interface ReactivateUsersExperiment {

    suspend fun fireDuckPlayerUseIfInExperiment()
    suspend fun fireSetBrowserAsDefault()
    suspend fun fireDuckPlayerClick()
    suspend fun fireChooseYourBrowserClick()
    suspend fun fireCloseScreen()
    suspend fun fireDismissDuckPlayer()
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
            if (reactivateUsersToggles.reactivateUsersExperimentMay25().isEnrolledAndEnabled(VARIANT_DUCKPLAYER_PROMPT)) {
                reactivateUsersPixelsPlugin.getDuckPlayerUseMetric()?.fire()
            }
        }
    }

    override suspend fun fireSetBrowserAsDefault() {
        withContext(dispatcherProvider.io()) {
            reactivateUsersPixelsPlugin.getSetBrowserAsDefaultMetric()?.fire()
        }
    }

    override suspend fun fireDuckPlayerClick() {
        withContext(dispatcherProvider.io()) {
            reactivateUsersPixelsPlugin.getDuckPlayerClickMetric()?.fire()
        }
    }

    override suspend fun fireChooseYourBrowserClick() {
        withContext(dispatcherProvider.io()) {
            reactivateUsersPixelsPlugin.getChooseYourBrowserClickMetric()?.fire()
        }
    }

    override suspend fun fireCloseScreen() {
        withContext(dispatcherProvider.io()) {
            reactivateUsersPixelsPlugin.getCloseScreenMetric()?.fire()
        }
    }

    override suspend fun fireDismissDuckPlayer() {
        withContext(dispatcherProvider.io()) {
            reactivateUsersPixelsPlugin.getDismissDuckPlayerMetric()?.fire()
        }
    }

    private fun MetricsPixel.fire() = getPixelDefinitions().forEach {
        pixel.fire(it.pixelName, it.params)
    }
}
