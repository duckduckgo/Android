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

package com.duckduckgo.subscriptions.impl.onboarding.welcome

import androidx.lifecycle.ViewModel
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingController
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingStepOutcome.COMPLETED
import com.duckduckgo.subscriptions.impl.onboarding.welcome.SubscriptionOnboardingWelcomeStepPlugin.Companion.WELCOME_STEP_ID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@ContributesViewModel(FragmentScope::class)
class SubscriptionOnboardingWelcomeViewModel @Inject constructor(
    private val controller: SubscriptionOnboardingController,
    private val currentTimeProvider: CurrentTimeProvider,
) : ViewModel() {

    data class ViewState(
        val formattedStartDate: String = "",
        val freeTrialDayLabels: List<String> = emptyList(),
    )

    private val _viewState = MutableStateFlow(buildViewState())
    val viewState: StateFlow<ViewState> = _viewState.asStateFlow()

    private fun buildViewState(): ViewState {
        val startDate = currentTimeProvider.localDateTimeNow().toLocalDate()
        return ViewState(
            formattedStartDate = startDate.format(DATE_FORMATTER),
            freeTrialDayLabels = (0 until FREE_TRIAL_DAYS).map { startDate.plusDays(it.toLong()).dayOfMonth.toString() },
        )
    }

    fun onPrimaryCtaClicked() {
        controller.onStepFinished(WELCOME_STEP_ID, COMPLETED)
    }

    private companion object {
        private const val FREE_TRIAL_DAYS = 7
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("MMMM d, yyyy")
    }
}
