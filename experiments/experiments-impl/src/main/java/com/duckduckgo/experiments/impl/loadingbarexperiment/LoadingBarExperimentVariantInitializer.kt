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

package com.duckduckgo.experiments.impl.loadingbarexperiment

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.experiments.impl.loadingbarexperiment.LoadingBarExperimentPixels.LOADING_BAR_EXPERIMENT_ENROLLMENT_CONTROL
import com.duckduckgo.experiments.impl.loadingbarexperiment.LoadingBarExperimentPixels.LOADING_BAR_EXPERIMENT_ENROLLMENT_TEST
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.apache.commons.math3.distribution.EnumeratedIntegerDistribution

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = MainProcessLifecycleObserver::class,
)
@SingleInstanceIn(AppScope::class)
class LoadingBarExperimentVariantInitializer @Inject constructor(
    private val loadingBarExperimentDataStore: LoadingBarExperimentDataStore,
    private val loadingBarExperimentFeature: LoadingBarExperimentFeature,
    private val pixel: Pixel,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) : MainProcessLifecycleObserver {

    override fun onResume(owner: LifecycleOwner) {
        appCoroutineScope.launch(dispatcherProvider.io()) { initialize() }
    }

    @VisibleForTesting
    suspend fun initialize() {
        if (!loadingBarExperimentDataStore.hasVariant &&
            loadingBarExperimentFeature.self().isEnabled() &&
            loadingBarExperimentFeature.allocateVariants().isEnabled()
        ) {
            loadingBarExperimentDataStore.variant = generateRandomBoolean()
            if (loadingBarExperimentDataStore.variant) {
                pixel.fire(LOADING_BAR_EXPERIMENT_ENROLLMENT_TEST)
            } else {
                pixel.fire(LOADING_BAR_EXPERIMENT_ENROLLMENT_CONTROL)
            }
        }
    }

    // Test variant = true, Control variant = false
    private fun generateRandomBoolean(): Boolean {
        val values = intArrayOf(0, 1)
        val probabilities = doubleArrayOf(0.5, 0.5)
        val distribution = EnumeratedIntegerDistribution(values, probabilities)
        return distribution.sample() == 1
    }
}
