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
import android.view.MotionEvent.ACTION_UP
import android.webkit.URLUtil
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.browser.DuckDuckGoUrlDetector
import com.duckduckgo.app.browser.omnibar.Omnibar.ViewMode
import com.duckduckgo.app.browser.omnibar.Omnibar.ViewMode.CustomTab
import com.duckduckgo.app.browser.omnibar.Omnibar.ViewMode.Error
import com.duckduckgo.app.browser.omnibar.Omnibar.ViewMode.NewTab
import com.duckduckgo.app.browser.omnibar.Omnibar.ViewMode.SSLWarning
import com.duckduckgo.app.browser.omnibar.OmnibarLayout.StateChange
import com.duckduckgo.app.browser.omnibar.OmnibarLayout.StateChange.OmnibarStateChange
import com.duckduckgo.app.browser.omnibar.OmnibarLayoutViewModel.LeadingIconState.DAX
import com.duckduckgo.app.browser.omnibar.OmnibarLayoutViewModel.LeadingIconState.DUCK_PLAYER
import com.duckduckgo.app.browser.omnibar.OmnibarLayoutViewModel.LeadingIconState.GLOBE
import com.duckduckgo.app.browser.omnibar.OmnibarLayoutViewModel.LeadingIconState.PRIVACY_SHIELD
import com.duckduckgo.app.browser.omnibar.OmnibarLayoutViewModel.LeadingIconState.SEARCH
import com.duckduckgo.app.browser.viewstate.HighlightableButton
import com.duckduckgo.app.browser.viewstate.LoadingViewState
import com.duckduckgo.app.browser.viewstate.OmnibarViewState
import com.duckduckgo.app.global.model.PrivacyShield
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter.FIRE_BUTTON_STATE
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Unique
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.browser.api.UserBrowserProperties
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.duckplayer.api.DuckPlayer
import com.duckduckgo.privacy.dashboard.impl.pixels.PrivacyDashboardPixels
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
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

