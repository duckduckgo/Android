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
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.browser.AddressDisplayFormatter
import com.duckduckgo.app.browser.DuckDuckGoUrlDetector
import com.duckduckgo.app.browser.animations.AddressBarTrackersAnimationManager
import com.duckduckgo.app.browser.defaultbrowsing.prompts.AdditionalDefaultBrowserPrompts
import com.duckduckgo.app.browser.omnibar.Omnibar.ViewMode
import com.duckduckgo.app.browser.omnibar.Omnibar.ViewMode.Browser
import com.duckduckgo.app.browser.omnibar.Omnibar.ViewMode.CustomTab
import com.duckduckgo.app.browser.omnibar.Omnibar.ViewMode.Error
import com.duckduckgo.app.browser.omnibar.Omnibar.ViewMode.MaliciousSiteWarning
import com.duckduckgo.app.browser.omnibar.Omnibar.ViewMode.NewTab
import com.duckduckgo.app.browser.omnibar.Omnibar.ViewMode.SSLWarning
import com.duckduckgo.app.browser.omnibar.OmnibarLayoutViewModel.LeadingIconState.Dax
import com.duckduckgo.app.browser.omnibar.OmnibarLayoutViewModel.LeadingIconState.DuckPlayer
import com.duckduckgo.app.browser.omnibar.OmnibarLayoutViewModel.LeadingIconState.EasterEggLogo
import com.duckduckgo.app.browser.omnibar.OmnibarLayoutViewModel.LeadingIconState.Globe
import com.duckduckgo.app.browser.omnibar.OmnibarLayoutViewModel.LeadingIconState.PrivacyShield
import com.duckduckgo.app.browser.omnibar.OmnibarLayoutViewModel.LeadingIconState.Search
import com.duckduckgo.app.browser.omnibar.model.Decoration
import com.duckduckgo.app.browser.omnibar.model.Decoration.ChangeCustomTabTitle
import com.duckduckgo.app.browser.omnibar.model.Decoration.LaunchCookiesAnimation
import com.duckduckgo.app.browser.omnibar.model.Decoration.LaunchTrackersAnimation
import com.duckduckgo.app.browser.omnibar.model.StateChange
import com.duckduckgo.app.browser.omnibar.model.StateChange.OmnibarStateChange
import com.duckduckgo.app.browser.urldisplay.UrlDisplayRepository
import com.duckduckgo.app.browser.viewstate.HighlightableButton
import com.duckduckgo.app.browser.viewstate.LoadingViewState
import com.duckduckgo.app.browser.viewstate.OmnibarViewState
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.pixels.duckchat.createWasUsedBeforePixelParams
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter.FIRE_BUTTON_STATE
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Unique
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.app.trackerdetection.model.Entity
import com.duckduckgo.browser.api.UserBrowserProperties
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.extractDomain
import com.duckduckgo.common.utils.isLocalUrl
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.duckchat.api.DuckAiFeatureState
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName
import com.duckduckgo.duckplayer.api.DuckPlayer.DuckPlayerState.ENABLED
import com.duckduckgo.privacy.dashboard.impl.pixels.PrivacyDashboardPixels
import com.duckduckgo.serp.logos.api.SerpEasterEggLogosToggles
import com.duckduckgo.serp.logos.api.SerpLogo
import com.duckduckgo.voice.api.VoiceSearchAvailability
import com.duckduckgo.voice.api.VoiceSearchAvailabilityPixelLogger
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.logcat
import javax.inject.Inject
import com.duckduckgo.app.global.model.PrivacyShield as PrivacyShieldState

