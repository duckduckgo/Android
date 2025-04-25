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

import android.view.MotionEvent.ACTION_UP
import android.webkit.URLUtil
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.browser.DuckDuckGoUrlDetector
import com.duckduckgo.app.browser.defaultbrowsing.prompts.DefaultBrowserPromptsExperiment
import com.duckduckgo.app.browser.omnibar.Omnibar.ViewMode
import com.duckduckgo.app.browser.omnibar.Omnibar.ViewMode.Browser
import com.duckduckgo.app.browser.omnibar.Omnibar.ViewMode.CustomTab
import com.duckduckgo.app.browser.omnibar.Omnibar.ViewMode.Error
import com.duckduckgo.app.browser.omnibar.Omnibar.ViewMode.MaliciousSiteWarning
import com.duckduckgo.app.browser.omnibar.Omnibar.ViewMode.NewTab
import com.duckduckgo.app.browser.omnibar.Omnibar.ViewMode.SSLWarning
import com.duckduckgo.app.browser.omnibar.OmnibarLayout.Decoration
import com.duckduckgo.app.browser.omnibar.OmnibarLayout.Decoration.ChangeCustomTabTitle
import com.duckduckgo.app.browser.omnibar.OmnibarLayout.Decoration.LaunchCookiesAnimation
import com.duckduckgo.app.browser.omnibar.OmnibarLayout.Decoration.LaunchTrackersAnimation
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
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.app.trackerdetection.model.Entity
import com.duckduckgo.browser.api.UserBrowserProperties
import com.duckduckgo.common.ui.experiments.visual.AppPersonalityFeature
import com.duckduckgo.common.ui.experiments.visual.store.VisualDesignExperimentDataStore
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckplayer.api.DuckPlayer
import com.duckduckgo.privacy.dashboard.impl.pixels.PrivacyDashboardPixels
import com.duckduckgo.voice.api.VoiceSearchAvailability
import com.duckduckgo.voice.api.VoiceSearchAvailabilityPixelLogger
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

