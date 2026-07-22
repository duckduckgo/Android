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
import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserDetector
import com.duckduckgo.app.global.DefaultRoleBrowserDialog
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.daxprompts.api.LaunchSource
import com.duckduckgo.daxprompts.impl.pixels.DaxPromptBrowserComparisonPixelName.REACTIVATE_USERS_BROWSER_COMPARISON_PROMPT_CLOSED
import com.duckduckgo.daxprompts.impl.pixels.DaxPromptBrowserComparisonPixelName.REACTIVATE_USERS_BROWSER_COMPARISON_PROMPT_DEFAULT_BROWSER_SET
import com.duckduckgo.daxprompts.impl.pixels.DaxPromptBrowserComparisonPixelName.REACTIVATE_USERS_BROWSER_COMPARISON_PROMPT_PRIMARY_BUTTON_CLICKED
import com.duckduckgo.daxprompts.impl.pixels.DaxPromptBrowserComparisonPixelName.REACTIVATE_USERS_BROWSER_COMPARISON_PROMPT_SHOWN
import com.duckduckgo.daxprompts.impl.pixels.DaxPromptBrowserComparisonPixelName.WIN_BACK_PROMPT_DEFAULT_BROWSER_NOT_SET
import com.duckduckgo.daxprompts.impl.pixels.DaxPromptBrowserComparisonPixelName.WIN_BACK_PROMPT_DEFAULT_BROWSER_SET
import com.duckduckgo.daxprompts.impl.pixels.DaxPromptBrowserComparisonPixelName.WIN_BACK_PROMPT_DISMISSED
import com.duckduckgo.daxprompts.impl.pixels.DaxPromptBrowserComparisonPixelName.WIN_BACK_PROMPT_PRIMARY_BUTTON_CLICKED
import com.duckduckgo.daxprompts.impl.pixels.DaxPromptBrowserComparisonPixelName.WIN_BACK_PROMPT_SHOWN
import com.duckduckgo.daxprompts.impl.pixels.DaxPromptBrowserComparisonPixelParameter.PARAM_NAME_INTERACTION_TYPE
import com.duckduckgo.daxprompts.impl.pixels.DaxPromptBrowserComparisonPixelParameter.PARAM_VALUE_MAYBE_LATER_BUTTON_TAPPED
import com.duckduckgo.daxprompts.impl.pixels.DaxPromptBrowserComparisonPixelParameter.PARAM_VALUE_NAVIGATION_BUTTON_OR_GESTURE_USED
import com.duckduckgo.daxprompts.impl.pixels.DaxPromptBrowserComparisonPixelParameter.PARAM_VALUE_SYSTEM_DIALOG_DISMISSED
import com.duckduckgo.daxprompts.impl.pixels.DaxPromptBrowserComparisonPixelParameter.PARAM_VALUE_X_BUTTON_TAPPED
import com.duckduckgo.daxprompts.impl.repository.DaxPromptsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import logcat.logcat

