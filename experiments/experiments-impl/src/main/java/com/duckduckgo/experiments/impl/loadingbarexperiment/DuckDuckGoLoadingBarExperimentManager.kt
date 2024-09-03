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

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.experiments.api.loadingbarexperiment.LoadingBarExperimentManager
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class DuckDuckGoLoadingBarExperimentManager @Inject constructor(
    private val loadingBarExperimentDataStore: LoadingBarExperimentDataStore,
    private val loadingBarExperimentFeature: LoadingBarExperimentFeature,
) : LoadingBarExperimentManager {

    private var hasVariant: Boolean? = null
    private var enabled: Boolean? = null

    override fun isExperimentEnabled(): Boolean {
        if (hasVariant == null) {
            hasVariant = loadingBarExperimentDataStore.hasVariant
        }

        if (enabled == null) {
            enabled = loadingBarExperimentFeature.self().isEnabled()
        }

        return hasVariant == true && enabled == true
    }

    override val variant: Boolean
        get() = loadingBarExperimentDataStore.variant
}
