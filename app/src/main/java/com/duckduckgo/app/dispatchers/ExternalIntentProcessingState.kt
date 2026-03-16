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

package com.duckduckgo.app.dispatchers

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

interface ExternalIntentProcessingState {
    val hasPendingTabLaunch: Boolean
    val hasPendingDuckAiOpen: Boolean
    val hasPendingSnackbar: Boolean
    fun onIntentRequestToChangeTab()
    fun onIntentRequestToOpenDuckAi()
    fun onIntentRequestToShowSnackbar()
    fun onPendingSnackbarDisplayed()
    fun onDuckAiClosed()
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class ExternalIntentProcessingStateImpl @Inject constructor(
    @AppCoroutineScope coroutineScope: CoroutineScope,
    tabRepository: TabRepository,
) : ExternalIntentProcessingState {
    override var hasPendingTabLaunch: Boolean = false
        private set

    override var hasPendingDuckAiOpen: Boolean = false
        private set

    override var hasPendingSnackbar: Boolean = false
        private set

    init {
        tabRepository.flowSelectedTab.filterNotNull().onEach { tab ->
            // if we are switching to a tab that already has a URL, consider tab launch processing complete
            if (!tab.url.isNullOrBlank()) {
                hasPendingTabLaunch = false
            }
        }.launchIn(coroutineScope)
    }

    override fun onIntentRequestToChangeTab() {
        hasPendingTabLaunch = true
    }

    override fun onIntentRequestToOpenDuckAi() {
        hasPendingDuckAiOpen = true
    }

    override fun onDuckAiClosed() {
        hasPendingDuckAiOpen = false
    }

    override fun onIntentRequestToShowSnackbar() {
        hasPendingSnackbar = true
    }

    override fun onPendingSnackbarDisplayed() {
        hasPendingSnackbar = false
    }
}
