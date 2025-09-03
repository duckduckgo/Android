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

package experiments.trackersblocking

import android.annotation.SuppressLint
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.browser.senseofprotection.SenseOfProtectionExperiment
import com.duckduckgo.app.browser.senseofprotection.SenseOfProtectionToggles
import com.duckduckgo.app.browser.senseofprotection.SenseOfProtectionToggles.Cohorts
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.feature.toggles.api.Toggle.State
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@SuppressLint("NoLifecycleObserver") // we don't observe app lifecycle
@ContributesViewModel(ViewScope::class)
class TrackersBlockingExperimentViewModel @Inject constructor(
    private val dispatchers: DispatcherProvider,
    private val senseOfProtectionToggles: SenseOfProtectionToggles,
    private val senseOfProtectionExperiment: SenseOfProtectionExperiment,
) : ViewModel(), DefaultLifecycleObserver {

    data class ViewState(
        val modifiedControl: Boolean = false,
        val variant1: Boolean = false,
        val variant2: Boolean = false,
    )

    private val viewState = MutableStateFlow(ViewState())
    fun viewState(): Flow<ViewState> = viewState.onStart { updateCurrentState() }

    @SuppressLint("DenyListedApi")
    fun onTrackersBlockingVariant1ExperimentalUIModeChanged(checked: Boolean) {
        viewModelScope.launch(dispatchers.io()) {
            val enrollmentDateET = ZonedDateTime.now(ZoneId.of("America/New_York")).toString()
            if (checked) {
                senseOfProtectionToggles.senseOfProtectionExistingUserExperiment27May25().setRawStoredState(
                    State(
                        remoteEnableState = true,
                        enable = true,
                        cohorts = listOf(
                            State.Cohort(name = Cohorts.MODIFIED_CONTROL.cohortName, weight = 1),
                            State.Cohort(name = Cohorts.VARIANT_1.cohortName, weight = 0),
                            State.Cohort(name = Cohorts.VARIANT_2.cohortName, weight = 0),
                        ),
                        assignedCohort = State.Cohort(name = Cohorts.MODIFIED_CONTROL.cohortName, weight = 1, enrollmentDateET = enrollmentDateET),
                    ),
                )
            } else {
                senseOfProtectionToggles.senseOfProtectionExistingUserExperiment27May25().setRawStoredState(
                    State(
                        remoteEnableState = false,
                        enable = false,
                        cohorts = listOf(
                            State.Cohort(name = Cohorts.MODIFIED_CONTROL.cohortName, weight = 0),
                            State.Cohort(name = Cohorts.VARIANT_1.cohortName, weight = 0),
                            State.Cohort(name = Cohorts.VARIANT_2.cohortName, weight = 0),
                        ),
                        assignedCohort = State.Cohort(name = Cohorts.MODIFIED_CONTROL.cohortName, weight = 0, enrollmentDateET = enrollmentDateET),
                    ),
                )
            }
            updateCurrentState()
        }
    }

    @SuppressLint("DenyListedApi")
    fun onTrackersBlockingVariant2ExperimentalUIModeChanged(checked: Boolean) {
        viewModelScope.launch(dispatchers.io()) {
            val enrollmentDateET = ZonedDateTime.now(ZoneId.of("America/New_York")).toString()
            if (checked) {
                senseOfProtectionToggles.senseOfProtectionExistingUserExperiment27May25().setRawStoredState(
                    State(
                        remoteEnableState = true,
                        enable = true,
                        cohorts = listOf(
                            State.Cohort(name = Cohorts.MODIFIED_CONTROL.cohortName, weight = 0),
                            State.Cohort(name = Cohorts.VARIANT_1.cohortName, weight = 1),
                            State.Cohort(name = Cohorts.VARIANT_2.cohortName, weight = 0),
                        ),
                        assignedCohort = State.Cohort(name = Cohorts.VARIANT_1.cohortName, weight = 1, enrollmentDateET = enrollmentDateET),
                    ),
                )
            } else {
                senseOfProtectionToggles.senseOfProtectionExistingUserExperiment27May25().setRawStoredState(
                    State(
                        remoteEnableState = false,
                        enable = false,
                        cohorts = listOf(
                            State.Cohort(name = Cohorts.MODIFIED_CONTROL.cohortName, weight = 0),
                            State.Cohort(name = Cohorts.VARIANT_1.cohortName, weight = 0),
                            State.Cohort(name = Cohorts.VARIANT_2.cohortName, weight = 0),
                        ),
                        assignedCohort = State.Cohort(name = Cohorts.VARIANT_1.cohortName, weight = 0, enrollmentDateET = enrollmentDateET),
                    ),
                )
            }
            updateCurrentState()
        }
    }

    @SuppressLint("DenyListedApi")
    fun onTrackersBlockingVariant3ExperimentalUIModeChanged(checked: Boolean) {
        viewModelScope.launch(dispatchers.io()) {
            val enrollmentDateET = ZonedDateTime.now(ZoneId.of("America/New_York")).toString()
            if (checked) {
                senseOfProtectionToggles.senseOfProtectionExistingUserExperiment27May25().setRawStoredState(
                    State(
                        remoteEnableState = true,
                        enable = true,
                        cohorts = listOf(
                            State.Cohort(name = Cohorts.MODIFIED_CONTROL.cohortName, weight = 0),
                            State.Cohort(name = Cohorts.VARIANT_1.cohortName, weight = 0),
                            State.Cohort(name = Cohorts.VARIANT_2.cohortName, weight = 1),
                        ),
                        assignedCohort = State.Cohort(name = Cohorts.VARIANT_2.cohortName, weight = 1, enrollmentDateET = enrollmentDateET),
                    ),
                )
            } else {
                senseOfProtectionToggles.senseOfProtectionExistingUserExperiment27May25().setRawStoredState(
                    State(
                        remoteEnableState = false,
                        enable = false,
                        cohorts = listOf(
                            State.Cohort(name = Cohorts.MODIFIED_CONTROL.cohortName, weight = 0),
                            State.Cohort(name = Cohorts.VARIANT_1.cohortName, weight = 0),
                            State.Cohort(name = Cohorts.VARIANT_2.cohortName, weight = 0),
                        ),
                        assignedCohort = State.Cohort(name = Cohorts.VARIANT_2.cohortName, weight = 0, enrollmentDateET = enrollmentDateET),
                    ),
                )
            }
            updateCurrentState()
        }
    }

    private fun updateCurrentState() {
        viewModelScope.launch {
            viewState.update {
                ViewState(
                    modifiedControl = senseOfProtectionExperiment.isUserEnrolledInModifiedControlCohortAndExperimentEnabled(),
                    variant1 = senseOfProtectionExperiment.isUserEnrolledInVariant1CohortAndExperimentEnabled(),
                    variant2 = senseOfProtectionExperiment.isUserEnrolledInVariant2CohortAndExperimentEnabled(),
                )
            }
        }
    }
}