@ContributesViewModel(FragmentScope::class)
class OmnibarLayoutViewModel @Inject constructor(
    private val tabRepository: TabRepository,
    private val voiceSearchAvailability: VoiceSearchAvailability,
    private val voiceSearchPixelLogger: VoiceSearchAvailabilityPixelLogger,
    private val duckDuckGoUrlDetector: DuckDuckGoUrlDetector,
    private val duckPlayer: com.duckduckgo.duckplayer.api.DuckPlayer,
    private val pixel: Pixel,
    private val userBrowserProperties: UserBrowserProperties,
    private val dispatcherProvider: DispatcherProvider,
    private val additionalDefaultBrowserPrompts: AdditionalDefaultBrowserPrompts,
    private val duckChat: DuckChat,
    private val duckAiFeatureState: DuckAiFeatureState,
    private val addressDisplayFormatter: AddressDisplayFormatter,
    private val settingsDataStore: SettingsDataStore,
    private val urlDisplayRepository: UrlDisplayRepository,
    private val serpEasterEggLogosToggles: SerpEasterEggLogosToggles,
    private val androidBrowserToggles: AndroidBrowserConfigFeature,
    private val addressBarTrackersAnimationManager: AddressBarTrackersAnimationManager,
    private val standardizedLeadingIconToggle: StandardizedLeadingIconFeatureToggle,
) : ViewModel() {

    private val isSplitOmnibarEnabled = settingsDataStore.omnibarType == OmnibarType.SPLIT

    private var handleAboutBlankEnabled: Boolean = false
    private var isSetFavouriteEasterEggLogoFeatureEnabled: Boolean = false

    private val _viewState = MutableStateFlow(
        ViewState(
            showChatMenu = duckAiFeatureState.showOmnibarShortcutInAllStates.value,
            showFireIcon = !isSplitOmnibarEnabled,
            showTabsMenu = !isSplitOmnibarEnabled,
            showBrowserMenu = !isSplitOmnibarEnabled,
        ),
    )

    val viewState = combine(
        _viewState,
        tabRepository.flowTabs,
        additionalDefaultBrowserPrompts.highlightPopupMenu,
        flow { emit(addressBarTrackersAnimationManager.isFeatureEnabled()) },
    ) { state, tabs, highlightOverflowMenu, isAddressBarTrackersAnimationEnabled ->
        state.copy(
            shouldUpdateTabsCount = tabs.size != state.tabCount && tabs.isNotEmpty(),
            tabCount = tabs.size,
            hasUnreadTabs = tabs.firstOrNull { !it.viewed } != null,
            showBrowserMenuHighlight = highlightOverflowMenu,
            viewMode = getViewMode(state),
            isAddressBarTrackersAnimationEnabled = isAddressBarTrackersAnimationEnabled,
        )
    }.flowOn(dispatcherProvider.io()).stateIn(viewModelScope, SharingStarted.Eagerly, _viewState.value)

    private fun getViewMode(state: ViewState): ViewMode {
        return if (state.viewMode is CustomTab) {
            val domain = if (state.url.isBlank()) state.omnibarText else state.url.extractDomain()
            if (domain != state.viewMode.domain) {
                val isDuckPlayerUrl = duckPlayer.getDuckPlayerState() == ENABLED && duckPlayer.isDuckPlayerUri(state.url)
                state.viewMode.copy(
                    domain = domain,
                    showDuckPlayerIcon = isDuckPlayerUrl,
                )
            } else {
                state.viewMode
            }
        } else {
            state.viewMode
        }
    }

    private val showDuckAiButton = combine(
        _viewState,
        duckAiFeatureState.showOmnibarShortcutOnNtpAndOnFocus,
        duckAiFeatureState.showOmnibarShortcutInAllStates,
    ) { viewState, showOnNtpAndOnFocus, showInAllStates ->
        when {
            viewState.viewMode is CustomTab -> {
                false
            }

            viewState.viewMode is ViewMode.DuckAI -> {
                false
            }

            showInAllStates -> {
                true
            }

            else -> showOnNtpAndOnFocus && (viewState.viewMode is NewTab || viewState.hasFocus && viewState.omnibarText.isNotBlank())
        }
    }.distinctUntilChanged()

    private val isFullUrlEnabled = urlDisplayRepository.isFullUrlEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = true,
        )

    private val command = Channel<Command>(1, DROP_OLDEST)
    fun commands(): Flow<Command> = command.receiveAsFlow()

    data class ViewState(
        val viewMode: ViewMode = Browser(null),
        val leadingIconState: LeadingIconState = Search,
        val previousLeadingIconState: LeadingIconState? = null,
        val privacyShield: PrivacyShieldState = PrivacyShieldState.UNKNOWN,
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
        val trackersBlocked: Int = 0,
        val previouslyTrackersBlocked: Int = 0,
        val showShadows: Boolean = false,
        val showTextInputClickCatcher: Boolean = false,
        val showFindInPage: Boolean = false,
        val showDuckAIHeader: Boolean = false,
        val showDuckAISidebar: Boolean = false,
        val isAddressBarTrackersAnimationEnabled: Boolean = false,
    ) {
        fun shouldUpdateOmnibarText(
            isFullUrlEnabled: Boolean,
            isAboutBlankHandleEnabled: Boolean,
        ): Boolean {
            val updateOmnibarText =
                this.viewMode is Browser || this.viewMode is MaliciousSiteWarning || (!isFullUrlEnabled && omnibarText.isNotEmpty())
            return if (isAboutBlankHandleEnabled) {
                updateOmnibarText && url.isNotEmpty()
            } else {
                updateOmnibarText
            }
        }
    }

    sealed class Command {
        data object CancelAnimations : Command()
        data class StartTrackersAnimation(
            val entities: List<Entity>?,
            val isCustomTab: Boolean,
            val isAddressBarTrackersAnimationEnabled: Boolean,
        ) : Command()

        data class StartCookiesAnimation(val isCosmetic: Boolean) : Command()
        data object MoveCaretToFront : Command()
        data class LaunchInputScreen(val query: String) : Command()
        data class EasterEggLogoClicked(val url: String) : Command()
        data object FocusInputField : Command()
        data object CancelEasterEggLogoAnimation : Command()
    }

    sealed class LeadingIconState {
        object Search : LeadingIconState()
        object PrivacyShield : LeadingIconState()
        object Dax : LeadingIconState()
        object DuckPlayer : LeadingIconState()
        object Globe : LeadingIconState()
        data class EasterEggLogo(
            val logoUrl: String,
            val serpUrl: String,
            val isFavourite: Boolean,
        ) : LeadingIconState()
    }

    init {
        logVoiceSearchAvailability()

        viewModelScope.launch(dispatcherProvider.io()) {
            handleAboutBlankEnabled = androidBrowserToggles.handleAboutBlank().isEnabled()
        }

        duckAiFeatureState.showInputScreen.onEach { inputScreenEnabled ->
            _viewState.update {
                it.copy(
                    showTextInputClickCatcher = inputScreenEnabled,
                )
            }
        }.launchIn(viewModelScope)

        showDuckAiButton.onEach { showDuckAiButton ->
            _viewState.update {
                it.copy(showChatMenu = showDuckAiButton)
            }
        }.launchIn(viewModelScope)

        viewState.map {
            NewTabPixelParams(
                isNtp = it.viewMode == NewTab,
                isFocused = it.hasFocus,
                isTabSwitcherButtonVisible = it.showTabsMenu,
                isFireButtonVisible = it.showFireIcon,
                isBrowserMenuButtonVisible = it.showBrowserMenu,
            )
        }.distinctUntilChanged()
            .filter { it.isNtp && it.isFocused }
            .onEach {
                val params = mapOf(
                    Pixel.PixelParameter.IS_TAB_SWITCHER_BUTTON_SHOWN to it.isTabSwitcherButtonVisible.toString(),
                    Pixel.PixelParameter.IS_FIRE_BUTTON_SHOWN to it.isFireButtonVisible.toString(),
                    Pixel.PixelParameter.IS_BROWSER_MENU_BUTTON_SHOWN to it.isBrowserMenuButtonVisible.toString(),
                )
                pixel.fire(pixel = AppPixelName.ADDRESS_BAR_NTP_FOCUSED, parameters = params)
            }.launchIn(viewModelScope)

        serpEasterEggLogosToggles.setFavourite().enabled().onEach { isSetFavouriteEasterEggLogoFeatureEnabled ->
            this.isSetFavouriteEasterEggLogoFeatureEnabled = isSetFavouriteEasterEggLogoFeatureEnabled
        }.flowOn(dispatcherProvider.io())
            .launchIn(viewModelScope)
    }

    fun onFindInPageRequested() {
        _viewState.update {
            it.copy(showFindInPage = true)
        }
    }

    fun onFindInPageDismissed() {
        _viewState.update {
            it.copy(showFindInPage = false)
        }
    }

    fun onOmnibarFocusChanged(
        hasFocus: Boolean,
        inputFieldText: String,
    ) {
        logcat { "Omnibar: onOmnibarFocusChanged hasFocus $hasFocus" }
        val showClearButton = hasFocus && inputFieldText.isNotBlank()
        val showControls = inputFieldText.isBlank() && !isSplitOmnibarEnabled

        if (hasFocus) {
            viewModelScope.launch {
                command.send(Command.CancelAnimations)
            }

            _viewState.update {
                val shouldUpdateOmnibarText = !isFullUrlEnabled.value &&
                    !it.omnibarText.isEmpty() &&
                    !duckDuckGoUrlDetector.isDuckDuckGoQueryUrl(it.url) || it.viewMode == ViewMode.DuckAI
                val omnibarText = if (shouldUpdateOmnibarText) {
                    if (it.viewMode == ViewMode.DuckAI) {
                        ""
                    } else {
                        it.url
                    }
                } else {
                    it.omnibarText
                }

                it.copy(
                    hasFocus = true,
                    expanded = true,
                    leadingIconState = Search,
                    previousLeadingIconState = it.leadingIconState,
                    highlightPrivacyShield = HighlightableButton.Gone,
                    showClearButton = showClearButton,
                    showTabsMenu = showControls,
                    showFireIcon = showControls,
                    showBrowserMenu = showControls,
                    showVoiceSearch = shouldShowVoiceSearch(
                        viewMode = _viewState.value.viewMode,
                        hasFocus = true,
                        query = _viewState.value.omnibarText,
                        hasQueryChanged = false,
                        urlLoaded = _viewState.value.url,
                    ),
                    updateOmnibarText = shouldUpdateOmnibarText,
                    omnibarText = omnibarText,
                    showDuckAIHeader = shouldShowDuckAiHeader(_viewState.value.viewMode, true),
                    showDuckAISidebar = shouldShowDuckAiHeader(_viewState.value.viewMode, true),
                )
            }
        } else {
            _viewState.update {
                val shouldUpdateOmnibarText = it.shouldUpdateOmnibarText(isFullUrlEnabled.value, handleAboutBlankEnabled)
                logcat { "Omnibar: lost focus in Browser or MaliciousSiteWarning mode $shouldUpdateOmnibarText" }
                val omnibarText = if (shouldUpdateOmnibarText) {
                    if (duckDuckGoUrlDetector.isDuckDuckGoQueryUrl(it.url)) {
                        logcat { "Omnibar: is DDG url, showing query ${it.query}" }
                        it.query
                    } else {
                        logcat { "Omnibar: is url, showing URL ${it.url}" }
                        if (isFullUrlEnabled.value) {
                            it.url
                        } else {
                            addressDisplayFormatter.getShortUrl(it.url)
                        }
                    }
                } else {
                    logcat { "Omnibar: not browser or MaliciousSiteWarning mode, not changing omnibar text" }
                    it.omnibarText
                }

                val currentLogoUrl = if (isSetFavouriteEasterEggLogoFeatureEnabled) {
                    getCurrentSerpLogoUrl(
                        currentUrl = it.url,
                        leadingIconState = it.previousLeadingIconState,
                    )
                } else {
                    when (val previousState = it.previousLeadingIconState) {
                        is EasterEggLogo -> previousState.logoUrl
                        else -> null
                    }
                }

                val isFavouriteEasterEggLogo = it.previousLeadingIconState is EasterEggLogo && it.previousLeadingIconState.isFavourite

                it.copy(
                    hasFocus = false,
                    expanded = false,
                    leadingIconState = getLeadingIconState(
                        viewMode = _viewState.value.viewMode,
                        hasFocus = false,
                        url = it.url,
                        serpLogoUrl = currentLogoUrl,
                        isFavouriteSerpLogo = isFavouriteEasterEggLogo,
                    ),
                    previousLeadingIconState = null,
                    highlightFireButton = HighlightableButton.Visible(highlighted = false),
                    showClearButton = false,
                    showTabsMenu = !isSplitOmnibarEnabled,
                    showFireIcon = !isSplitOmnibarEnabled,
                    showBrowserMenu = !isSplitOmnibarEnabled,
                    showVoiceSearch = shouldShowVoiceSearch(
                        viewMode = _viewState.value.viewMode,
                        hasFocus = false,
                        query = _viewState.value.omnibarText,
                        hasQueryChanged = false,
                        urlLoaded = _viewState.value.url,
                    ),
                    updateOmnibarText = shouldUpdateOmnibarText,
                    omnibarText = omnibarText,
                    showDuckAIHeader = shouldShowDuckAiHeader(_viewState.value.viewMode, false),
                    showDuckAISidebar = shouldShowDuckAiHeader(_viewState.value.viewMode, false),
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
        viewMode: ViewMode,
        hasFocus: Boolean,
        url: String,
        serpLogoUrl: String?,
        isFavouriteSerpLogo: Boolean,
    ): LeadingIconState {
        return when (viewMode) {
            Error, SSLWarning, MaliciousSiteWarning -> Globe
            NewTab -> Search
            else -> {
                when {
                    hasFocus -> Search
                    serpLogoUrl != null -> EasterEggLogo(logoUrl = serpLogoUrl, serpUrl = url, isFavourite = isFavouriteSerpLogo)
                    shouldShowDaxIcon(url) -> Dax
                    shouldShowDuckPlayerIcon(url) -> DuckPlayer
                    // Show globe icon for localhost and private network addresses
                    standardizedLeadingIconToggle.self().isEnabled() && isLocalUrl(url) -> Globe
                    url.isEmpty() -> Search
                    else -> PrivacyShield
                }
            }
        }
    }

    private fun isLocalUrl(url: String): Boolean {
        return try {
            url.toUri().isLocalUrl
        } catch (e: Exception) {
            false
        }
    }

    private fun getCurrentSerpLogoUrl(
        currentUrl: String,
        leadingIconState: LeadingIconState?,
    ): String? {
        val isDuckDuckGoQueryUrl = duckDuckGoUrlDetector.isDuckDuckGoQueryUrl(currentUrl)
        return when {
            leadingIconState is EasterEggLogo && isDuckDuckGoQueryUrl -> leadingIconState.logoUrl
            else -> null
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
        viewMode: ViewMode,
        hasFocus: Boolean = false,
        query: String = "",
        hasQueryChanged: Boolean = false,
        urlLoaded: String = "",
    ): Boolean {
        return if (viewMode == ViewMode.DuckAI) {
            false
        } else {
            voiceSearchAvailability.shouldShowVoiceSearch(
                hasFocus = hasFocus,
                query = query,
                hasQueryChanged = hasQueryChanged,
                urlLoaded = urlLoaded,
            )
        }
    }

    private fun shouldShowDuckAiHeader(
        viewMode: ViewMode,
        hasFocus: Boolean,
    ): Boolean {
        logcat { "Omnibar: shouldShowDuckAiHeader $viewMode, focus: $hasFocus" }
        return if (viewMode == ViewMode.DuckAI) {
            !hasFocus
        } else {
            false
        }
    }

    fun onViewModeChanged(viewMode: ViewMode) {
        val currentViewMode = _viewState.value.viewMode
        val hasFocus = _viewState.value.hasFocus
        logcat { "Omnibar: onViewModeChanged $viewMode" }
        if (currentViewMode is CustomTab) {
            logcat { "Omnibar: custom tab mode enabled, sending updates there" }
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
                            showShadows = true,
                        )
                    }
                }

                is ViewMode.DuckAI -> {
                    _viewState.update {
                        it.copy(
                            viewMode = viewMode,
                            showClearButton = false,
                            showVoiceSearch = false,
                            showShadows = true,
                            scrollingEnabled = false,
                            hasFocus = false,
                            isLoading = false,
                            omnibarText = "",
                            updateOmnibarText = true,
                            showDuckAIHeader = shouldShowDuckAiHeader(viewMode, hasFocus),
                            showDuckAISidebar = shouldShowDuckAiHeader(viewMode, hasFocus),
                        )
                    }
                }

                else -> {
                    val scrollingEnabled = viewMode != NewTab
                    val hasFocus = _viewState.value.hasFocus
                    val currentLogoUrl = if (isSetFavouriteEasterEggLogoFeatureEnabled) {
                        getCurrentSerpLogoUrl(
                            currentUrl = _viewState.value.url,
                            leadingIconState = _viewState.value.leadingIconState,
                        )
                    } else {
                        when (val leadingIconState = _viewState.value.leadingIconState) {
                            is EasterEggLogo -> leadingIconState.logoUrl
                            else -> null
                        }
                    }
                    _viewState.update {
                        it.copy(
                            viewMode = viewMode,
                            leadingIconState = getLeadingIconState(
                                viewMode = viewMode,
                                hasFocus = hasFocus,
                                url = _viewState.value.url,
                                serpLogoUrl = currentLogoUrl,
                                isFavouriteSerpLogo = it.leadingIconState is EasterEggLogo && it.leadingIconState.isFavourite,
                            ),
                            scrollingEnabled = scrollingEnabled,
                            showVoiceSearch = shouldShowVoiceSearch(
                                viewMode = _viewState.value.viewMode,
                                hasFocus = _viewState.value.hasFocus,
                                query = _viewState.value.omnibarText,
                                hasQueryChanged = false,
                                urlLoaded = _viewState.value.url,
                            ),
                            showShadows = false,
                            showDuckAIHeader = shouldShowDuckAiHeader(viewMode, hasFocus),
                            showDuckAISidebar = shouldShowDuckAiHeader(viewMode, hasFocus),
                        )
                    }
                }
            }
        }
    }

    fun onPrivacyShieldChanged(privacyShieldState: PrivacyShieldState) {
        logcat { "Omnibar: onPrivacyShieldChanged $privacyShieldState" }
        _viewState.update {
            it.copy(
                privacyShield = privacyShieldState,
            )
        }
    }

    fun onClearTextButtonPressed() {
        logcat { "Omnibar: onClearTextButtonPressed" }
        firePixelBasedOnCurrentUrl(
            AppPixelName.ADDRESS_BAR_NEW_TAB_PAGE_ENTRY_CLEARED,
            AppPixelName.ADDRESS_BAR_SERP_ENTRY_CLEARED,
            AppPixelName.ADDRESS_BAR_WEBSITE_ENTRY_CLEARED,
        )

        _viewState.update {
            it.copy(
                omnibarText = "",
                updateOmnibarText = true,
                expanded = true,
                showClearButton = false,
                showBrowserMenu = !isSplitOmnibarEnabled,
                showTabsMenu = !isSplitOmnibarEnabled,
                showFireIcon = !isSplitOmnibarEnabled,
            )
        }
    }

    fun onFireIconPressed(pulseAnimationPlaying: Boolean) {
        logcat { "Omnibar: onFireIconPressed" }
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
        logcat { "Omnibar: onPrivacyShieldButtonPressed" }
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
        val showControls = (!hasFocus || query.isBlank()) && !isSplitOmnibarEnabled

        logcat { "Omnibar: onInputStateChanged query $query hasFocus $hasFocus clearQuery $clearQuery deleteLastCharacter $deleteLastCharacter" }

        _viewState.update {
            val updatedQuery = if (deleteLastCharacter) {
                logcat { "Omnibar: deleting last character, old query ${it.query} also deleted" }
                if (isFullUrlEnabled.value) {
                    it.url
                } else {
                    addressDisplayFormatter.getShortUrl(it.url)
                }
            } else if (clearQuery) {
                logcat { "Omnibar: clearing old query ${it.query}, we keep it as reference" }
                it.query
            } else {
                logcat { "Omnibar: not clearing or deleting old query ${it.query}, updating query to $query" }
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
                showClearButton = showClearButton,
                showVoiceSearch = shouldShowVoiceSearch(
                    viewMode = _viewState.value.viewMode,
                    hasFocus = hasFocus,
                    query = query,
                    hasQueryChanged = query != updatedQuery,
                    urlLoaded = _viewState.value.url,
                ),
            )
        }
    }

    fun onHighlightItem(decoration: Decoration.HighlightOmnibarItem) {
        // We only want to disable scrolling if one of the elements is highlighted
        logcat { "Omnibar: onHighlightItem" }
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
            is OmnibarStateChange -> onExternalOmnibarStateChanged(stateChange.omnibarViewState, stateChange.forceRender)
            is StateChange.LoadingStateChange -> onExternalLoadingStateChanged(stateChange.loadingViewState)
        }
    }

    private fun onExternalOmnibarStateChanged(
        omnibarViewState: OmnibarViewState,
        forceRender: Boolean,
    ) {
        logcat { "Omnibar: onExternalOmnibarStateChanged $omnibarViewState forceRender $forceRender" }
        val state = if (shouldUpdateOmnibarTextInput(omnibarViewState, _viewState.value.omnibarText) || forceRender) {
            if (forceRender &&
                !duckDuckGoUrlDetector.isDuckDuckGoQueryUrl(omnibarViewState.queryOrFullUrl) &&
                isNotAboutBlank(omnibarViewState)
            ) {
                val url = if (isFullUrlEnabled.value) {
                    omnibarViewState.queryOrFullUrl
                } else {
                    addressDisplayFormatter.getShortUrl(omnibarViewState.queryOrFullUrl)
                }
                _viewState.value.copy(
                    omnibarText = url,
                    updateOmnibarText = true,
                )
            } else {
                val omnibarText = if (_viewState.value.viewMode == ViewMode.DuckAI) {
                    ""
                } else {
                    omnibarViewState.omnibarText
                }
                _viewState.value.copy(
                    omnibarText = omnibarText,
                )
            }
        } else {
            _viewState.value
        }

        if (omnibarViewState.navigationChange) {
            _viewState.update {
                state.copy(
                    expanded = true,
                    expandedAnimated = true,
                    updateOmnibarText = true,
                )
            }
        } else {
            _viewState.update {
                state.copy(
                    expanded = omnibarViewState.forceExpand,
                    expandedAnimated = omnibarViewState.forceExpand,
                    updateOmnibarText = true,
                    showVoiceSearch = shouldShowVoiceSearch(
                        viewMode = _viewState.value.viewMode,
                        hasFocus = omnibarViewState.isEditing,
                        query = omnibarViewState.omnibarText,
                        hasQueryChanged = true,
                        urlLoaded = _viewState.value.url,
                    ),
                    leadingIconState = when (omnibarViewState.serpLogo) {
                        is SerpLogo.EasterEgg -> getLeadingIconState(
                            viewMode = _viewState.value.viewMode,
                            hasFocus = omnibarViewState.isEditing,
                            url = _viewState.value.url,
                            serpLogoUrl = omnibarViewState.serpLogo.logoUrl,
                            isFavouriteSerpLogo = omnibarViewState.serpLogo.isFavourite,
                        )

                        SerpLogo.Normal -> getLeadingIconState(
                            viewMode = _viewState.value.viewMode,
                            hasFocus = omnibarViewState.isEditing,
                            url = _viewState.value.url,
                            serpLogoUrl = null,
                            isFavouriteSerpLogo = false,
                        )

                        null -> {
                            if (isSetFavouriteEasterEggLogoFeatureEnabled) {
                                // serpLogo is null - preserve existing Easter Egg if present
                                val currentLogoUrl = getCurrentSerpLogoUrl(
                                    currentUrl = _viewState.value.url,
                                    leadingIconState = _viewState.value.leadingIconState,
                                )
                                getLeadingIconState(
                                    viewMode = _viewState.value.viewMode,
                                    hasFocus = omnibarViewState.isEditing,
                                    url = _viewState.value.url,
                                    serpLogoUrl = currentLogoUrl,
                                    isFavouriteSerpLogo = it.leadingIconState is EasterEggLogo && it.leadingIconState.isFavourite,
                                )
                            } else {
                                // previous behaviour for null
                                getLeadingIconState(
                                    viewMode = _viewState.value.viewMode,
                                    hasFocus = omnibarViewState.isEditing,
                                    url = _viewState.value.url,
                                    serpLogoUrl = null,
                                    isFavouriteSerpLogo = false,
                                )
                            }
                        }
                    },
                )
            }
        }
    }

    private fun onExternalLoadingStateChanged(loadingState: LoadingViewState) {
        logcat { "Omnibar: onExternalLoadingStateChanged $loadingState" }
        val currentLogoUrl = if (isSetFavouriteEasterEggLogoFeatureEnabled) {
            getCurrentSerpLogoUrl(
                currentUrl = loadingState.url,
                leadingIconState = _viewState.value.leadingIconState,
            )
        } else {
            when (val leadingIconState = _viewState.value.leadingIconState) {
                is EasterEggLogo -> leadingIconState.logoUrl
                else -> null
            }
        }
        _viewState.update {
            it.copy(
                url = loadingState.url,
                isLoading = loadingState.isLoading,
                loadingProgress = loadingState.progress,
                leadingIconState = getLeadingIconState(
                    viewMode = _viewState.value.viewMode,
                    hasFocus = it.hasFocus,
                    url = loadingState.url,
                    serpLogoUrl = currentLogoUrl,
                    isFavouriteSerpLogo = it.leadingIconState is EasterEggLogo && it.leadingIconState.isFavourite,
                ),
                showVoiceSearch = shouldShowVoiceSearch(
                    viewMode = _viewState.value.viewMode,
                    hasFocus = _viewState.value.hasFocus,
                    query = _viewState.value.omnibarText,
                    hasQueryChanged = false,
                    urlLoaded = loadingState.url,
                ),
            )
        }
    }

    private fun isNotAboutBlank(viewState: OmnibarViewState) = !(handleAboutBlankEnabled && viewState.omnibarText == ABOUT_BLANK)

    fun onUserTouchedOmnibarTextInput(touchAction: Int) {
        logcat { "Omnibar: onUserTouchedOmnibarTextInput" }
        if (touchAction == ACTION_UP) {
            firePixelBasedOnCurrentUrl(
                AppPixelName.ADDRESS_BAR_NEW_TAB_PAGE_CLICKED,
                AppPixelName.ADDRESS_BAR_SERP_CLICKED,
                AppPixelName.ADDRESS_BAR_WEBSITE_CLICKED,
            )
        }
    }

    fun onBackKeyPressed() {
        logcat { "Omnibar: onBackKeyPressed" }
        firePixelBasedOnCurrentUrl(
            AppPixelName.ADDRESS_BAR_NEW_TAB_PAGE_CANCELLED,
            AppPixelName.ADDRESS_BAR_SERP_CANCELLED,
            AppPixelName.ADDRESS_BAR_WEBSITE_CANCELLED,
        )
        _viewState.update {
            it.copy(
                omnibarText = if (isFullUrlEnabled.value) it.url else addressDisplayFormatter.getShortUrl(it.url),
                updateOmnibarText = true,
            )
        }
    }

    fun onBackButtonPressed() {
        logcat { "Omnibar: onBackButtonPressed" }
        firePixelBasedOnCurrentUrl(
            AppPixelName.ADDRESS_BAR_NEW_TAB_PAGE_CLOSED,
            AppPixelName.ADDRESS_BAR_SERP_CLOSED,
            AppPixelName.ADDRESS_BAR_WEBSITE_CLOSED,
        )
        pixel.fire(DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_LEGACY_OMNIBAR_BACK_BUTTON_PRESSED)
    }

    fun onEnterKeyPressed() {
        logcat { "Omnibar: onEnterKeyPressed" }
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
                if (!decoration.entities.isNullOrEmpty()) {
                    val hasFocus = _viewState.value.hasFocus
                    if (!hasFocus) {
                        _viewState.update {
                            it.copy(
                                leadingIconState = PrivacyShield,
                            )
                        }
                        viewModelScope.launch {
                            command.send(
                                Command.StartTrackersAnimation(
                                    entities = decoration.entities,
                                    isCustomTab = viewState.value.viewMode is CustomTab,
                                    isAddressBarTrackersAnimationEnabled = viewState.value.isAddressBarTrackersAnimationEnabled,
                                ),
                            )
                        }
                    }
                }
            }

            else -> {
                // no-op
            }
        }
    }

    private fun shouldUpdateOmnibarTextInput(
        viewState: OmnibarViewState,
        currentText: String,
    ) = (!viewState.isEditing || viewState.omnibarText.isEmpty()) && currentText != viewState.omnibarText

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
        logcat { "Omnibar: onVoiceSearchDisabled" }
        _viewState.update {
            it.copy(
                showVoiceSearch = shouldShowVoiceSearch(
                    viewMode = _viewState.value.viewMode,
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
                    viewMode = customTabMode.copy(
                        title = decoration.title,
                    ),
                )
            }
        }
    }

    fun onDuckChatButtonPressed() {
        viewModelScope.launch {
            val launchSource = when {
                viewState.value.hasFocus -> "focused"
                viewState.value.viewMode is NewTab -> "ntp"
                viewState.value.viewMode is Browser -> when {
                    duckDuckGoUrlDetector.isDuckDuckGoQueryUrl(viewState.value.url) -> "serp"
                    else -> "website"
                }

                else -> "unknown"
            }
            val launchSourceParams = mapOf("source" to launchSource)
            val wasUsedBeforeParams = duckChat.createWasUsedBeforePixelParams()

            val params = mutableMapOf<String, String>().apply {
                putAll(wasUsedBeforeParams)
                putAll(launchSourceParams)
            }

            pixel.fire(DuckChatPixelName.DUCK_CHAT_SEARCHBAR_BUTTON_OPEN, parameters = params)
        }
    }

    fun setDraftTextIfNtpOrSerp(query: String) {
        val isNtp = _viewState.value.viewMode is NewTab
        val isSerp = _viewState.value.viewMode is Browser && duckDuckGoUrlDetector.isDuckDuckGoQueryUrl(_viewState.value.url)
        if (isNtp || isSerp) {
            _viewState.update {
                it.copy(
                    omnibarText = query,
                    updateOmnibarText = true,
                )
            }
        }
    }

    fun onDuckAiHeaderClicked() {
        if (duckAiFeatureState.showInputScreen.value) {
            onTextInputClickCatcherClicked()
        } else {
            _viewState.update {
                it.copy(
                    showDuckAIHeader = false,
                )
            }
            viewModelScope.launch {
                command.send(Command.FocusInputField)
            }
        }
    }

    fun onTextInputClickCatcherClicked() {
        viewModelScope.launch {
            val omnibarText = viewState.value.omnibarText
            val url = viewState.value.url
            val isDuckDuckGoQueryUrl = duckDuckGoUrlDetector.isDuckDuckGoQueryUrl(url)
            val textToPreFill = if (omnibarText.isNotEmpty() && url.isNotEmpty() && !isDuckDuckGoQueryUrl) {
                url
            } else {
                omnibarText
            }
            command.send(Command.LaunchInputScreen(query = textToPreFill))
        }
    }

    fun onLogoClicked() {
        viewModelScope.launch {
            val state = _viewState.value.leadingIconState
            if (state is EasterEggLogo) {
                command.send(Command.EasterEggLogoClicked(state.logoUrl))
            }
        }
    }

    fun onCancelAddressBarAnimations() {
        viewModelScope.launch {
            command.send(Command.CancelEasterEggLogoAnimation)
        }
    }

    private data class NewTabPixelParams(
        val isNtp: Boolean,
        val isFocused: Boolean,
        val isTabSwitcherButtonVisible: Boolean,
        val isFireButtonVisible: Boolean,
        val isBrowserMenuButtonVisible: Boolean,
    )

    companion object {
        private const val ABOUT_BLANK = "about:blank"
    }
}
