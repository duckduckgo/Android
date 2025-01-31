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

package com.duckduckgo.app.browser.defaultbrowsing.prompts

import android.content.Intent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface DefaultBrowserPromptsExperiment {

    val highlightPopupMenu: StateFlow<Boolean>
    val showSetAsDefaultPopupMenuItem: StateFlow<Boolean>
    val commands: Flow<Command>

    fun onPopupMenuLaunched()
    fun onSetAsDefaultPopupMenuItemSelected()

    fun onMessageDialogShown()
    fun onMessageDialogDismissed()
    fun onMessageDialogConfirmationButtonClicked()
    fun onMessageDialogNotNowButtonClicked()

    fun onSystemDefaultBrowserDialogShown()
    fun onSystemDefaultBrowserDialogSuccess()
    fun onSystemDefaultBrowserDialogCanceled()

    fun onSystemDefaultAppsActivityOpened()
    fun onSystemDefaultAppsActivityClosed()

    sealed class Command {
        data object OpenMessageDialog : Command()
        data class OpenSystemDefaultBrowserDialog(val intent: Intent) : Command()
        data class OpenSystemDefaultAppsActivity(val intent: Intent) : Command()
    }
}
