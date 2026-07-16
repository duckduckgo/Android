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
import logcat.LogPriority.VERBOSE
import logcat.logcat
import java.time.ZonedDateTime
import javax.inject.Inject

/**
 * A single active experiment enrolment resolved for Event Hub reporting.
 *
 * @param name the experiment (sub)feature name, e.g. `tdsNextExperiment007` or `contentScopeExperiment1`.
 * @param cohort the assigned cohort name, e.g. `control` / `treatment`.
 * @param enrollmentDateMillis epoch millis the user was enrolled into the cohort, or null if unknown.
 */
data class ResolvedExperiment(
    val name: String,
    val cohort: String,
    val enrollmentDateMillis: Long?,
)

/**
 * Resolves the experiments (and their cohorts) the user is currently enrolled in, so they can be
 * attached to Event Hub pixels. Works uniformly across experiment types — TDS/blocklist experiments
 * (parent `blockList`) and CSS experiments (parent `contentScopeExperiments`) are both surfaced by
 * the feature-toggles framework as active experiment toggles with an assigned cohort.
 */
interface ExperimentCohortResolver {
    /**
     * @param matchExperiments optional list of name prefixes to filter by. When null, every active
     * experiment is returned. Otherwise only experiments whose name starts with one of the prefixes
     * are returned (e.g. `tdsNextExperiment` matches all TDS experiments).
     */
    suspend fun activeExperiments(matchExperiments: List<String>?): List<ResolvedExperiment>
}

@ContributesBinding(AppScope::class)
class RealExperimentCohortResolver @Inject constructor(
    private val featureTogglesInventory: FeatureTogglesInventory,
) : ExperimentCohortResolver {

    override suspend fun activeExperiments(matchExperiments: List<String>?): List<ResolvedExperiment> {
        return featureTogglesInventory.getAllActiveExperimentToggles()
            .mapNotNull { toggle ->
                val name = toggle.featureName().name
                if (!matches(name, matchExperiments)) return@mapNotNull null
                val cohort = toggle.getCohort() ?: return@mapNotNull null
                ResolvedExperiment(
                    name = name,
                    cohort = cohort.name,
                    enrollmentDateMillis = parseEnrollmentDate(cohort.enrollmentDateET),
                )
            }
            .sortedBy { it.name }
    }

    private fun matches(name: String, matchExperiments: List<String>?): Boolean {
        if (matchExperiments == null) return true
        return matchExperiments.any { prefix -> name.startsWith(prefix) }
    }

    private fun parseEnrollmentDate(enrollmentDateET: String?): Long? {
        if (enrollmentDateET == null) return null
        return runCatching { ZonedDateTime.parse(enrollmentDateET).toInstant().toEpochMilli() }
            .onFailure { logcat(VERBOSE) { "EventHub: failed to parse enrollmentDate '$enrollmentDateET': ${it.message}" } }
            .getOrNull()
    }
}