@ContributesViewModel(FragmentScope::class)
class OmnibarLayoutViewModel @Inject constructor(
    private val tabRepository: TabRepository,
    private val voiceSearchAvailability: VoiceSearchAvailability,
    private val voiceSearchPixelLogger: VoiceSearchAvailabilityPixelLogger,
    private val duckDuckGoUrlDetector: DuckDuckGoUrlDetector,
    private val duckPlayer: DuckPlayer,
    private val pixel: Pixel,
    private val duckChat: DuckChat,
    private val userBrowserProperties: UserBrowserProperties,
    private val dispatcherProvider: DispatcherProvider,
    private val defaultBrowserPromptsExperiment: DefaultBrowserPromptsExperiment,
    visualDesignExperimentDataStore: VisualDesignExperimentDataStore,
    private val appPersonalityFeature: AppPersonalityFeature,
) : ViewModel() {

    private val _viewState = MutableStateFlow(ViewState())
    val viewState = combine(
        _viewState,
        tabRepository.flowTabs,
        defaultBrowserPromptsExperiment.highlightPopupMenu,
        visualDesignExperimentDataStore.navigationBarState,
    ) { state, tabs, highlightOverflowMenu, navigationBarState ->
        state.copy(
            shouldUpdateTabsCount = tabs.size != state.tabCount && tabs.isNotEmpty(),
            tabCount = tabs.size,
            hasUnreadTabs = tabs.firstOrNull { !it.viewed } != null,
            showBrowserMenuHighlight = highlightOverflowMenu,
            isNavigationBarEnabled = navigationBarState.isEnabled,
        )
    }.flowOn(dispatcherProvider.io()).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), ViewState())

    private val command = Channel<Command>(1, DROP_OLDEST)
    fun commands(): Flow<Command> = command.receiveAsFlow()

    data class ViewState(
        val viewMode: ViewMode = Browser(null),
        val leadingIconState: LeadingIconState = LeadingIconState.SEARCH,
        val privacyShield: PrivacyShield = PrivacyShield.UNKNOWN,
        val hasFocus: Boolean = false,
        val query: String = "",
        val omnibarText: String = "",
        val url: String = "",
        val expanded: Boolean = false,
        val expandedAnimated: Boolean = false,
        val updateOmnibarText: Boolean = false,
        val tabCount: Int = 0,
        val hasUnreadTabs: Boolean = false,
        val shouldUpdateTabsCount: Boolean = false,
        val showVoiceSearch: Boolean = false,
        val showClearButton: Boolean = false,
        val showTabsMenu: Boolean = true,
        val showFireIcon: Boolean = true,
        val showBrowserMenu: Boolean = true,
        val showChatMenu: Boolean = true,
        val showBrowserMenuHighlight: Boolean = false,
        val scrollingEnabled: Boolean = true,
        val isLoading: Boolean = false,
        val loadingProgress: Int = 0,
        val highlightPrivacyShield: HighlightableButton = HighlightableButton.Visible(enabled = false),
        val highlightFireButton: HighlightableButton = HighlightableButton.Visible(),
        val isNavigationBarEnabled: Boolean = false,
        val experimentalIconsEnabled: Boolean = false,
        val trackersBlocked: Int = 0,
        val previouslyTrackersBlocked: Int = 0,
    ) {
        fun shouldUpdateOmnibarText(): Boolean {
            return this.viewMode is Browser || this.viewMode is MaliciousSiteWarning
        }
    }

    sealed class Command {
        data object CancelAnimations : Command()
        data class StartTrackersAnimation(val entities: List<Entity>?) : Command()
        data class StartCookiesAnimation(val isCosmetic: Boolean) : Command()
        data object StartExperimentVariant1Animation : Command()
        data class StartExperimentVariant2OrVariant3Animation(val entities: List<Entity>?) : Command()
        data object MoveCaretToFront : Command()
    }

    enum class LeadingIconState {
        SEARCH,
        PRIVACY_SHIELD,
        DAX,
        DUCK_PLAYER,
        GLOBE,
    }

    init {
        logVoiceSearchAvailability()
    }

    fun onOmnibarFocusChanged(
        hasFocus: Boolean,
        query: String,
    ) {
        Timber.d("Omnibar: onOmnibarFocusChanged")
        val showClearButton = hasFocus && query.isNotBlank()
        val showControls = query.isBlank()

        if (hasFocus) {
            viewModelScope.launch {
                command.send(Command.CancelAnimations)
            }

            _viewState.update {
                it.copy(
                    hasFocus = true,
                    expanded = true,
                    leadingIconState = SEARCH,
                    highlightPrivacyShield = HighlightableButton.Gone,
                    showClearButton = showClearButton,
                    showTabsMenu = showControls,
                    showFireIcon = showControls,
                    showBrowserMenu = showControls,
                    showChatMenu = !showControls,
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
                val shouldUpdateOmnibarText = it.shouldUpdateOmnibarText()
                Timber.d("Omnibar: lost focus in Browser or MaliciousSiteWarning mode $shouldUpdateOmnibarText")
                val omnibarText = if (shouldUpdateOmnibarText) {
                    if (duckDuckGoUrlDetector.isDuckDuckGoQueryUrl(it.url)) {
                        Timber.d("Omnibar: is DDG url, showing query ${it.query}")
                        it.query
                    } else {
                        Timber.d("Omnibar: is url, showing URL ${it.url}")
                        it.url
                    }
                } else {
                    Timber.d("Omnibar: not browser or MaliciousSiteWarning mode, not changing omnibar text")
                    it.omnibarText
                }
                it.copy(
                    hasFocus = false,
                    expanded = false,
                    leadingIconState = getLeadingIconState(false, it.url),
                    highlightFireButton = HighlightableButton.Visible(highlighted = false),
                    showClearButton = false,
                    showTabsMenu = true,
                    showFireIcon = true,
                    showBrowserMenu = true,
                    showChatMenu = false,
                    showVoiceSearch = shouldShowVoiceSearch(
                        hasFocus = false,
                        query = _viewState.value.omnibarText,
                        hasQueryChanged = false,
                        urlLoaded = _viewState.value.url,
                    ),
                    updateOmnibarText = shouldUpdateOmnibarText,
                    omnibarText = omnibarText,
                )
            }

            viewModelScope.launch {
                command.send(Command.MoveCaretToFront)
            }
        }
    }

    fun onOmnibarScrollingEnabledChanged(isEnabled: Boolean) {
        _viewState.update {
            it.copy(
                scrollingEnabled = isEnabled,
                expanded = !isEnabled,
                expandedAnimated = !isEnabled,
            )
        }
    }

    private fun logVoiceSearchAvailability() {
        if (voiceSearchAvailability.isVoiceSearchSupported) voiceSearchPixelLogger.log()
    }

    private fun getLeadingIconState(
        hasFocus: Boolean,
        url: String,
    ): LeadingIconState {
        return when (_viewState.value.viewMode) {
            Error, SSLWarning, MaliciousSiteWarning -> GLOBE
            NewTab -> SEARCH
            else -> {
                if (hasFocus) {
                    SEARCH
                } else if (shouldShowDaxIcon(url)) {
                    DAX
                } else if (shouldShowDuckPlayerIcon(url)) {
                    DUCK_PLAYER
                } else {
                    if (url.isEmpty()) {
                        SEARCH
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
        val currentViewMode = _viewState.value.viewMode
        Timber.d("Omnibar: onViewModeChanged $viewMode")
        if (currentViewMode is CustomTab) {
            Timber.d("Omnibar: custom tab mode enabled, sending updates there")
        } else {
            when (viewMode) {
                is CustomTab -> {
                    _viewState.update {
                        it.copy(
                            viewMode = viewMode,
                            showClearButton = false,
                            showVoiceSearch = false,
                            showBrowserMenu = true,
                            showTabsMenu = false,
                            showFireIcon = false,
                            showChatMenu = false,
                        )
                    }
                }

                else -> {
                    val scrollingEnabled = viewMode != NewTab
                    val hasFocus = _viewState.value.hasFocus
                    val leadingIcon = if (hasFocus) {
                        LeadingIconState.SEARCH
                    } else {
                        when (viewMode) {
                            Error, SSLWarning, MaliciousSiteWarning -> GLOBE
                            NewTab -> SEARCH
                            else -> SEARCH
                        }
                    }

                    _viewState.update {
                        it.copy(
                            viewMode = viewMode,
                            leadingIconState = leadingIcon,
                            scrollingEnabled = scrollingEnabled,
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
    }

    fun onPrivacyShieldChanged(privacyShield: PrivacyShield) {
        Timber.d("Omnibar: onPrivacyShieldChanged $privacyShield")
        _viewState.update {
            it.copy(
                privacyShield = privacyShield,
            )
        }
    }

    fun onClearTextButtonPressed() {
        Timber.d("Omnibar: onClearTextButtonPressed")
        firePixelBasedOnCurrentUrl(
            AppPixelName.ADDRESS_BAR_NEW_TAB_PAGE_ENTRY_CLEARED,
            AppPixelName.ADDRESS_BAR_SERP_ENTRY_CLEARED,
            AppPixelName.ADDRESS_BAR_WEBSITE_ENTRY_CLEARED,
        )
        val showControls = true

        _viewState.update {
            it.copy(
                omnibarText = "",
                updateOmnibarText = true,
                expanded = true,
                showClearButton = false,
                showBrowserMenu = showControls,
                showTabsMenu = showControls,
                showFireIcon = showControls,
                showChatMenu = false,
            )
        }
    }

    fun onFireIconPressed(pulseAnimationPlaying: Boolean) {
        Timber.d("Omnibar: onFireIconPressed")
        if (_viewState.value.highlightFireButton.isHighlighted()) {
            _viewState.update {
                it.copy(
                    highlightFireButton = HighlightableButton.Visible(
                        enabled = true,
                        highlighted = false,
                    ),
                    scrollingEnabled = true,
                )
            }
        }

        pixel.fire(
            AppPixelName.MENU_ACTION_FIRE_PRESSED.pixelName,
            mapOf(FIRE_BUTTON_STATE to pulseAnimationPlaying.toString()),
        )
    }

    fun onPrivacyShieldButtonPressed() {
        Timber.d("Omnibar: onPrivacyShieldButtonPressed")
        if (_viewState.value.highlightPrivacyShield.isHighlighted()) {
            _viewState.update {
                it.copy(
                    highlightPrivacyShield = HighlightableButton.Visible(
                        enabled = true,
                        highlighted = false,
                    ),
                    scrollingEnabled = true,
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
        clearQuery: Boolean,
        deleteLastCharacter: Boolean,
    ) {
        val showClearButton = hasFocus && query.isNotBlank()
        val showControls = !hasFocus || query.isBlank()

        Timber.d("Omnibar: onInputStateChanged query $query hasFocus $hasFocus clearQuery $clearQuery deleteLastCharacter $deleteLastCharacter")

        _viewState.update {
            val updatedQuery = if (deleteLastCharacter) {
                Timber.d("Omnibar: deleting last character, old query ${it.query} also deleted")
                it.url
            } else if (clearQuery) {
                Timber.d("Omnibar: clearing old query ${it.query}, we keep it as reference")
                it.query
            } else {
                Timber.d("Omnibar: not clearing or deleting old query ${it.query}, updating query to $query")
                query
            }

            it.copy(
                query = updatedQuery,
                omnibarText = query,
                updateOmnibarText = false,
                hasFocus = hasFocus,
                showBrowserMenu = showControls,
                showTabsMenu = showControls,
                showFireIcon = showControls,
                showChatMenu = !showControls,
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
        // We only want to disable scrolling if one of the elements is highlighted
        Timber.d("Omnibar: onHighlightItem")
        val isScrollingDisabled = decoration.privacyShield || decoration.fireButton
        _viewState.update {
            it.copy(
                highlightPrivacyShield = HighlightableButton.Visible(
                    enabled = true,
                    highlighted = decoration.privacyShield,
                ),
                highlightFireButton = HighlightableButton.Visible(
                    enabled = true,
                    highlighted = decoration.fireButton,
                ),
                scrollingEnabled = !isScrollingDisabled,
            )
        }
    }

    fun onExternalStateChange(stateChange: StateChange) {
        when (stateChange) {
            is OmnibarStateChange -> onExternalOmnibarStateChanged(stateChange.omnibarViewState)
            is StateChange.LoadingStateChange -> onExternalLoadingStateChanged(stateChange.loadingViewState)
        }
    }

    private fun onExternalOmnibarStateChanged(omnibarViewState: OmnibarViewState) {
        Timber.d("Omnibar: onExternalOmnibarStateChanged $omnibarViewState")
        if (shouldUpdateOmnibarTextInput(omnibarViewState, _viewState.value.omnibarText)) {
            if (omnibarViewState.navigationChange) {
                _viewState.update {
                    it.copy(
                        expanded = true,
                        expandedAnimated = true,
                        omnibarText = omnibarViewState.omnibarText,
                        updateOmnibarText = true,
                    )
                }
            } else {
                _viewState.update {
                    it.copy(
                        expanded = omnibarViewState.forceExpand,
                        expandedAnimated = omnibarViewState.forceExpand,
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
        Timber.d("Omnibar: onExternalLoadingStateChanged $loadingState")
        _viewState.update {
            it.copy(
                url = loadingState.url,
                isLoading = loadingState.isLoading,
                loadingProgress = loadingState.progress,
                leadingIconState = getLeadingIconState(it.hasFocus, loadingState.url),
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
        Timber.d("Omnibar: onUserTouchedOmnibarTextInput")
        if (touchAction == ACTION_UP) {
            firePixelBasedOnCurrentUrl(
                AppPixelName.ADDRESS_BAR_NEW_TAB_PAGE_CLICKED,
                AppPixelName.ADDRESS_BAR_SERP_CLICKED,
                AppPixelName.ADDRESS_BAR_WEBSITE_CLICKED,
            )
        }
    }

    fun onBackKeyPressed() {
        Timber.d("Omnibar: onBackKeyPressed")
        firePixelBasedOnCurrentUrl(
            AppPixelName.ADDRESS_BAR_NEW_TAB_PAGE_CANCELLED,
            AppPixelName.ADDRESS_BAR_SERP_CANCELLED,
            AppPixelName.ADDRESS_BAR_WEBSITE_CANCELLED,
        )
        _viewState.update {
            it.copy(
                omnibarText = it.url,
                updateOmnibarText = true,
            )
        }
    }

    fun onEnterKeyPressed() {
        Timber.d("Omnibar: onEnterKeyPressed")
        firePixelBasedOnCurrentUrl(
            AppPixelName.KEYBOARD_GO_NEW_TAB_CLICKED,
            AppPixelName.KEYBOARD_GO_SERP_CLICKED,
            AppPixelName.KEYBOARD_GO_WEBSITE_CLICKED,
        )
    }

    fun onAnimationStarted(decoration: Decoration) {
        when (decoration) {
            is LaunchCookiesAnimation -> {
                viewModelScope.launch {
                    command.send(Command.StartCookiesAnimation(decoration.isCosmetic))
                }
            }

            is LaunchTrackersAnimation -> {
                Timber.d("Omnibar: LaunchTrackersAnimation")
                if (!decoration.entities.isNullOrEmpty()) {
                    val hasFocus = _viewState.value.hasFocus
                    if (!hasFocus) {
                        _viewState.update {
                            it.copy(
                                leadingIconState = PRIVACY_SHIELD,
                            )
                        }
                        viewModelScope.launch {
                            when {
                                appPersonalityFeature.self().isEnabled() && appPersonalityFeature.variant1().isEnabled() -> {
                                    command.send(Command.StartExperimentVariant1Animation)
                                }

                                appPersonalityFeature.self().isEnabled() && !appPersonalityFeature.variant1().isEnabled() -> {
                                    command.send(
                                        Command.StartExperimentVariant2OrVariant3Animation(decoration.entities),
                                    )
                                }

                                else -> {
                                    command.send(Command.StartTrackersAnimation(decoration.entities))
                                }
                            }
                        }
                    }
                }
            }

            else -> {
                // no-op
            }
        }
    }

    fun onStartedTransforming() {
        viewModelScope.launch {
            command.send(Command.CancelAnimations)
            _viewState.update {
                it.copy(
                    highlightPrivacyShield = HighlightableButton.Gone,
                )
            }
        }
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

    fun onVoiceSearchDisabled(url: String) {
        Timber.d("Omnibar: onVoiceSearchDisabled")
        _viewState.update {
            it.copy(
                showVoiceSearch = shouldShowVoiceSearch(
                    urlLoaded = url,
                ),
            )
        }
    }

    fun onCustomTabTitleUpdate(decoration: ChangeCustomTabTitle) {
        val customTabMode = viewState.value.viewMode
        if (customTabMode is CustomTab) {
            _viewState.update {
                it.copy(
                    viewMode = customTabMode.copy(title = decoration.title),
                )
            }
        }
    }
}
