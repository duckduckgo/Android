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
import com.duckduckgo.app.browser.DuckDuckGoUrlDetector
import com.duckduckgo.app.browser.omnibar.OmnibarViewModel.BrowserState.Browser
import com.duckduckgo.app.browser.omnibar.OmnibarViewModel.BrowserState.Error
import com.duckduckgo.app.browser.omnibar.OmnibarViewModel.LeadingIconState.PRIVACY_SHIELD
import com.duckduckgo.app.browser.viewstate.FindInPageViewState
import com.duckduckgo.app.browser.viewstate.HighlightableButton
import com.duckduckgo.app.browser.viewstate.LoadingViewState
import com.duckduckgo.app.browser.viewstate.OmnibarViewState
import com.duckduckgo.app.global.model.PrivacyShield
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.voice.api.VoiceSearchAvailability
import com.duckduckgo.voice.api.VoiceSearchAvailabilityPixelLogger
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

@SuppressLint("NoLifecycleObserver") // we don't observe app lifecycle
@ContributesViewModel(ViewScope::class)
class OmnibarViewModel @Inject constructor(
    private val tabRepository: TabRepository,
    private val voiceSearchAvailability: VoiceSearchAvailability,
    private val voiceSearchPixelLogger: VoiceSearchAvailabilityPixelLogger,
    private val duckDuckGoUrlDetector: DuckDuckGoUrlDetector,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel(), DefaultLifecycleObserver {

    data class ViewState(
        val leadingIconState: LeadingIconState = LeadingIconState.SEARCH,
        val privacyShield: PrivacyShield = PrivacyShield.UNKNOWN,
        val browserState: BrowserState = Browser(),
        val loadingState: LoadingViewState = LoadingViewState(),
        val findInPageState: FindInPageViewState = FindInPageViewState(),
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

    enum class LeadingIconState {
        SEARCH,
        PRIVACY_SHIELD,
        DAX,
        GLOBE,
    }

    sealed class BrowserState {
        data class Browser(val url: String? = "") : BrowserState()
        data object Error : BrowserState()
        data object NewTab : BrowserState()
    }

    sealed class Command {
        data class FindInPageInputChanged(val query: String) : Command()
        data object FindInPageInputDismissed : Command()
        data object CancelTrackersAnimation : Command()
    }

    private val _viewState = MutableStateFlow(ViewState())
    val viewState = _viewState.asStateFlow()
    private fun currentViewState() = _viewState.value

    private val command = Channel<Command>(1, DROP_OLDEST)
    fun commands(): Flow<Command> = command.receiveAsFlow()

    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)
        tabRepository.flowTabs.onEach { tabs ->
            _viewState.update { currentViewState().copy(tabs = tabs) }
        }.flowOn(dispatcherProvider.io()).launchIn(viewModelScope)

        logVoiceSearchAvailability()
    }

    fun onOmnibarFocusChanged(
        hasFocus: Boolean,
        query: String,
    ) {
        // focus vs unfocused mode
        if (hasFocus) {
            viewModelScope.launch {
                command.send(Command.CancelTrackersAnimation)
            }

            _viewState.update {
                currentViewState().copy(
                    hasFocus = true,
                    forceExpand = true,
                    leadingIconState = LeadingIconState.SEARCH,
                    highlightPrivacyShield = HighlightableButton.Gone,
                    showClearButton = query.isNotBlank(),
                    showVoiceSearch = shouldShowVoiceSearch(
                        hasFocus = true,
                        query = viewState.value.omnibarText,
                        hasQueryChanged = false,
                        urlLoaded = viewState.value.loadingState.url,
                    ),
                )
            }

            // trigger autocomplete
        } else {
            _viewState.update {
                currentViewState().copy(
                    hasFocus = false,
                    forceExpand = true,
                    leadingIconState = leadingIconState(it.loadingState),
                    showTabsButton = true,
                    highlightFireButton = HighlightableButton.Visible(highlighted = false),
                    highlightMenuButton = HighlightableButton.Visible(highlighted = false),
                    showClearButton = false,
                    showVoiceSearch = shouldShowVoiceSearch(
                        hasFocus = false,
                        query = viewState.value.omnibarText,
                        hasQueryChanged = false,
                        urlLoaded = viewState.value.loadingState.url,
                    ),
                )
            }
        }
    }

    fun onPrivacyShieldChanged(privacyShield: PrivacyShield) {
        _viewState.update {
            currentViewState().copy(
                leadingIconState = PRIVACY_SHIELD,
                privacyShield = privacyShield,
            )
        }
    }

    fun onNewLoadingState(loadingState: LoadingViewState) {
        _viewState.update {
            currentViewState().copy(
                loadingState = loadingState,
                leadingIconState = leadingIconState(loadingState),
                showVoiceSearch = shouldShowVoiceSearch(
                    hasFocus = viewState.value.hasFocus,
                    query = viewState.value.omnibarText,
                    hasQueryChanged = false,
                    urlLoaded = loadingState.url,
                ),
            )
        }
    }

    private fun leadingIconState(loadingState: LoadingViewState): LeadingIconState {
        return if (shouldShowDaxIcon(loadingState.url)) {
            LeadingIconState.DAX
        } else {
            LeadingIconState.PRIVACY_SHIELD
        }
    }

    private fun shouldShowDaxIcon(currentUrl: String?): Boolean {
        val url = currentUrl ?: return false
        return duckDuckGoUrlDetector.isDuckDuckGoQueryUrl(url)
    }

    fun onBrowserStateChanged(browserState: BrowserState) {
        val hasFocus = viewState.value.hasFocus
        val leadingIcon = if (hasFocus) {
            LeadingIconState.SEARCH
        } else {
            when (browserState) {
                Error -> LeadingIconState.GLOBE
                else -> LeadingIconState.SEARCH
            }
        }
        _viewState.update {
            currentViewState().copy(
                browserState = browserState,
                leadingIconState = leadingIcon,
                showVoiceSearch = shouldShowVoiceSearch(
                    hasFocus = viewState.value.hasFocus,
                    query = viewState.value.omnibarText,
                    hasQueryChanged = false,
                    urlLoaded = viewState.value.loadingState.url,
                ),
            )
        }
    }

    fun onFindInPageChanged(findInPageState: FindInPageViewState) {
        _viewState.update { currentViewState().copy(findInPageState = findInPageState) }
    }

    fun onFindInPageTextChanged(query: String) {
        Timber.d("Omnibar: onFindInPageTextChanged query $query")
        viewModelScope.launch {
            command.send(Command.FindInPageInputChanged(query))
        }
    }

    fun onFindInPageFocusChanged(
        hasFocus: Boolean,
        query: String,
    ) {
        Timber.d("Omnibar: onFindInPageFocusChanged hasFocus $hasFocus query $query")
        if (hasFocus && query != _viewState.value.findInPageState.searchTerm) {
            val currentViewState = _viewState.value.findInPageState
            var findInPage = currentViewState.copy(visible = true, searchTerm = query)
            if (query.isEmpty()) {
                findInPage = findInPage.copy(showNumberMatches = false)
            }

            onFindInPageChanged(findInPage)
        }
    }

    fun onOmnibarTextChanged(
        omnibarState: OmnibarViewState,
        currentText: String,
    ) {
        if (omnibarState.isEditing) {
            viewModelScope.launch {
                command.send(Command.CancelTrackersAnimation)
            }
        }

        if (shouldUpdateOmnibarTextInput(omnibarState, currentText)) {
            _viewState.update {
                currentViewState().copy(
                    forceExpand = omnibarState.forceExpand,
                    shouldMoveCaretToEnd = omnibarState.shouldMoveCaretToEnd,
                    omnibarText = omnibarState.omnibarText,
                    showVoiceSearch = shouldShowVoiceSearch(
                        hasFocus = omnibarState.isEditing,
                        query = omnibarState.omnibarText,
                        hasQueryChanged = true,
                        urlLoaded = viewState.value.loadingState.url,
                    ),
                    highlightFireButton = omnibarState.fireButton,
                    highlightPrivacyShield = omnibarState.showPrivacyShield,
                    highlightMenuButton = omnibarState.showMenuButton,
                )
            }
        }
    }

    private fun shouldUpdateOmnibarTextInput(
        viewState: OmnibarViewState,
        currentText: String,
    ) =
        (!viewState.isEditing || viewState.omnibarText.isEmpty()) && currentText != viewState.omnibarText

    fun onClearTextButtonPressed() {
        _viewState.update {
            currentViewState().copy(
                omnibarText = "",
            )
        }
    }

    fun onOmnibarInputTextChanged(query: String) {
        Timber.d("Omnibar: input changed $query url ${_viewState.value.loadingState.url}")
    }

    private fun logVoiceSearchAvailability() {
        // if (voiceSearchAvailability.isVoiceSearchSupported) voiceSearchPixelLogger.log()
    }

    private fun shouldShowVoiceSearch(
        hasFocus: Boolean = false,
        query: String = "",
        hasQueryChanged: Boolean = false,
        urlLoaded: String = "",
    ): Boolean {
        return voiceSearchAvailability.shouldShowVoiceSearch(
            hasFocus = hasFocus,
            query = query,
            hasQueryChanged = hasQueryChanged,
            urlLoaded = urlLoaded,
        )
    }
}
