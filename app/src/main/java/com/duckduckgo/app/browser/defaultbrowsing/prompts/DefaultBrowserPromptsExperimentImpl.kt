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

package com.duckduckgo.app.browser.defaultbrowsing.prompts

import android.content.Context
import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserDetector
import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserSystemSettings
import com.duckduckgo.app.browser.defaultbrowsing.prompts.store.DefaultBrowserPromptsDataStore
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.global.DefaultRoleBrowserDialog
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.app.statistics.api.PixelSender
import com.duckduckgo.app.usage.app.AppDaysUsedRepository
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.CONFLATED
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = MainProcessLifecycleObserver::class,
)
@ContributesMultibinding(
    scope = AppScope::class,
    boundType = DefaultBrowserPromptsExperiment::class,
)
class DefaultBrowserPromptsExperimentImpl @Inject constructor(
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val applicationContext: Context,
    private val defaultBrowserPromptsFeatureToggles: DefaultBrowserPromptsFeatureToggles,
    private val defaultBrowserDetector: DefaultBrowserDetector,
    private val defaultRoleBrowserDialog: DefaultRoleBrowserDialog,
    private val defaultBrowserSystemSettings: DefaultBrowserSystemSettings,
    private val appDaysUsedRepository: AppDaysUsedRepository,
    private val defaultBrowserPromptsDataStore: DefaultBrowserPromptsDataStore,
    private val pixelSender: PixelSender,
) : DefaultBrowserPromptsExperiment, MainProcessLifecycleObserver {
    private val _highlightOverflowMenu = MutableStateFlow(false)
    private val _showOverflowMenuItem = MutableStateFlow(false)
    private val _commands = Channel<DefaultBrowserPromptsExperiment.Command>(capacity = CONFLATED, onBufferOverflow = DROP_OLDEST)

    override val highlightOverflowMenu: StateFlow<Boolean> = _highlightOverflowMenu.asStateFlow()
    override val showOverflowMenuItem: StateFlow<Boolean> = _showOverflowMenuItem.asStateFlow()
    override val commands: Flow<DefaultBrowserPromptsExperiment.Command> = _commands.receiveAsFlow()

    // init {
    //     when {
    //         defaultBrowserPromptsFeatureToggles.additionalPrompts().isEnabled(AdditionalPromptsCohorts.VARIANT_1) -> {
    //         }
    //     }
    //     defaultBrowserPromptsFeatureToggles.additionalPrompts().getCohort()
    // }

    override fun onOverflowMenuOpened() {
        TODO("Not yet implemented")
    }

    override fun onOverflowMenuItemClicked() {
        TODO("Not yet implemented")
    }

    override fun onMessageDialogOpened() {
        TODO("Not yet implemented")
    }

    override fun onMessageDialogDismissed() {
        TODO("Not yet implemented")
    }

    override fun onMessageDialogSetBrowserButtonClicked() {
        TODO("Not yet implemented")
    }

    override fun onMessageDialogNotNowButtonClicked() {
        TODO("Not yet implemented")
    }

    override fun onSystemDefaultBrowserDialogOpened() {
        TODO("Not yet implemented")
    }

    override fun onSystemDefaultBrowserDialogSuccess() {
        TODO("Not yet implemented")
    }

    override fun onSystemDefaultBrowserDialogCanceled() {
        TODO("Not yet implemented")
    }

    override fun onSystemDefaultAppsActivityOpened() {
        TODO("Not yet implemented")
    }

    override fun onSystemDefaultAppsActivityClosed() {
        TODO("Not yet implemented")
    }
}
