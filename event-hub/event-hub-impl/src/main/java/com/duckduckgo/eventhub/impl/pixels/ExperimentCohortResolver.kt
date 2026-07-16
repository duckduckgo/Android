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

package com.duckduckgo.eventhub.impl.pixels

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.FeatureTogglesInventory
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

/**
 * Resolves the experiments (and their assigned cohorts) the user is currently enrolled in, so they
 * can be attached to Event Hub pixels. Works uniformly across experiment types — TDS/blocklist
 * experiments (parent `blockList`) and CSS experiments (parent `contentScopeExperiments`) are both
 * surfaced by the feature-toggles framework as active experiment toggles with an assigned cohort.
 */
interface ExperimentCohortResolver {
    /**
     * @return a map of experiment (sub)feature name to assigned cohort name for every active
     * experiment. Callers apply any `matchExperiments` filtering themselves so the same snapshot can
     * be reused across multiple parameters.
     */
    suspend fun activeExperimentCohorts(): Map<String, String>
}

@ContributesBinding(AppScope::class)
class RealExperimentCohortResolver @Inject constructor(
    private val featureTogglesInventory: FeatureTogglesInventory,
) : ExperimentCohortResolver {

    override suspend fun activeExperimentCohorts(): Map<String, String> {
        return featureTogglesInventory.getAllActiveExperimentToggles()
            .mapNotNull { toggle ->
                val cohort = toggle.getCohort() ?: return@mapNotNull null
                toggle.featureName().name to cohort.name
            }
            .toMap()
    }
}
