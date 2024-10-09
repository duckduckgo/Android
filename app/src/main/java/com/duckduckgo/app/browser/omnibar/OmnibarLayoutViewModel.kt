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
import com.duckduckgo.app.browser.omnibar.NewOmnibarViewModel.Command
import com.duckduckgo.app.browser.omnibar.Omnibar.ViewMode
import com.duckduckgo.app.browser.omnibar.Omnibar.ViewMode.Browser
import com.duckduckgo.app.browser.omnibar.Omnibar.ViewMode.CustomTab
import com.duckduckgo.app.browser.omnibar.Omnibar.ViewMode.Error
import com.duckduckgo.app.browser.omnibar.Omnibar.ViewMode.NewTab
import com.duckduckgo.app.browser.omnibar.Omnibar.ViewMode.SSLWarning
import com.duckduckgo.app.browser.omnibar.OmnibarLayoutViewModel.LeadingIconState.DAX
import com.duckduckgo.app.browser.omnibar.OmnibarLayoutViewModel.LeadingIconState.DUCK_PLAYER
import com.duckduckgo.app.browser.omnibar.OmnibarLayoutViewModel.LeadingIconState.GLOBE
import com.duckduckgo.app.browser.omnibar.OmnibarLayoutViewModel.LeadingIconState.HIDDEN
import com.duckduckgo.app.browser.omnibar.OmnibarLayoutViewModel.LeadingIconState.PRIVACY_SHIELD
import com.duckduckgo.app.browser.omnibar.OmnibarLayoutViewModel.LeadingIconState.SEARCH
import com.duckduckgo.app.global.model.PrivacyShield
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.duckplayer.api.DuckPlayer
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

@SuppressLint("NoLifecycleObserver") // we don't observe app lifecycle
@ContributesViewModel(FragmentScope::class)
class OmnibarLayoutViewModel @Inject constructor(
    private val tabRepository: TabRepository,
    private val voiceSearchAvailability: VoiceSearchAvailability,
    private val voiceSearchPixelLogger: VoiceSearchAvailabilityPixelLogger,
    private val duckDuckGoUrlDetector: DuckDuckGoUrlDetector,
    private val duckPlayer: DuckPlayer,
    private val pixel: Pixel,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel(), DefaultLifecycleObserver {

    private val _viewState = MutableStateFlow(ViewState())
    val viewState = _viewState.asStateFlow()
    private fun currentViewState() = _viewState.value

    private val command = Channel<Command>(1, DROP_OLDEST)
    fun commands(): Flow<Command> = command.receiveAsFlow()

    data class ViewState(
        val viewMode: ViewMode = ViewMode.Browser(null),
        val leadingIconState: LeadingIconState = LeadingIconState.SEARCH,
        val privacyShield: PrivacyShield = PrivacyShield.UNKNOWN,
        val hasFocus: Boolean = false,
        val omnibarText: String = "",
        val url: String = "",
        val expanded: Boolean = true,
        val tabs: List<TabEntity> = emptyList(),
        val showVoiceSearch: Boolean = false,
        val showClearButton: Boolean = false,
        val showTabsButton: Boolean = true,
        val showFireButton: Boolean = true,
    )

    enum class LeadingIconState {
        SEARCH,
        PRIVACY_SHIELD,
        DAX,
        DUCK_PLAYER,
        GLOBE,
        HIDDEN,
    }

    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)
        tabRepository.flowTabs.onEach { tabs ->
            _viewState.update { currentViewState().copy(tabs = tabs) }
        }.flowOn(dispatcherProvider.io()).launchIn(viewModelScope)

        logVoiceSearchAvailability()
    }

    fun onFocusChanged(
        hasFocus: Boolean,
        query: String,
    ) {
    }

    private fun logVoiceSearchAvailability() {
        if (voiceSearchAvailability.isVoiceSearchSupported) voiceSearchPixelLogger.log()
    }

    private fun leadingIconState(viewMode: ViewMode): LeadingIconState {
        return when (viewMode) {
            is Browser -> {
                if (shouldShowDaxIcon(viewMode.url)) {
                    DAX
                } else if (shouldShowDuckPlayerIcon(viewMode.url)) {
                    DUCK_PLAYER
                } else if (viewMode.url != null) {
                    if (viewMode.url.isEmpty()) {
                        SEARCH
                    } else {
                        PRIVACY_SHIELD
                    }
                } else {
                    PRIVACY_SHIELD
                }
            }

            Error -> GLOBE
            NewTab -> SEARCH
            SSLWarning -> GLOBE
            is CustomTab -> HIDDEN
        }
    }

    private fun shouldShowDaxIcon(currentUrl: String?): Boolean {
        val url = currentUrl ?: return false
        return duckDuckGoUrlDetector.isDuckDuckGoQueryUrl(url)
    }

    private fun shouldShowDuckPlayerIcon(currentUrl: String?): Boolean {
        val url = currentUrl ?: return false
        return duckPlayer.isDuckPlayerUri(url)
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

    fun onViewModeChanged(viewMode: ViewMode) {
        when (viewMode) {
            is CustomTab -> {
                _viewState.update {
                    currentViewState().copy(
                        viewMode = ViewMode.CustomTab(
                            viewMode.toolbarColor,
                            viewMode.domain.orEmpty(),
                        ),
                        showClearButton = false,
                        showVoiceSearch = false,
                        showTabsButton = false,
                        showFireButton = false,
                    )
                }
            }
            else -> {
                _viewState.update {
                    currentViewState().copy(
                        viewMode = viewMode,
                        leadingIconState = leadingIconState(viewMode),
                        expanded = true,
                    )
                }
            }
        }
    }

    fun onPrivacyShieldChanged(privacyShield: PrivacyShield) {
        _viewState.update {
            currentViewState().copy(
                privacyShield = privacyShield,
            )
        }
    }

    fun onOutlineEnabled(enabled: Boolean) {
        _viewState.update {
            currentViewState().copy(
                hasFocus = enabled,
            )
        }
    }
}
