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
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.daxprompts.impl.pixels.DaxPromptBrowserComparisonPixelName.REACTIVATE_USERS_BROWSER_COMPARISON_PROMPT_CLOSED
import com.duckduckgo.daxprompts.impl.pixels.DaxPromptBrowserComparisonPixelName.REACTIVATE_USERS_BROWSER_COMPARISON_PROMPT_DEFAULT_BROWSER_SET
import com.duckduckgo.daxprompts.impl.pixels.DaxPromptBrowserComparisonPixelName.REACTIVATE_USERS_BROWSER_COMPARISON_PROMPT_PRIMARY_BUTTON_CLICKED
import com.duckduckgo.daxprompts.impl.pixels.DaxPromptBrowserComparisonPixelName.REACTIVATE_USERS_BROWSER_COMPARISON_PROMPT_SHOWN
import com.duckduckgo.daxprompts.impl.pixels.DaxPromptBrowserComparisonPixelParameter.PARAM_NAME_INTERACTION_TYPE
import com.duckduckgo.daxprompts.impl.pixels.DaxPromptBrowserComparisonPixelParameter.PARAM_VALUE_MAYBE_LATER_BUTTON_TAPPED
import com.duckduckgo.daxprompts.impl.pixels.DaxPromptBrowserComparisonPixelParameter.PARAM_VALUE_NAVIGATION_BUTTON_OR_GESTURE_USED
import com.duckduckgo.daxprompts.impl.pixels.DaxPromptBrowserComparisonPixelParameter.PARAM_VALUE_SYSTEM_DIALOG_DISMISSED
import com.duckduckgo.daxprompts.impl.pixels.DaxPromptBrowserComparisonPixelParameter.PARAM_VALUE_X_BUTTON_TAPPED
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
    private val pixel: Pixel,
    private val applicationContext: Context,
) : ViewModel() {

    private val command = Channel<Command>(1, BufferOverflow.DROP_OLDEST)

    fun commands(): Flow<Command> {
        return command.receiveAsFlow()
    }

    fun onPromptShown() {
        viewModelScope.launch {
            pixel.fire(REACTIVATE_USERS_BROWSER_COMPARISON_PROMPT_SHOWN)
        }
    }

    fun onCloseButtonClicked() {
        viewModelScope.launch {
            command.send(Command.CloseScreen())
            pixel.fire(
                pixel = REACTIVATE_USERS_BROWSER_COMPARISON_PROMPT_CLOSED,
                parameters = mapOf(PARAM_NAME_INTERACTION_TYPE to PARAM_VALUE_X_BUTTON_TAPPED),
            )
        }
    }

    fun onPrimaryButtonClicked() {
        viewModelScope.launch {
            if (defaultRoleBrowserDialog.shouldShowDialog()) {
                val intent = defaultRoleBrowserDialog.createIntent(applicationContext)
                if (intent != null) {
                    command.send(Command.BrowserComparisonChart(intent))
                    pixel.fire(REACTIVATE_USERS_BROWSER_COMPARISON_PROMPT_PRIMARY_BUTTON_CLICKED)
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

    fun onGhostButtonClicked() {
        viewModelScope.launch {
            command.send(Command.CloseScreen())
            pixel.fire(
                pixel = REACTIVATE_USERS_BROWSER_COMPARISON_PROMPT_CLOSED,
                parameters = mapOf(PARAM_NAME_INTERACTION_TYPE to PARAM_VALUE_MAYBE_LATER_BUTTON_TAPPED),
            )
        }
    }

    fun onDefaultBrowserSet() {
        defaultRoleBrowserDialog.dialogShown()
        viewModelScope.launch {
            command.send(Command.CloseScreen(true))
            pixel.fire(REACTIVATE_USERS_BROWSER_COMPARISON_PROMPT_DEFAULT_BROWSER_SET)
        }
    }

    fun onDefaultBrowserNotSet() {
        defaultRoleBrowserDialog.dialogShown()
        viewModelScope.launch {
            command.send(Command.CloseScreen(false))
            pixel.fire(
                pixel = REACTIVATE_USERS_BROWSER_COMPARISON_PROMPT_CLOSED,
                parameters = mapOf(PARAM_NAME_INTERACTION_TYPE to PARAM_VALUE_SYSTEM_DIALOG_DISMISSED),
            )
        }
    }

    fun markBrowserComparisonPromptAsShown() {
        viewModelScope.launch {
            daxPromptsRepository.setDaxPromptsBrowserComparisonShown()
        }
    }

    fun onBackNavigation() {
        viewModelScope.launch {
            pixel.fire(
                pixel = REACTIVATE_USERS_BROWSER_COMPARISON_PROMPT_CLOSED,
                parameters = mapOf(PARAM_NAME_INTERACTION_TYPE to PARAM_VALUE_NAVIGATION_BUTTON_OR_GESTURE_USED),
            )
        }
    }

    sealed class Command {
        data class CloseScreen(val defaultBrowserSet: Boolean? = null) : Command()
        data class BrowserComparisonChart(val intent: Intent) : Command()
    }
}
