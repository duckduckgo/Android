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

package com.duckduckgo.app.browser.webview.profile

import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject

/**
 * Event emitted when tabs need to be reset after a profile switch.
 */
data object ResetTabsForProfileSwitchEvent

/**
 * Handles communication between ClearPersonalDataAction and BrowserActivity
 * for resetting tabs after a WebView profile switch.
 *
 * When a profile switch occurs:
 * 1. ClearPersonalDataAction calls [requestReset]
 * 2. BrowserActivity observes [resetEvent] and removes all tab fragments
 * 3. New fragments are created with the new WebView profile
 */
interface ProfileSwitchTabsResetter {
    /**
     * Flow of reset events that BrowserActivity observes.
     */
    val resetEvent: Flow<ResetTabsForProfileSwitchEvent>

    /**
     * Request a tab reset after profile switch.
     */
    suspend fun requestReset()
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealProfileSwitchTabsResetter @Inject constructor() : ProfileSwitchTabsResetter {

    private val _resetEvent = MutableSharedFlow<ResetTabsForProfileSwitchEvent>()
    override val resetEvent: Flow<ResetTabsForProfileSwitchEvent> = _resetEvent.asSharedFlow()

    override suspend fun requestReset() {
        _resetEvent.emit(ResetTabsForProfileSwitchEvent)
    }
}
