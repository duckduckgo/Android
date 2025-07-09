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

package com.duckduckgo.daxprompts.impl.ui

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.global.DefaultRoleBrowserDialog
import com.duckduckgo.daxprompts.impl.ReactivateUsersExperiment
import com.duckduckgo.daxprompts.impl.repository.DaxPromptsRepository
import com.duckduckgo.di.scopes.ActivityScope
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import logcat.logcat

@ContributesViewModel(ActivityScope::class)
class DaxPromptBrowserComparisonViewModel @Inject constructor(
    private val defaultRoleBrowserDialog: DefaultRoleBrowserDialog,
    private val daxPromptsRepository: DaxPromptsRepository,
    private val reactivateUsersExperiment: ReactivateUsersExperiment,
    private val applicationContext: Context,
) : ViewModel() {

    private val command = Channel<Command>(1, BufferOverflow.DROP_OLDEST)

    fun commands(): Flow<Command> {
        return command.receiveAsFlow()
    }

    fun onMoreLinkClicked() {
        viewModelScope.launch {
            reactivateUsersExperiment.firePlusEvenMoreProtectionsLinkClick()
            command.send(Command.OpenDetailsPage(BROWSER_COMPARISON_MORE_URL))
        }
    }

    fun onCloseButtonClicked() {
        viewModelScope.launch {
            command.send(Command.CloseScreen())
            reactivateUsersExperiment.fireCloseScreen()
        }
    }

    fun onPrimaryButtonClicked() {
        viewModelScope.launch {
            if (defaultRoleBrowserDialog.shouldShowDialog()) {
                val intent = defaultRoleBrowserDialog.createIntent(applicationContext)
                if (intent != null) {
                    command.send(Command.BrowserComparisonChart(intent))
                    reactivateUsersExperiment.fireChooseYourBrowserClick()
                } else {
                    logcat { "Default browser dialog not available" }
                    command.send(Command.CloseScreen())
                }
            } else {
                logcat { "Default browser dialog should not be shown" }
                command.send(Command.CloseScreen())
            }
        }
    }

    fun onDefaultBrowserSet() {
        defaultRoleBrowserDialog.dialogShown()
        viewModelScope.launch {
            command.send(Command.CloseScreen(true))
            reactivateUsersExperiment.fireSetBrowserAsDefault()
        }
    }

    fun onDefaultBrowserNotSet() {
        defaultRoleBrowserDialog.dialogShown()
        viewModelScope.launch {
            command.send(Command.CloseScreen(false))
        }
    }

    fun markBrowserComparisonPromptAsShown() {
        viewModelScope.launch {
            daxPromptsRepository.setDaxPromptsShowBrowserComparison(false)
        }
    }

    sealed class Command {
        data class CloseScreen(val defaultBrowserSet: Boolean? = null) : Command()
        data class OpenDetailsPage(val url: String) : Command()
        data class BrowserComparisonChart(val intent: Intent) : Command()
    }

    companion object {
        internal const val BROWSER_COMPARISON_MORE_URL = "https://duckduckgo.com/compare-privacy"
    }
}