@SuppressLint("NoLifecycleObserver") // we don't observe app lifecycle
@ContributesViewModel(FragmentScope::class)
class OmnibarLayoutViewModel @Inject constructor(
    private val tabRepository: TabRepository,
    private val voiceSearchAvailability: VoiceSearchAvailability,
    private val voiceSearchPixelLogger: VoiceSearchAvailabilityPixelLogger,
    private val duckDuckGoUrlDetector: DuckDuckGoUrlDetector,
    private val duckPlayer: DuckPlayer,
    private val pixel: Pixel,
    private val userBrowserProperties: UserBrowserProperties,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel(), DefaultLifecycleObserver {

    private val _viewState = MutableStateFlow(ViewState())

    val viewState = _viewState.asStateFlow().onStart {
        tabRepository.flowTabs.onEach { tabs ->
            Timber.d("Omnibar: tabs $tabs")
            _viewState.update { ViewState(tabs = tabs) }
        }
    }.flowOn(dispatcherProvider.io())

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
        val expanded: Boolean = false,
        val expandedAnimated: Boolean = false,
        val updateOmnibarText: Boolean = false,
        val shouldMoveCaretToEnd: Boolean = false,
        val tabs: List<TabEntity> = emptyList(),
        val showVoiceSearch: Boolean = false,
        val showClearButton: Boolean = false,
        val showControls: Boolean = true,
        val highlightPrivacyShield: HighlightableButton = HighlightableButton.Visible(enabled = false),
        val highlightFireButton: HighlightableButton = HighlightableButton.Visible(),
    )

    sealed class Command {
        data object CancelTrackersAnimation : Command()
    }

    enum class LeadingIconState {
        SEARCH,
        PRIVACY_SHIELD,
        DAX,
        DUCK_PLAYER,
        GLOBE,
    }

    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)

        tabRepository.flowTabs.onEach { tabs ->
            Timber.d("Omnibar: Tabs received")
            _viewState.update { ViewState(tabs = tabs) }
        }.flowOn(dispatcherProvider.io()).launchIn(viewModelScope)

        logVoiceSearchAvailability()
    }

    fun onOmnibarFocusChanged(
        hasFocus: Boolean,
        query: String,
    ) {
        Timber.d("Omnibar: onOmnibarFocusChanged")
        val showClearButton = hasFocus && query.isNotBlank()

        if (hasFocus) {
            viewModelScope.launch {
                command.send(Command.CancelTrackersAnimation)
            }

            _viewState.update {
                currentViewState().copy(
                    hasFocus = true,
                    expanded = true,
                    leadingIconState = SEARCH,
                    highlightPrivacyShield = HighlightableButton.Gone,
                    showClearButton = showClearButton,
                    showControls = query.isBlank(),
                    showVoiceSearch = shouldShowVoiceSearch(
                        hasFocus = true,
                        query = _viewState.value.omnibarText,
                        hasQueryChanged = false,
                        urlLoaded = _viewState.value.url,
                    ),
                )
            }
        } else {
            _viewState.update {
                currentViewState().copy(
                    hasFocus = false,
                    expanded = false,
                    leadingIconState = leadingIconState(it.url),
                    highlightFireButton = HighlightableButton.Visible(highlighted = false),
                    showClearButton = false,
                    showControls = true,
                    showVoiceSearch = shouldShowVoiceSearch(
                        hasFocus = false,
                        query = _viewState.value.omnibarText,
                        hasQueryChanged = false,
                        urlLoaded = _viewState.value.url,
                    ),
                )
            }
        }
    }

    private fun logVoiceSearchAvailability() {
        if (voiceSearchAvailability.isVoiceSearchSupported) voiceSearchPixelLogger.log()
    }

    private fun leadingIconState(url: String?): LeadingIconState {
        return when (_viewState.value.viewMode) {
            Error -> GLOBE
            NewTab -> SEARCH
            SSLWarning -> GLOBE
            else -> {
                if (shouldShowDaxIcon(url)) {
                    DAX
                } else if (shouldShowDuckPlayerIcon(url)) {
                    DUCK_PLAYER
                } else {
                    if (url != null) {
                        if (url.isEmpty()) {
                            SEARCH
                        } else {
                            PRIVACY_SHIELD
                        }
                    } else {
                        PRIVACY_SHIELD
                    }
                }
            }
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
                        viewMode = viewMode,
                        showClearButton = false,
                        showVoiceSearch = false,
                        showControls = false,
                    )
                }
            }

            else -> {
                val hasFocus = _viewState.value.hasFocus
                val leadingIcon = if (hasFocus) {
                    LeadingIconState.SEARCH
                } else {
                    when (viewMode) {
                        Error -> GLOBE
                        NewTab -> SEARCH
                        SSLWarning -> GLOBE
                        else -> SEARCH
                    }
                }

                _viewState.update {
                    currentViewState().copy(
                        leadingIconState = leadingIcon,
                        showVoiceSearch = shouldShowVoiceSearch(
                            hasFocus = _viewState.value.hasFocus,
                            query = _viewState.value.omnibarText,
                            hasQueryChanged = false,
                            urlLoaded = _viewState.value.url,
                        ),
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

    fun onClearTextButtonPressed() {
        firePixelBasedOnCurrentUrl(
            AppPixelName.ADDRESS_BAR_NEW_TAB_PAGE_ENTRY_CLEARED,
            AppPixelName.ADDRESS_BAR_SERP_ENTRY_CLEARED,
            AppPixelName.ADDRESS_BAR_WEBSITE_ENTRY_CLEARED,
        )

        _viewState.update {
            currentViewState().copy(
                omnibarText = "",
                updateOmnibarText = true,
                expanded = true,
                showControls = true,
                showClearButton = false,
            )
        }
    }

    fun onFireIconPressed(pulseAnimationPlaying: Boolean) {
        if (_viewState.value.highlightFireButton.isHighlighted()) {
            _viewState.update {
                currentViewState().copy(
                    highlightFireButton = HighlightableButton.Visible(
                        enabled = true,
                        highlighted = false,
                    ),
                )
            }
        }

        pixel.fire(
            AppPixelName.MENU_ACTION_FIRE_PRESSED.pixelName,
            mapOf(FIRE_BUTTON_STATE to pulseAnimationPlaying.toString()),
        )
    }

    fun onPrivacyShieldButtonPressed() {
        if (_viewState.value.highlightPrivacyShield.isHighlighted()) {
            _viewState.update {
                currentViewState().copy(
                    highlightPrivacyShield = HighlightableButton.Visible(
                        enabled = true,
                        highlighted = false,
                    ),
                )
            }

            pixel.fire(
                pixel = PrivacyDashboardPixels.PRIVACY_DASHBOARD_FIRST_TIME_OPENED,
                parameters = mapOf(
                    "daysSinceInstall" to userBrowserProperties.daysSinceInstalled().toString(),
                    "from_onboarding" to "true",
                ),
                type = Unique(),
            )
        }
    }

    fun onInputStateChanged(
        query: String,
        hasFocus: Boolean,
    ) {
        val showClearButton = hasFocus && query.isNotBlank()
        val showControls = !hasFocus || query.isBlank()

        _viewState.update {
            currentViewState().copy(
                omnibarText = query,
                updateOmnibarText = false,
                hasFocus = hasFocus,
                showControls = showControls,
                showClearButton = showClearButton,
                showVoiceSearch = shouldShowVoiceSearch(
                    hasFocus = hasFocus,
                    query = query,
                    hasQueryChanged = true,
                    urlLoaded = _viewState.value.url,
                ),
            )
        }
    }

    fun onHighlightItem(decoration: OmnibarLayout.Decoration.HighlightOmnibarItem) {
        if (decoration.privacyShield) {
            _viewState.update {
                currentViewState().copy(
                    highlightPrivacyShield = HighlightableButton.Visible(
                        enabled = true,
                        highlighted = true,
                    ),
                    highlightFireButton = HighlightableButton.Visible(
                        enabled = true,
                        highlighted = false,
                    ),
                )
            }
        }

        if (decoration.fireButton) {
            _viewState.update {
                currentViewState().copy(
                    highlightPrivacyShield = HighlightableButton.Visible(
                        enabled = true,
                        highlighted = false,
                    ),
                    highlightFireButton = HighlightableButton.Visible(
                        enabled = true,
                        highlighted = true,
                    ),
                )
            }
        }
    }

    fun onExternalStateChange(stateChange: StateChange) {
        when (stateChange) {
            is OmnibarStateChange -> onExternalOmnibarStateChanged(stateChange.omnibarViewState)
            is StateChange.LoadingStateChange -> onExternalLoadingStateChanged(stateChange.loadingViewState)
        }
    }

    private fun onExternalOmnibarStateChanged(omnibarViewState: OmnibarViewState) {
        if (shouldUpdateOmnibarTextInput(omnibarViewState, _viewState.value.omnibarText)) {
            if (omnibarViewState.navigationChange) {
                _viewState.update {
                    currentViewState().copy(
                        expanded = true,
                        expandedAnimated = true,
                        omnibarText = omnibarViewState.omnibarText,
                        updateOmnibarText = true,
                    )
                }
            } else {
                _viewState.update {
                    currentViewState().copy(
                        expanded = omnibarViewState.forceExpand,
                        expandedAnimated = omnibarViewState.forceExpand,
                        shouldMoveCaretToEnd = omnibarViewState.shouldMoveCaretToEnd,
                        omnibarText = omnibarViewState.omnibarText,
                        updateOmnibarText = true,
                        showVoiceSearch = shouldShowVoiceSearch(
                            hasFocus = omnibarViewState.isEditing,
                            query = omnibarViewState.omnibarText,
                            hasQueryChanged = true,
                            urlLoaded = _viewState.value.url,
                        ),

                    )
                }
            }
        }
    }

    private fun onExternalLoadingStateChanged(loadingState: LoadingViewState) {
        _viewState.update {
            currentViewState().copy(
                url = loadingState.url,
                leadingIconState = leadingIconState(loadingState.url),
                showVoiceSearch = shouldShowVoiceSearch(
                    hasFocus = _viewState.value.hasFocus,
                    query = _viewState.value.omnibarText,
                    hasQueryChanged = false,
                    urlLoaded = loadingState.url,
                ),
            )
        }
    }

    fun onUserTouchedOmnibarTextInput(touchAction: Int) {
        if (touchAction == ACTION_UP) {
            firePixelBasedOnCurrentUrl(
                AppPixelName.ADDRESS_BAR_NEW_TAB_PAGE_CLICKED,
                AppPixelName.ADDRESS_BAR_SERP_CLICKED,
                AppPixelName.ADDRESS_BAR_WEBSITE_CLICKED,
            )
        }
    }

    fun onBackKeyPressed() {
        firePixelBasedOnCurrentUrl(
            AppPixelName.ADDRESS_BAR_NEW_TAB_PAGE_CANCELLED,
            AppPixelName.ADDRESS_BAR_SERP_CANCELLED,
            AppPixelName.ADDRESS_BAR_WEBSITE_CANCELLED,
        )
    }

    fun onEnterKeyPressed() {
        firePixelBasedOnCurrentUrl(
            AppPixelName.KEYBOARD_GO_NEW_TAB_CLICKED,
            AppPixelName.KEYBOARD_GO_SERP_CLICKED,
            AppPixelName.KEYBOARD_GO_WEBSITE_CLICKED,
        )
    }

    private fun shouldUpdateOmnibarTextInput(
        viewState: OmnibarViewState,
        currentText: String,
    ) =
        (!viewState.isEditing || viewState.omnibarText.isEmpty()) && currentText != viewState.omnibarText

    private fun firePixelBasedOnCurrentUrl(
        emptyUrlPixel: AppPixelName,
        duckDuckGoQueryUrlPixel: AppPixelName,
        websiteUrlPixel: AppPixelName,
    ) {
        val text = _viewState.value.url
        if (text.isEmpty()) {
            pixel.fire(emptyUrlPixel)
        } else if (duckDuckGoUrlDetector.isDuckDuckGoQueryUrl(text)) {
            pixel.fire(duckDuckGoQueryUrlPixel)
        } else if (isUrl(text)) {
            pixel.fire(websiteUrlPixel)
        }
    }

    private fun isUrl(text: String): Boolean {
        return URLUtil.isNetworkUrl(text) || URLUtil.isAssetUrl(text) || URLUtil.isFileUrl(text) || URLUtil.isContentUrl(
            text,
        )
    }
}
