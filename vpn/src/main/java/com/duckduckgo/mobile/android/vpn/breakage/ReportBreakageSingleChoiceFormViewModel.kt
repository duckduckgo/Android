/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.breakage

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.vpn.R
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@ContributesViewModel(ActivityScope::class)
class ReportBreakageSingleChoiceFormViewModel @Inject constructor(
    private val dispatcherProvider: DispatcherProvider
) : ViewModel() {

    private var selectedChoice: Choice? = null

    private val choices = MutableStateFlow(CHOICES)
    private var refreshTickerChannel = MutableStateFlow(System.currentTimeMillis())

    private val command = Channel<ReportBreakageSingleChoiceFormView.Command>(1, BufferOverflow.DROP_OLDEST)
    internal fun commands(): Flow<ReportBreakageSingleChoiceFormView.Command> = command.receiveAsFlow()

    internal fun getChoices(): Flow<ReportBreakageSingleChoiceFormView.State> {
        return choices.asStateFlow()
            .combine(refreshTickerChannel) { choices, _ -> choices }
            .map { it.update(selectedChoice) }
            .map { ReportBreakageSingleChoiceFormView.State(it, canSubmit = it.any { choice -> choice.isSelected }) }
    }

    internal fun onChoiceSelected(choice: Choice) {
        selectedChoice = choice.copy(isSelected = true)
        viewModelScope.launch(dispatcherProvider.main()) { refreshTickerChannel.emit(System.currentTimeMillis()) }
    }

    private fun List<Choice>.update(newValue: Choice?): List<Choice> {
        return toMutableList().map {
            if (it.questionStringRes == newValue?.questionStringRes) newValue else it
        }
    }

    fun onSubmitChoices() {
        viewModelScope.launch(dispatcherProvider.main()) {
            selectedChoice?.let {
                command.send(ReportBreakageSingleChoiceFormView.Command.SubmitChoice(it))
            }
        }
    }

    companion object {
        @VisibleForTesting
        val CHOICES = listOf(
            Choice(R.string.atp_ReportBreakageChoiceLoggedIn),
            Choice(R.string.atp_ReportBreakageChoiceProblemWhenLogin),
            Choice(R.string.atp_ReportBreakageChoiceProblemCreatingAccount),
            Choice(R.string.atp_ReportBreakageChoiceNoLogging),
        )
    }
}
