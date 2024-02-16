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

package com.duckduckgo.app.onboarding.ui.page.experiment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.onboarding.ui.page.experiment.ExperimentWelcomePage.Companion.PreOnboardingDialogType
import com.duckduckgo.app.onboarding.ui.page.experiment.ExperimentWelcomePage.Companion.PreOnboardingDialogType.CELEBRATION
import com.duckduckgo.app.onboarding.ui.page.experiment.ExperimentWelcomePage.Companion.PreOnboardingDialogType.COMPARISON_CHART
import com.duckduckgo.app.onboarding.ui.page.experiment.ExperimentWelcomePage.Companion.PreOnboardingDialogType.INITIAL
import com.duckduckgo.app.onboarding.ui.page.experiment.ExperimentWelcomePageViewModel.Command.Finish
import com.duckduckgo.app.onboarding.ui.page.experiment.ExperimentWelcomePageViewModel.Command.ShowComparisonChart
import com.duckduckgo.app.onboarding.ui.page.experiment.ExperimentWelcomePageViewModel.Command.ShowSuccessDialog
import com.duckduckgo.di.scopes.FragmentScope
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@ContributesViewModel(FragmentScope::class)
class ExperimentWelcomePageViewModel @Inject constructor() : ViewModel() {

    private val _commands = Channel<Command>(1, DROP_OLDEST)
    val commands: Flow<Command> = _commands.receiveAsFlow()

    sealed interface Command {
        data object ShowComparisonChart : Command
        data object ShowSuccessDialog : Command
        data object Finish : Command
    }

    fun onPrimaryCtaClicked(currentDialog: PreOnboardingDialogType) {
        when (currentDialog) {
            INITIAL -> {
                viewModelScope.launch {
                    _commands.send(ShowComparisonChart)
                }
            }
            COMPARISON_CHART -> {
                viewModelScope.launch {
                    _commands.send(ShowSuccessDialog)
                }
            }
            CELEBRATION -> {
                viewModelScope.launch {
                    _commands.send(Finish)
                }
            }
        }
    }
}
