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

package com.duckduckgo.app.settings

import android.annotation.SuppressLint
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.pixels.AppPixelName.SETTINGS_ABOUT_DDG_SHARE_FEEDBACK_PRESSED
import com.duckduckgo.app.pixels.AppPixelName.SETTINGS_ABOUT_PRESSED
import com.duckduckgo.app.pixels.AppPixelName.SETTINGS_ACCESSIBILITY_PRESSED
import com.duckduckgo.app.pixels.AppPixelName.SETTINGS_APPTP_PRESSED
import com.duckduckgo.app.pixels.AppPixelName.SETTINGS_COOKIE_POPUP_PROTECTION_PRESSED
import com.duckduckgo.app.pixels.AppPixelName.SETTINGS_EMAIL_PROTECTION_PRESSED
import com.duckduckgo.app.pixels.AppPixelName.SETTINGS_FIRE_BUTTON_PRESSED
import com.duckduckgo.app.pixels.AppPixelName.SETTINGS_GENERAL_PRESSED
import com.duckduckgo.app.pixels.AppPixelName.SETTINGS_NEXT_STEPS_ADDRESS_BAR
import com.duckduckgo.app.pixels.AppPixelName.SETTINGS_NEXT_STEPS_VOICE_SEARCH
import com.duckduckgo.app.pixels.AppPixelName.SETTINGS_OPENED
import com.duckduckgo.app.pixels.AppPixelName.SETTINGS_PERMISSIONS_PRESSED
import com.duckduckgo.app.pixels.AppPixelName.SETTINGS_SYNC_PRESSED
import com.duckduckgo.app.settings.NewSettingsViewModel.Command.LaunchAboutScreen
import com.duckduckgo.app.settings.NewSettingsViewModel.Command.LaunchAccessibilitySettings
import com.duckduckgo.app.settings.NewSettingsViewModel.Command.LaunchAddHomeScreenWidget
import com.duckduckgo.app.settings.NewSettingsViewModel.Command.LaunchAppTPOnboarding
import com.duckduckgo.app.settings.NewSettingsViewModel.Command.LaunchAppTPTrackersScreen
import com.duckduckgo.app.settings.NewSettingsViewModel.Command.LaunchAppearanceScreen
import com.duckduckgo.app.settings.NewSettingsViewModel.Command.LaunchAutofillSettings
import com.duckduckgo.app.settings.NewSettingsViewModel.Command.LaunchCookiePopupProtectionScreen
import com.duckduckgo.app.settings.NewSettingsViewModel.Command.LaunchDuckChatScreen
import com.duckduckgo.app.settings.NewSettingsViewModel.Command.LaunchEmailProtection
import com.duckduckgo.app.settings.NewSettingsViewModel.Command.LaunchEmailProtectionNotSupported
import com.duckduckgo.app.settings.NewSettingsViewModel.Command.LaunchFeedback
import com.duckduckgo.app.settings.NewSettingsViewModel.Command.LaunchFireButtonScreen
import com.duckduckgo.app.settings.NewSettingsViewModel.Command.LaunchGeneralSettingsScreen
import com.duckduckgo.app.settings.NewSettingsViewModel.Command.LaunchOtherPlatforms
import com.duckduckgo.app.settings.NewSettingsViewModel.Command.LaunchPermissionsScreen
import com.duckduckgo.app.settings.NewSettingsViewModel.Command.LaunchPproUnifiedFeedback
import com.duckduckgo.app.settings.NewSettingsViewModel.Command.LaunchSyncSettings
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autoconsent.api.Autoconsent
import com.duckduckgo.autofill.api.AutofillCapabilityChecker
import com.duckduckgo.autofill.api.email.EmailManager
import com.duckduckgo.common.ui.settings.SearchableTag
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckplayer.api.DuckPlayer
import com.duckduckgo.duckplayer.api.DuckPlayer.DuckPlayerState.DISABLED_WIH_HELP_LINK
import com.duckduckgo.duckplayer.api.DuckPlayer.DuckPlayerState.ENABLED
import com.duckduckgo.mobile.android.app.tracking.AppTrackingProtection
import com.duckduckgo.subscriptions.api.PrivacyProUnifiedFeedback
import com.duckduckgo.subscriptions.api.PrivacyProUnifiedFeedback.PrivacyProFeedbackSource.DDG_SETTINGS
import com.duckduckgo.sync.api.DeviceSyncState
import com.duckduckgo.voice.api.VoiceSearchAvailability
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.text.similarity.LevenshteinDistance
import org.apache.commons.text.similarity.SimilarityInput
import java.util.UUID
import javax.inject.Inject

