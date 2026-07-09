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

package com.duckduckgo.adblocking.impl.menu

import com.duckduckgo.adblocking.impl.AdBlockingSettingsRepository
import com.duckduckgo.adblocking.impl.domain.AdBlockingState
import com.duckduckgo.adblocking.impl.domain.AdBlockingStatusChecker
import com.duckduckgo.adblocking.impl.store.AdBlockingSessionStore
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

interface AdBlockingMenuController {

    /**
     * The option currently in effect, used to place the checkmark on the matching sheet row.
     */
    fun currentChoice(): AdBlockingChoice

    /**
     * Applies the user's selection to the persisted setting and/or the session override.
     */
    fun onChoiceSelected(choice: AdBlockingChoice)
}

@ContributesBinding(AppScope::class)
class RealAdBlockingMenuController @Inject constructor(
    private val settingsRepository: AdBlockingSettingsRepository,
    private val sessionStore: AdBlockingSessionStore,
    private val statusChecker: AdBlockingStatusChecker,
    @AppCoroutineScope private val appScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) : AdBlockingMenuController {

    override fun currentChoice(): AdBlockingChoice = when (statusChecker.currentState()) {
        is AdBlockingState.Enabled -> AdBlockingChoice.ALWAYS_ON
        AdBlockingState.Disabled.UntilRelaunch -> AdBlockingChoice.DISABLE_UNTIL_RELAUNCH
        else -> AdBlockingChoice.ALWAYS_OFF
    }

    override fun onChoiceSelected(choice: AdBlockingChoice) {
        appScope.launch(dispatcherProvider.io()) {
            when (choice) {
                AdBlockingChoice.ALWAYS_ON -> {
                    settingsRepository.setEnabled(true)
                    sessionStore.clear()
                }
                AdBlockingChoice.DISABLE_UNTIL_RELAUNCH -> sessionStore.setDisabledUntilRelaunch()
                AdBlockingChoice.ALWAYS_OFF -> {
                    settingsRepository.setEnabled(false)
                    sessionStore.clear()
                }
            }
        }
    }
}
