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
import com.duckduckgo.app.browser.senseofprotection.SenseOfProtectionToggles.Cohorts.VARIANT_2
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.browser.api.UserBrowserProperties
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.MetricsPixel
import com.duckduckgo.feature.toggles.api.Toggle.State.CohortName
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val EXISTING_USER_DAY_COUNT_THRESHOLD = 28

interface SenseOfProtectionExperiment {

    fun enrolUserInNewExperimentIfEligible(): Boolean
    fun getTabManagerPixelParams(): Map<String, String>
    fun firePrivacyDashboardClickedPixelIfInExperiment()
    fun isUserEnrolledInVariant2CohortAndExperimentEnabled(): Boolean
}

@ContributesBinding(
    scope = AppScope::class,
    boundType = SenseOfProtectionExperiment::class,
)
@SingleInstanceIn(AppScope::class)
class SenseOfProtectionExperimentImpl @Inject constructor(
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val userBrowserProperties: UserBrowserProperties,
    private val senseOfProtectionToggles: SenseOfProtectionToggles,
    private val senseOfProtectionPixelsPlugin: SenseOfProtectionPixelsPlugin,
    private val pixel: Pixel,
) : SenseOfProtectionExperiment {

    init {
        // enrol users in the existing user experiment if they are not already enrolled in the new user experiment
        if (userBrowserProperties.daysSinceInstalled() > EXISTING_USER_DAY_COUNT_THRESHOLD) {
            if (isNotEnrolledInNewUserExperiment()) {
                isExistingUserExperimentEnabled(cohortName = MODIFIED_CONTROL)
            }
        }
    }

    override fun enrolUserInNewExperimentIfEligible(): Boolean {
        return if (userBrowserProperties.daysSinceInstalled() <= EXISTING_USER_DAY_COUNT_THRESHOLD) {
            isNewUserExperimentEnabled(cohortName = MODIFIED_CONTROL)
        } else {
            false
        }
    }

    override fun isUserEnrolledInVariant2CohortAndExperimentEnabled(): Boolean =
        getNewUserExperimentCohortName() == VARIANT_2.cohortName && isNewUserExperimentEnabled(VARIANT_2) ||
        getExistingUserExperimentCohortName() == VARIANT_2.cohortName && isExistingUserExperimentEnabled(VARIANT_2)

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

    override fun firePrivacyDashboardClickedPixelIfInExperiment() {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            if (isUserEnrolledInVariantAndExperimentEnabled()) {
                senseOfProtectionPixelsPlugin.getPrivacyDashboardClickedMetric()?.fire()
            }
        }
    }

    private fun isUserEnrolledInVariantAndExperimentEnabled(): Boolean {
        return when {
            isEnrolledInNewUserExperiment() -> {
                val cohortNameString = senseOfProtectionToggles.senseOfProtectionNewUserExperimentApr25().getCohort()?.name
                val cohortName = cohortNameString?.let {
                    SenseOfProtectionToggles.Cohorts.valueOf(it)
                } ?: return false

                isNewUserExperimentEnabled(cohortName) && cohortName.isVariantCohort()
            }

            isEnrolledInExistingUserExperiment() -> {
                val cohortNameString = senseOfProtectionToggles.senseOfProtectionExistingUserExperimentApr25().getCohort()?.name
                val cohortName = cohortNameString?.let {
                    SenseOfProtectionToggles.Cohorts.valueOf(it)
                } ?: return false

                isExistingUserExperimentEnabled(cohortName) && cohortName.isVariantCohort()
            }

            else -> false
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

    private fun MetricsPixel.fire() = getPixelDefinitions().forEach {
        pixel.fire(it.pixelName, it.params)
    }

    private fun SenseOfProtectionToggles.Cohorts?.isVariantCohort(): Boolean = this != MODIFIED_CONTROL &&
        this in setOf(SenseOfProtectionToggles.Cohorts.VARIANT_1, VARIANT_2)
}