class DaxPromptBrowserComparisonViewModel @AssistedInject constructor(
    @Assisted private val launchSource: LaunchSource,
    private val defaultRoleBrowserDialog: DefaultRoleBrowserDialog,
    private val defaultBrowserDetector: DefaultBrowserDetector,
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
            val pixelName = when (launchSource) {
                LaunchSource.REACTIVATE_USERS -> REACTIVATE_USERS_BROWSER_COMPARISON_PROMPT_SHOWN
                LaunchSource.WIN_BACK -> WIN_BACK_PROMPT_SHOWN
            }
            pixel.fire(pixelName)
        }
    }

    fun onCloseButtonClicked() {
        viewModelScope.launch {
            command.send(Command.CloseScreen())
            when (launchSource) {
                LaunchSource.REACTIVATE_USERS -> pixel.fire(
                    pixel = REACTIVATE_USERS_BROWSER_COMPARISON_PROMPT_CLOSED,
                    parameters = mapOf(PARAM_NAME_INTERACTION_TYPE to PARAM_VALUE_X_BUTTON_TAPPED),
                )
                LaunchSource.WIN_BACK -> fireWinBackDismissedPixel()
            }
        }
    }

    fun onPrimaryButtonClicked() {
        viewModelScope.launch {
            firePrimaryButtonClickedPixel()
            val roleDialogIntent = if (defaultRoleBrowserDialog.shouldShowDialog()) {
                defaultRoleBrowserDialog.createIntent(applicationContext)
            } else {
                null
            }
            if (roleDialogIntent != null) {
                command.send(Command.BrowserComparisonChart(roleDialogIntent))
            } else {
                logcat { "Default role browser dialog unavailable, opening system default apps settings" }
                command.send(Command.LaunchSystemDefaultAppsSettings)
            }
        }
    }

    private fun firePrimaryButtonClickedPixel() {
        val pixelName = when (launchSource) {
            LaunchSource.REACTIVATE_USERS -> REACTIVATE_USERS_BROWSER_COMPARISON_PROMPT_PRIMARY_BUTTON_CLICKED
            LaunchSource.WIN_BACK -> WIN_BACK_PROMPT_PRIMARY_BUTTON_CLICKED
        }
        pixel.fire(pixelName)
    }

    fun onGhostButtonClicked() {
        viewModelScope.launch {
            command.send(Command.CloseScreen())
            when (launchSource) {
                LaunchSource.REACTIVATE_USERS -> pixel.fire(
                    pixel = REACTIVATE_USERS_BROWSER_COMPARISON_PROMPT_CLOSED,
                    parameters = mapOf(PARAM_NAME_INTERACTION_TYPE to PARAM_VALUE_MAYBE_LATER_BUTTON_TAPPED),
                )
                LaunchSource.WIN_BACK -> fireWinBackDismissedPixel()
            }
        }
    }

    fun onDefaultBrowserSet() {
        defaultRoleBrowserDialog.dialogShown()
        handleDefaultBrowserResult(isDefault = true)
    }

    fun onDefaultBrowserNotSet() {
        defaultRoleBrowserDialog.dialogShown()
        handleDefaultBrowserResult(isDefault = false)
    }

    fun onSystemDefaultAppsSettingsReturned() {
        handleDefaultBrowserResult(isDefault = defaultBrowserDetector.isDefaultBrowser())
    }

    private fun handleDefaultBrowserResult(isDefault: Boolean) {
        viewModelScope.launch {
            if (isDefault) {
                val pixelName = when (launchSource) {
                    LaunchSource.REACTIVATE_USERS -> REACTIVATE_USERS_BROWSER_COMPARISON_PROMPT_DEFAULT_BROWSER_SET
                    LaunchSource.WIN_BACK -> WIN_BACK_PROMPT_DEFAULT_BROWSER_SET
                }
                pixel.fire(pixelName)
            } else {
                when (launchSource) {
                    LaunchSource.REACTIVATE_USERS -> pixel.fire(
                        pixel = REACTIVATE_USERS_BROWSER_COMPARISON_PROMPT_CLOSED,
                        parameters = mapOf(PARAM_NAME_INTERACTION_TYPE to PARAM_VALUE_SYSTEM_DIALOG_DISMISSED),
                    )
                    LaunchSource.WIN_BACK -> pixel.fire(WIN_BACK_PROMPT_DEFAULT_BROWSER_NOT_SET)
                }
            }
            command.send(Command.CloseScreen(isDefault))
        }
    }

    fun markBrowserComparisonPromptAsShown() {
        viewModelScope.launch {
            daxPromptsRepository.setDaxPromptsBrowserComparisonShown()
        }
    }

    fun onBackNavigation() {
        viewModelScope.launch {
            when (launchSource) {
                LaunchSource.REACTIVATE_USERS -> pixel.fire(
                    pixel = REACTIVATE_USERS_BROWSER_COMPARISON_PROMPT_CLOSED,
                    parameters = mapOf(PARAM_NAME_INTERACTION_TYPE to PARAM_VALUE_NAVIGATION_BUTTON_OR_GESTURE_USED),
                )
                LaunchSource.WIN_BACK -> fireWinBackDismissedPixel()
            }
        }
    }

    private fun fireWinBackDismissedPixel() {
        pixel.fire(WIN_BACK_PROMPT_DISMISSED)
    }

    sealed class Command {
        data class CloseScreen(val defaultBrowserSet: Boolean? = null) : Command()
        data class BrowserComparisonChart(val intent: Intent) : Command()
        data object LaunchSystemDefaultAppsSettings : Command()
    }

    @AssistedFactory
    interface Factory {
        fun create(launchSource: LaunchSource): DaxPromptBrowserComparisonViewModel
    }
}
