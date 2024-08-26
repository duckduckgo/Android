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

package com.duckduckgo.app.browser.omnibar

import android.annotation.SuppressLint
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.browser.omnibar.OmnibarViewModel.LeadingIconState.PRIVACY_SHIELD
import com.duckduckgo.app.browser.viewstate.HighlightableButton
import com.duckduckgo.app.browser.viewstate.LoadingViewState
import com.duckduckgo.app.global.model.PrivacyShield
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.voice.api.VoiceSearchAvailability
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

@SuppressLint("NoLifecycleObserver") // we don't observe app lifecycle
@ContributesViewModel(ViewScope::class)
class OmnibarViewModel @Inject constructor(
    private val tabRepository: TabRepository,
    private val voiceSearchAvailability: VoiceSearchAvailability,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel(), DefaultLifecycleObserver {

    // create a FocusedViewState / UnfocusedViewState so we can toggle between the two
    // loading a query or site will update the FocusedViewState but won't be displayed

    data class ViewState(
        val leadingIconState: LeadingIconState = LeadingIconState.SEARCH,
        val privacyShield: PrivacyShield = PrivacyShield.UNKNOWN,
        val loadingState: LoadingViewState = LoadingViewState(),
        val omnibarText: String = "",
        val hasFocus: Boolean = false,
        val shouldMoveCaretToEnd: Boolean = false,
        val forceExpand: Boolean = true,
        val showClearButton: Boolean = false,
        val showVoiceSearch: Boolean = false,
        val showTabsButton: Boolean = true,
        val highlightPrivacyShield: HighlightableButton = HighlightableButton.Visible(enabled = false),
        val highlightFireButton: HighlightableButton = HighlightableButton.Visible(),
        val highlightMenuButton: HighlightableButton = HighlightableButton.Visible(),
        val tabs: List<TabEntity> = emptyList(),
    )

    data class FocusedViewState(
        val omnibarText: String = "",

    )

    enum class LeadingIconState {
        SEARCH,
        PRIVACY_SHIELD,
        DAX,
        GLOBE,
    }

    private val _viewState = MutableStateFlow(ViewState())
    val viewState = _viewState.asStateFlow()

    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)

        tabRepository.flowTabs.onEach { tabs ->
            _viewState.update { ViewState(tabs = tabs) }
        }.flowOn(dispatcherProvider.io()).launchIn(viewModelScope)
    }

    fun onOmnibarFocusChanged(
        hasFocus: Boolean,
        query: String,
    ) {
        // focus vs unfocused mode
        if (hasFocus) {
            _viewState.update {
                ViewState(
                    hasFocus = true,
                    forceExpand = true,
                    leadingIconState = LeadingIconState.SEARCH,
                    highlightPrivacyShield = HighlightableButton.Gone,
                    showClearButton = query.isNotBlank(),
                )
            }
        } else {
            _viewState.update {
                ViewState(
                    hasFocus = false,
                    forceExpand = true,
                    leadingIconState = PRIVACY_SHIELD,
                    showTabsButton = true,
                    highlightFireButton = HighlightableButton.Visible(highlighted = false),
                    highlightMenuButton = HighlightableButton.Visible(highlighted = false),
                    showClearButton = false,
                    showVoiceSearch = voiceSearchAvailability.shouldShowVoiceSearch(
                        hasFocus = hasFocus,
                        query = query,
                        // hasQueryChanged = hasQueryChanged,
                        // urlLoaded = url ?: "",
                        hasQueryChanged = false,
                        urlLoaded = "",
                    ),
                )
            }
        }
    }

    fun onNewLoadingState(loadingState: LoadingViewState) {
        _viewState.update { ViewState(loadingState = loadingState) }
    }
}