@SuppressLint("NoLifecycleObserver")
@ContributesViewModel(ActivityScope::class)
class NewSettingsViewModel @Inject constructor(
    private val appTrackingProtection: AppTrackingProtection,
    private val pixel: Pixel,
    private val emailManager: EmailManager,
    private val autofillCapabilityChecker: AutofillCapabilityChecker,
    private val deviceSyncState: DeviceSyncState,
    private val dispatcherProvider: DispatcherProvider,
    private val autoconsent: Autoconsent,
    private val duckPlayer: DuckPlayer,
    private val duckChat: DuckChat,
    private val voiceSearchAvailability: VoiceSearchAvailability,
    private val privacyProUnifiedFeedback: PrivacyProUnifiedFeedback,
) : ViewModel(), DefaultLifecycleObserver {

    data class ViewState(
        val appTrackingProtectionEnabled: Boolean = false,
        val emailAddress: String? = null,
        val showAutofill: Boolean = false,
        val showSyncSetting: Boolean = false,
        val isAutoconsentEnabled: Boolean = false,
        val isDuckPlayerEnabled: Boolean = false,
        val isDuckChatEnabled: Boolean = false,
        val isVoiceSearchVisible: Boolean = false,
        val searchResults: Set<UUID>? = null,
    )

    sealed class Command {
        data class LaunchEmailProtection(val url: String) : Command()
        data object LaunchEmailProtectionNotSupported : Command()
        data object LaunchAutofillSettings : Command()
        data object LaunchAccessibilitySettings : Command()
        data object LaunchAddHomeScreenWidget : Command()
        data object LaunchAppTPTrackersScreen : Command()
        data object LaunchAppTPOnboarding : Command()
        data object LaunchSyncSettings : Command()
        data object LaunchCookiePopupProtectionScreen : Command()
        data object LaunchFireButtonScreen : Command()
        data object LaunchPermissionsScreen : Command()
        data object LaunchDuckChatScreen : Command()
        data object LaunchAppearanceScreen : Command()
        data object LaunchAboutScreen : Command()
        data object LaunchGeneralSettingsScreen : Command()
        data object LaunchFeedback : Command()
        data object LaunchPproUnifiedFeedback : Command()
        data object LaunchOtherPlatforms : Command()
    }

    private val viewState = MutableStateFlow(ViewState())

    private val command = Channel<Command>(1, BufferOverflow.DROP_OLDEST)
    private val appTPPollJob = ConflatedJob()

    init {
        pixel.fire(SETTINGS_OPENED)
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        start()
        startPollingAppTPState()
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        appTPPollJob.cancel()
    }

    @VisibleForTesting
    internal fun start() {

        viewModelScope.launch {
            viewState.emit(
                currentViewState().copy(
                    appTrackingProtectionEnabled = appTrackingProtection.isRunning(),
                    emailAddress = emailManager.getEmailAddress(),
                    showAutofill = autofillCapabilityChecker.canAccessCredentialManagementScreen(),
                    showSyncSetting = deviceSyncState.isFeatureEnabled(),
                    isAutoconsentEnabled = autoconsent.isSettingEnabled(),
                    isDuckPlayerEnabled = duckPlayer.getDuckPlayerState().let { it == ENABLED || it == DISABLED_WIH_HELP_LINK },
                    isDuckChatEnabled = duckChat.isEnabled(),
                    isVoiceSearchVisible = voiceSearchAvailability.isVoiceSearchSupported,
                ),
            )
        }
    }

    // FIXME
    // We need to fix this. This logic as inside the start method but it messes with the unit tests
    // because when doing runningBlockingTest {} there is no delay and the tests crashes because this
    // becomes a while(true) without any delay
    private fun startPollingAppTPState() {
        appTPPollJob += viewModelScope.launch(dispatcherProvider.io()) {
            while (isActive) {
                val isDeviceShieldEnabled = appTrackingProtection.isRunning()
                val currentState = currentViewState()
                viewState.value = currentState.copy(
                    appTrackingProtectionEnabled = isDeviceShieldEnabled,
                )
                delay(1_000)
            }
        }
    }

    fun viewState(): StateFlow<ViewState> {
        return viewState
    }

    fun commands(): Flow<Command> {
        return command.receiveAsFlow()
    }

    fun userRequestedToAddHomeScreenWidget() {
        viewModelScope.launch { command.send(LaunchAddHomeScreenWidget) }
    }

    fun onChangeAddressBarPositionClicked() {
        viewModelScope.launch { command.send(LaunchAppearanceScreen) }
        pixel.fire(SETTINGS_NEXT_STEPS_ADDRESS_BAR)
    }

    fun onEnableVoiceSearchClicked() {
        viewModelScope.launch { command.send(LaunchAccessibilitySettings) }
        pixel.fire(SETTINGS_NEXT_STEPS_VOICE_SEARCH)
    }

    fun onCookiePopupProtectionSettingClicked() {
        viewModelScope.launch { command.send(LaunchCookiePopupProtectionScreen) }
        pixel.fire(SETTINGS_COOKIE_POPUP_PROTECTION_PRESSED)
    }

    fun onAutofillSettingsClick() {
        viewModelScope.launch { command.send(LaunchAutofillSettings) }
    }

    fun onAccessibilitySettingClicked() {
        viewModelScope.launch { command.send(LaunchAccessibilitySettings) }
        pixel.fire(SETTINGS_ACCESSIBILITY_PRESSED)
    }

    fun onAboutSettingClicked() {
        viewModelScope.launch { command.send(LaunchAboutScreen) }
        pixel.fire(SETTINGS_ABOUT_PRESSED)
    }

    fun onGeneralSettingClicked() {
        viewModelScope.launch { command.send(LaunchGeneralSettingsScreen) }
        pixel.fire(SETTINGS_GENERAL_PRESSED)
    }

    fun onEmailProtectionSettingClicked() {
        viewModelScope.launch {
            val command = if (emailManager.isEmailFeatureSupported()) {
                LaunchEmailProtection(EMAIL_PROTECTION_URL)
            } else {
                LaunchEmailProtectionNotSupported
            }
            this@NewSettingsViewModel.command.send(command)
        }
        pixel.fire(SETTINGS_EMAIL_PROTECTION_PRESSED)
    }

    fun onAppTPSettingClicked() {
        viewModelScope.launch {
            if (appTrackingProtection.isOnboarded()) {
                command.send(LaunchAppTPTrackersScreen)
            } else {
                command.send(LaunchAppTPOnboarding)
            }
            pixel.fire(SETTINGS_APPTP_PRESSED)
        }
    }

    private fun currentViewState(): ViewState {
        return viewState.value
    }

    fun onSyncSettingClicked() {
        viewModelScope.launch { command.send(LaunchSyncSettings) }
        pixel.fire(SETTINGS_SYNC_PRESSED)
    }

    fun onFireButtonSettingClicked() {
        viewModelScope.launch { command.send(LaunchFireButtonScreen) }
        pixel.fire(SETTINGS_FIRE_BUTTON_PRESSED)
    }

    fun onPermissionsSettingClicked() {
        viewModelScope.launch { command.send(LaunchPermissionsScreen) }
        pixel.fire(SETTINGS_PERMISSIONS_PRESSED)
    }

    fun onDuckChatSettingClicked() {
        viewModelScope.launch { command.send(LaunchDuckChatScreen) }
    }

    fun onShareFeedbackClicked() {
        viewModelScope.launch {
            if (privacyProUnifiedFeedback.shouldUseUnifiedFeedback(source = DDG_SETTINGS)) {
                command.send(LaunchPproUnifiedFeedback)
            } else {
                command.send(LaunchFeedback)
            }
        }
        pixel.fire(SETTINGS_ABOUT_DDG_SHARE_FEEDBACK_PRESSED)
    }

    fun onLaunchedFromNotification(pixelName: String) {
        pixel.fire(pixelName)
    }

    fun onDdgOnOtherPlatformsClicked() {
        viewModelScope.launch { command.send(LaunchOtherPlatforms) }
    }

    private var debounceJob: Job? = null

    fun onSearchQueryTextChange(
        query: String?,
        searchableTags: List<SearchableTag>
    ) {
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            delay(300) // 300ms debounce
            processSearch(query, searchableTags)
        }
    }

    private suspend fun processSearch(query: String?, searchableTags: List<SearchableTag>) {
        if (query.isNullOrBlank()) {
            viewState.update { it.copy(searchResults = null) }
            return
        }

        withContext(dispatcherProvider.computation()) {
            val normalizedQuery = query.lowercase().trim()
            val queryWords = normalizedQuery.split("\\s+".toRegex())
            val queryNGrams = generateNGrams(normalizedQuery, 3)  // Using trigrams

            val results = searchableTags.filter { tag ->
                tag.keywords.any { keyword ->
                    val normalizedKeyword = keyword.lowercase()

                    // Exact match
                    if (normalizedKeyword == normalizedQuery) return@any true

                    // Prefix matching (only for single-word queries)
                    if (queryWords.size == 1 && normalizedKeyword.startsWith(normalizedQuery)) return@any true

                    // Word tokenization and matching
                    if (queryWords.size > 1 && queryWords.all { word -> normalizedKeyword.contains(word) }) return@any true

                    // N-gram similarity with a higher threshold
                    val keywordNGrams = generateNGrams(normalizedKeyword, 2)
                    val similarity = calculateJaccardSimilarity(queryNGrams, keywordNGrams)
                    if (similarity >= 0.5) return@any true  // Increased threshold for stricter matching

                    // Partial word matching with typo tolerance
                    if (partialMatchWithTypoTolerance(normalizedQuery, normalizedKeyword)) return@any true

                    false
                }
            }.map { it.id }.toSet()

            viewState.update { it.copy(searchResults = results) }
        }
    }

    private fun generateNGrams(text: String, n: Int): Set<String> {
        return text.windowed(n).toSet()
    }

    private fun calculateJaccardSimilarity(set1: Set<String>, set2: Set<String>): Double {
        val intersection = set1.intersect(set2)
        val union = set1.union(set2)
        return intersection.size.toDouble() / union.size
    }

    private fun partialMatchWithTypoTolerance(query: String, keyword: String): Boolean {
        val queryWords = query.split("\\s+".toRegex())
        val keywordWords = keyword.split("\\s+".toRegex())

        return queryWords.all { queryWord ->
            keywordWords.any { keywordWord ->
                val distance = levenshteinDistance(queryWord, keywordWord)
                when {
                    queryWord.length <= 2 -> {
                        queryWord == keywordWord
                    }
                    queryWord.length <= 3 -> {
                        distance in 0..1
                    }
                    queryWord.length <= 5 -> {
                        distance in 0..2
                    }
                    else -> {
                        distance in 0..3
                    }
                }
            }
        }
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        return LevenshteinDistance(4).apply(SimilarityInput.input(s1), SimilarityInput.input(s2))
    }

    companion object {
        const val EMAIL_PROTECTION_URL = "https://duckduckgo.com/email"
    }
}
