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
import com.duckduckgo.common.ui.experiments.visual.store.ExperimentalThemingDataStore
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.MetricsPixel
import com.duckduckgo.feature.toggles.api.Toggle.State.CohortName
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.logcat

private const val EXISTING_USER_DAY_COUNT_THRESHOLD = 28

interface SenseOfProtectionExperiment {

    suspend fun enrolUserInNewExperimentIfEligible(): Boolean
    suspend fun getTabManagerPixelParams(): Map<String, String>
    fun firePrivacyDashboardClickedPixelIfInExperiment()
    suspend fun isUserEnrolledInAVariantAndExperimentEnabled(): Boolean
    suspend fun isUserEnrolledInVariant1CohortAndExperimentEnabled(): Boolean
    suspend fun isUserEnrolledInVariant2CohortAndExperimentEnabled(): Boolean
    suspend fun isUserEnrolledInModifiedControlCohortAndExperimentEnabled(): Boolean
    suspend fun shouldShowNewPrivacyShield(): Boolean
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
    private val experimentalThemingDataStore: ExperimentalThemingDataStore,
    private val pixel: Pixel,
) : SenseOfProtectionExperiment {

    init {
        // enrol users in the existing user experiment if they are not already enrolled in the new user experiment
        appCoroutineScope.launch(dispatcherProvider.io()) {
            if (userBrowserProperties.daysSinceInstalled() > EXISTING_USER_DAY_COUNT_THRESHOLD) {
                if (canBeEnrolledInExistingUserExperiment()) {
                    enrollInExistingUserExperiment(cohortName = MODIFIED_CONTROL)
                }
            }
        }
    }

    private suspend fun canBeEnrolledInExistingUserExperiment(): Boolean {
        return !isEnrolledInNewUserExperiment() && !seesNewVisualDesign()
    }

    override suspend fun enrolUserInNewExperimentIfEligible(): Boolean {
        return if (canBeEnrolledInNewUserExperiment()) {
            enrollInNewUserExperiment(cohortName = MODIFIED_CONTROL)
        } else {
            false
        }
    }

    private fun canBeEnrolledInNewUserExperiment(): Boolean {
        return (userBrowserProperties.daysSinceInstalled() <= EXISTING_USER_DAY_COUNT_THRESHOLD) && !seesNewVisualDesign()
    }

    override suspend fun isUserEnrolledInModifiedControlCohortAndExperimentEnabled(): Boolean =
        getNewUserExperimentCohortName() == MODIFIED_CONTROL.cohortName && isNewUserExperimentEnabled(MODIFIED_CONTROL) ||
            getExistingUserExperimentCohortName() == MODIFIED_CONTROL.cohortName && isExistingUserExperimentEnabled(MODIFIED_CONTROL)

    override suspend fun isUserEnrolledInVariant1CohortAndExperimentEnabled(): Boolean =
        getNewUserExperimentCohortName() == VARIANT_1.cohortName && isNewUserExperimentEnabled(VARIANT_1) ||
            getExistingUserExperimentCohortName() == VARIANT_1.cohortName && isExistingUserExperimentEnabled(VARIANT_1)

    override suspend fun isUserEnrolledInVariant2CohortAndExperimentEnabled(): Boolean =
        getNewUserExperimentCohortName() == VARIANT_2.cohortName && isNewUserExperimentEnabled(VARIANT_2) ||
            getExistingUserExperimentCohortName() == VARIANT_2.cohortName && isExistingUserExperimentEnabled(VARIANT_2)

    override suspend fun shouldShowNewPrivacyShield(): Boolean {
        return isUserEnrolledInVariant1CohortAndExperimentEnabled() || isUserEnrolledInVariant2CohortAndExperimentEnabled()
    }

    override suspend fun getTabManagerPixelParams(): Map<String, String> {
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

    override suspend fun isUserEnrolledInAVariantAndExperimentEnabled(): Boolean {
        val enrolledInModifiedControl = isUserEnrolledInModifiedControlCohortAndExperimentEnabled()
        val enrolledInVariant1 = isUserEnrolledInVariant1CohortAndExperimentEnabled()
        val enrolledInVariant2 = isUserEnrolledInVariant2CohortAndExperimentEnabled()

        return enrolledInModifiedControl || enrolledInVariant1 || enrolledInVariant2
    }

    private suspend fun enrollInNewUserExperiment(cohortName: CohortName): Boolean {
        senseOfProtectionToggles.senseOfProtectionNewUserExperiment27May25().enroll()

        return senseOfProtectionToggles.senseOfProtectionNewUserExperiment27May25().isEnrolledAndEnabled(cohortName)
    }

    private suspend fun enrollInExistingUserExperiment(cohortName: CohortName): Boolean {
        senseOfProtectionToggles.senseOfProtectionExistingUserExperiment27May25().enroll()

        return senseOfProtectionToggles.senseOfProtectionExistingUserExperiment27May25().isEnrolledAndEnabled(cohortName)
    }

    private suspend fun isUserEnrolledInNewUserExperimentModifiedControlCohortAndExperimentEnabled(): Boolean =
        getNewUserExperimentCohortName() == MODIFIED_CONTROL.cohortName && isNewUserExperimentEnabled(MODIFIED_CONTROL)

    private suspend fun isUserEnrolledInNewUserExperimentVariant1CohortAndExperimentEnabled(): Boolean =
        getNewUserExperimentCohortName() == VARIANT_1.cohortName && isNewUserExperimentEnabled(VARIANT_1)

    private suspend fun isUserEnrolledInNewUserExperimentVariant2CohortAndExperimentEnabled(): Boolean =
        getNewUserExperimentCohortName() == VARIANT_2.cohortName && isNewUserExperimentEnabled(VARIANT_2)

    private suspend fun isUserEnrolledInExistingUserExperimentModifiedControlCohortAndExperimentEnabled(): Boolean =
        getExistingUserExperimentCohortName() == MODIFIED_CONTROL.cohortName && isExistingUserExperimentEnabled(MODIFIED_CONTROL)

    private suspend fun isUserEnrolledInExistingUserExperimentVariant1CohortAndExperimentEnabled(): Boolean =
        getExistingUserExperimentCohortName() == VARIANT_1.cohortName && isExistingUserExperimentEnabled(VARIANT_1)

    private suspend fun isUserEnrolledInExistingUserExperimentVariant2CohortAndExperimentEnabled(): Boolean =
        getExistingUserExperimentCohortName() == VARIANT_2.cohortName && isExistingUserExperimentEnabled(VARIANT_2)

    private suspend fun isEnrolledInNewUserExperiment(): Boolean {
        val enrolledInModifiedControl = isUserEnrolledInNewUserExperimentModifiedControlCohortAndExperimentEnabled()
        val enrolledInVariant1 = isUserEnrolledInNewUserExperimentVariant1CohortAndExperimentEnabled()
        val enrolledInVariant2 = isUserEnrolledInNewUserExperimentVariant2CohortAndExperimentEnabled()
        return enrolledInModifiedControl || enrolledInVariant1 || enrolledInVariant2
    }

    private suspend fun isEnrolledInExistingUserExperiment(): Boolean {
        val enrolledInModifiedControl = isUserEnrolledInExistingUserExperimentModifiedControlCohortAndExperimentEnabled()
        val enrolledInVariant1 = isUserEnrolledInExistingUserExperimentVariant1CohortAndExperimentEnabled()
        val enrolledInVariant2 = isUserEnrolledInExistingUserExperimentVariant2CohortAndExperimentEnabled()
        return enrolledInModifiedControl || enrolledInVariant1 || enrolledInVariant2
    }

    private suspend fun isNewUserExperimentEnabled(cohortName: CohortName): Boolean =
        senseOfProtectionToggles.senseOfProtectionNewUserExperiment27May25().isEnrolledAndEnabled(cohortName)

    private suspend fun isExistingUserExperimentEnabled(cohortName: CohortName): Boolean =
        senseOfProtectionToggles.senseOfProtectionExistingUserExperiment27May25().isEnrolledAndEnabled(cohortName)

    private suspend fun getNewUserExperimentCohortName(): String? =
        senseOfProtectionToggles.senseOfProtectionNewUserExperiment27May25().getCohort()?.name

    private fun getNewUserExperimentName(): String =
        senseOfProtectionToggles.senseOfProtectionNewUserExperiment27May25().featureName().name

    private suspend fun getExistingUserExperimentCohortName(): String? =
        senseOfProtectionToggles.senseOfProtectionExistingUserExperiment27May25().getCohort()?.name

    private fun getExistingUserExperimentName(): String =
        senseOfProtectionToggles.senseOfProtectionExistingUserExperiment27May25().featureName().name

    private fun MetricsPixel.fire() = getPixelDefinitions().forEach {
        pixel.fire(it.pixelName, it.params)
    }

    private fun seesNewVisualDesign(): Boolean {
        val seesNewVisualDesing = experimentalThemingDataStore.isSplitOmnibarEnabled.value
        logcat { "VisualDesign: seesNewVisualDesign $seesNewVisualDesing" }
        return seesNewVisualDesing
    }
}
