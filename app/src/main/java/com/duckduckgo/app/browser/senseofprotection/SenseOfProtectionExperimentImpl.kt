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

package com.duckduckgo.app.browser.senseofprotection

import com.duckduckgo.app.browser.senseofprotection.SenseOfProtectionToggles.Cohorts.MODIFIED_CONTROL
import com.duckduckgo.browser.api.UserBrowserProperties
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.Toggle.State.CohortName
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject

private const val EXISTING_USER_DAY_COUNT_THRESHOLD = 28

interface SenseOfProtectionExperiment {

    fun isEnabled(cohort: CohortName): Boolean
    fun getTabManagerPixelParams(): Map<String, String>
}

@ContributesBinding(
    scope = AppScope::class,
    boundType = SenseOfProtectionExperiment::class,
)
@SingleInstanceIn(AppScope::class)
class SenseOfProtectionExperimentImpl @Inject constructor(
    private val userBrowserProperties: UserBrowserProperties,
    private val senseOfProtectionToggles: SenseOfProtectionToggles,
) : SenseOfProtectionExperiment {

    override fun isEnabled(cohortName: CohortName): Boolean {
        return if (userBrowserProperties.daysSinceInstalled() > EXISTING_USER_DAY_COUNT_THRESHOLD) {
            // A user might have already been enrolled in the new user experiment so we need to check they are not part of a cohort before we can
            // enroll them into the existing user experiment
            if (isNotEnrolledInNewUserExperiment()) {
                isExistingUserExperimentEnabled(cohortName)
            } else {
                false
            }
        } else {
            isNewUserExperimentEnabled(cohortName)
        }
    }

    override fun getTabManagerPixelParams(): Map<String, String> {
        return when {
            isEnrolledInNewUserExperiment() -> {
                mapOf(
                    "cohort" to (getNewUserExperimentCohortName() ?: ""),
                    "experiment" to getNewUserExperimentName(),
                )
            }
            isEnrolledInExistingUserExperiment() -> {
                mapOf(
                    "cohort" to (getExistingUserExperimentCohortName() ?: ""),
                    "experiment" to getExistingUserExperimentName(),
                )
            }
            else -> emptyMap()
        }
    }

    private fun isEnrolledInNewUserExperiment(): Boolean =
        senseOfProtectionToggles.senseOfProtectionNewUserExperimentApr25().getCohort() != null

    private fun isEnrolledInExistingUserExperiment(): Boolean =
        senseOfProtectionToggles.senseOfProtectionExistingUserExperimentApr25().getCohort() != null

    private fun isNotEnrolledInNewUserExperiment(): Boolean =
        senseOfProtectionToggles.senseOfProtectionNewUserExperimentApr25().getCohort() == null

    private fun isNewUserExperimentEnabled(cohortName: CohortName): Boolean =
        senseOfProtectionToggles.senseOfProtectionNewUserExperimentApr25().isEnabled(cohort = cohortName)

    private fun isExistingUserExperimentEnabled(cohortName: CohortName): Boolean =
        senseOfProtectionToggles.senseOfProtectionExistingUserExperimentApr25().isEnabled(cohort = cohortName)

    private fun getNewUserExperimentCohortName(): String? =
        senseOfProtectionToggles.senseOfProtectionNewUserExperimentApr25().getCohort()?.name

    private fun getNewUserExperimentName(): String =
        senseOfProtectionToggles.senseOfProtectionNewUserExperimentApr25().featureName().name

    private fun getExistingUserExperimentCohortName(): String? =
        senseOfProtectionToggles.senseOfProtectionExistingUserExperimentApr25().getCohort()?.name

    private fun getExistingUserExperimentName(): String =
        senseOfProtectionToggles.senseOfProtectionExistingUserExperimentApr25().featureName().name
}
