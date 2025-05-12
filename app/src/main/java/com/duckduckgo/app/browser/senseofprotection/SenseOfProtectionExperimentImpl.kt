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
import com.duckduckgo.app.browser.senseofprotection.SenseOfProtectionToggles.Cohorts.VARIANT_1
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
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val EXISTING_USER_DAY_COUNT_THRESHOLD = 28

interface SenseOfProtectionExperiment {

    fun enrolUserInNewExperimentIfEligible(): Boolean
    fun getTabManagerPixelParams(): Map<String, String>
    fun firePrivacyDashboardClickedPixelIfInExperiment()
    fun isUserEnrolledInAVariantAndExperimentEnabled(): Boolean
    fun isUserEnrolledInVariant1CohortAndExperimentEnabled(): Boolean
    fun isUserEnrolledInVariant2CohortAndExperimentEnabled(): Boolean
    fun isUserEnrolledInModifiedControlCohortAndExperimentEnabled(): Boolean
    fun shouldShowNewPrivacyShield(): Boolean
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
        appCoroutineScope.launch(dispatcherProvider.io()) {
            if (userBrowserProperties.daysSinceInstalled() > EXISTING_USER_DAY_COUNT_THRESHOLD) {
                if (!isEnrolledInNewUserExperiment()) {
                    enrollInExistingUserExperiment(cohortName = MODIFIED_CONTROL)
                }
            }
        }
    }

    override fun enrolUserInNewExperimentIfEligible(): Boolean {
        return if (userBrowserProperties.daysSinceInstalled() <= EXISTING_USER_DAY_COUNT_THRESHOLD) {
            enrollInNewUserExperiment(cohortName = MODIFIED_CONTROL)
        } else {
            false
        }
    }

    override fun isUserEnrolledInModifiedControlCohortAndExperimentEnabled(): Boolean =
        getNewUserExperimentCohortName() == MODIFIED_CONTROL.cohortName && isNewUserExperimentEnabled(MODIFIED_CONTROL) ||
            getExistingUserExperimentCohortName() == MODIFIED_CONTROL.cohortName && isExistingUserExperimentEnabled(MODIFIED_CONTROL)

    override fun isUserEnrolledInVariant1CohortAndExperimentEnabled(): Boolean =
        getNewUserExperimentCohortName() == VARIANT_1.cohortName && isNewUserExperimentEnabled(VARIANT_1) ||
            getExistingUserExperimentCohortName() == VARIANT_1.cohortName && isExistingUserExperimentEnabled(VARIANT_1)

    override fun isUserEnrolledInVariant2CohortAndExperimentEnabled(): Boolean =
        getNewUserExperimentCohortName() == VARIANT_2.cohortName && isNewUserExperimentEnabled(VARIANT_2) ||
            getExistingUserExperimentCohortName() == VARIANT_2.cohortName && isExistingUserExperimentEnabled(VARIANT_2)

    override fun shouldShowNewPrivacyShield(): Boolean {
        return isUserEnrolledInVariant1CohortAndExperimentEnabled() || isUserEnrolledInVariant2CohortAndExperimentEnabled()
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

    override fun firePrivacyDashboardClickedPixelIfInExperiment() {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            if (isUserEnrolledInAVariantAndExperimentEnabled()) {
                senseOfProtectionPixelsPlugin.getPrivacyDashboardClickedMetric()?.fire()
            }
        }
    }

    override fun isUserEnrolledInAVariantAndExperimentEnabled(): Boolean {
        val enrolledInModifiedControl = isUserEnrolledInModifiedControlCohortAndExperimentEnabled()
        val enrolledInVariant1 = isUserEnrolledInVariant1CohortAndExperimentEnabled()
        val enrolledInVariant2 = isUserEnrolledInVariant2CohortAndExperimentEnabled()

        return enrolledInModifiedControl || enrolledInVariant1 || enrolledInVariant2
    }

    private fun enrollInNewUserExperiment(cohortName: CohortName): Boolean {
        return senseOfProtectionToggles.senseOfProtectionNewUserExperimentApr25().isEnabled(cohortName)
    }

    private fun enrollInExistingUserExperiment(cohortName: CohortName): Boolean {
        return senseOfProtectionToggles.senseOfProtectionExistingUserExperimentApr25().isEnabled(cohortName)
    }

    private fun isUserEnrolledInNewUserExperimentModifiedControlCohortAndExperimentEnabled(): Boolean =
        getNewUserExperimentCohortName() == MODIFIED_CONTROL.cohortName && isNewUserExperimentEnabled(MODIFIED_CONTROL)

    private fun isUserEnrolledInNewUserExperimentVariant1CohortAndExperimentEnabled(): Boolean =
        getNewUserExperimentCohortName() == VARIANT_1.cohortName && isNewUserExperimentEnabled(VARIANT_1)

    private fun isUserEnrolledInNewUserExperimentVariant2CohortAndExperimentEnabled(): Boolean =
        getNewUserExperimentCohortName() == VARIANT_2.cohortName && isNewUserExperimentEnabled(VARIANT_2)

    private fun isUserEnrolledInExistingUserExperimentModifiedControlCohortAndExperimentEnabled(): Boolean =
        getExistingUserExperimentCohortName() == MODIFIED_CONTROL.cohortName && isExistingUserExperimentEnabled(MODIFIED_CONTROL)

    private fun isUserEnrolledInExistingUserExperimentVariant1CohortAndExperimentEnabled(): Boolean =
        getExistingUserExperimentCohortName() == VARIANT_1.cohortName && isExistingUserExperimentEnabled(VARIANT_1)

    private fun isUserEnrolledInExistingUserExperimentVariant2CohortAndExperimentEnabled(): Boolean =
        getExistingUserExperimentCohortName() == VARIANT_2.cohortName && isExistingUserExperimentEnabled(VARIANT_2)

    private fun isEnrolledInNewUserExperiment(): Boolean {
        val enrolledInModifiedControl = isUserEnrolledInNewUserExperimentModifiedControlCohortAndExperimentEnabled()
        val enrolledInVariant1 = isUserEnrolledInNewUserExperimentVariant1CohortAndExperimentEnabled()
        val enrolledInVariant2 = isUserEnrolledInNewUserExperimentVariant2CohortAndExperimentEnabled()
        return enrolledInModifiedControl || enrolledInVariant1 || enrolledInVariant2
    }

    private fun isEnrolledInExistingUserExperiment(): Boolean {
        val enrolledInModifiedControl = isUserEnrolledInExistingUserExperimentModifiedControlCohortAndExperimentEnabled()
        val enrolledInVariant1 = isUserEnrolledInExistingUserExperimentVariant1CohortAndExperimentEnabled()
        val enrolledInVariant2 = isUserEnrolledInExistingUserExperimentVariant2CohortAndExperimentEnabled()
        return enrolledInModifiedControl || enrolledInVariant1 || enrolledInVariant2
    }

    private fun isNewUserExperimentEnabled(cohortName: CohortName): Boolean =
        senseOfProtectionToggles.senseOfProtectionNewUserExperimentApr25().isEnrolledAndEnabled(cohortName)

    private fun isExistingUserExperimentEnabled(cohortName: CohortName): Boolean =
        senseOfProtectionToggles.senseOfProtectionExistingUserExperimentApr25().isEnrolledAndEnabled(cohortName)

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
}
