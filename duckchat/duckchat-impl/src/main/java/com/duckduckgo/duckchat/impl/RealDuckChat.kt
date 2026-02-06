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

package com.duckduckgo.duckchat.impl

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.di.IsMainProcess
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.tabs.BrowserNav
import com.duckduckgo.common.utils.AppUrl.ParamKey.QUERY
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.DuckAiFeatureState
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.api.DuckChatSettingsNoParams
import com.duckduckgo.duckchat.impl.DuckChatConstants.HOST_DUCK_AI
import com.duckduckgo.duckchat.impl.feature.AIChatImageUploadFeature
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.duckduckgo.duckchat.impl.inputscreen.newaddressbaroption.NewAddressBarCallback
import com.duckduckgo.duckchat.impl.inputscreen.newaddressbaroption.NewAddressBarOptionBottomSheetDialogFactory
import com.duckduckgo.duckchat.impl.inputscreen.newaddressbaroption.NewAddressBarSelection
import com.duckduckgo.duckchat.impl.inputscreen.newaddressbaroption.NewAddressBarSelection.SEARCH_AND_AI
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_NEW_ADDRESS_BAR_PICKER_CANCELLED
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_NEW_ADDRESS_BAR_PICKER_CONFIRMED
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_NEW_ADDRESS_BAR_PICKER_DISPLAYED
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_NEW_ADDRESS_BAR_PICKER_NOT_NOW
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelParameters.NEW_ADDRESS_BAR_SELECTION
import com.duckduckgo.duckchat.impl.repository.DuckChatFeatureRepository
import com.duckduckgo.duckchat.impl.ui.DuckAiContextualOnboardingBottomSheetDialog
import com.duckduckgo.duckchat.impl.ui.DuckAiContextualOnboardingBottomSheetDialogFactory
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.privacy.config.api.PrivacyConfigCallbackPlugin
import com.duckduckgo.sync.api.DeviceSyncState
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.logcat
import okhttp3.HttpUrl.Companion.toHttpUrl
import javax.inject.Inject

interface DuckChatInternal : DuckChat {
    /**
     * Set user setting to determine whether DuckChat should be enabled or disabled.
     */
    suspend fun setEnableDuckChatUserSetting(enabled: Boolean)

    /**
     * Set user setting to determine whether DuckChat should be shown in browser menu.
     */
    suspend fun setShowInBrowserMenuUserSetting(showDuckChat: Boolean)

    /**
     * Set user setting to determine whether DuckChat should be shown in address bar.
     */
    suspend fun setShowInAddressBarUserSetting(showDuckChat: Boolean)

    /**
     * Set user setting to determine whether the Input Mode toggle should be shown on the voice search screen.
     */
    suspend fun setShowInVoiceSearchUserSetting(showToggle: Boolean)

    /**
     * Set user setting to determine whether DuckChat should automatically update the page context in Contextual Mode
     */
    suspend fun setAutomaticPageContextUserSetting(isEnabled: Boolean)

    /**
     * Observes whether DuckChat is user enabled or disabled.
     */
    fun observeEnableDuckChatUserSetting(): Flow<Boolean>

    /**
     * Observes whether DuckChat should be shown in browser menu based on user settings only.
     */
    fun observeShowInBrowserMenuUserSetting(): Flow<Boolean>

    /**
     * Observes whether DuckChat should be shown in address bar based on user settings only.
     */
    fun observeShowInAddressBarUserSetting(): Flow<Boolean>

    /**
     * Observes whether the Input Mode toggle should be shown on the voice search screen based on user settings only.
     */
    fun observeShowInVoiceSearchUserSetting(): Flow<Boolean>

    /**
     * Opens DuckChat settings.
     */
    fun openDuckChatSettings()

    /**
     * Closes DuckChat.
     */
    fun closeDuckChat()

    /**
     * Opens DuckChat with a new session.
     */
    fun openNewDuckChatSession()

    /**
     * Calls onClose when a close event is emitted.
     */
    fun observeCloseEvent(
        lifecycleOwner: LifecycleOwner,
        onClose: () -> Unit,
    )

    /**
     * Returns whether address bar entry point is enabled or not.
     */
    fun isAddressBarEntryPointEnabled(): Boolean

    /**
     * Returns whether voice search entry point is enabled or not.
     */
    fun isVoiceSearchEntryPointEnabled(): Boolean

    /**
     * Returns whether DuckChat is user enabled or not.
     */
    fun isDuckChatUserEnabled(): Boolean

    /**
     * Updates the current chat state.
     */
    fun updateChatState(state: ChatState)

    /**
     * Returns the current chat state.
     */
    val chatState: StateFlow<ChatState>

    /**
     * Returns whether image upload is enabled or not.
     */
    fun isImageUploadEnabled(): Boolean

    /**
     * Returns whether standalone migration is supported.
     */
    fun isStandaloneMigrationEnabled(): Boolean

    /**
     * Returns the time a Duck Chat session should be kept alive
     */
    fun keepSessionIntervalInMinutes(): Int

    /**
     * Returns whether dedicated Duck.ai input screen feature is available (its feature flag is enabled).
     */
    fun isInputScreenFeatureAvailable(): Boolean

    /**
     * Returns whether dedicated Duck.ai full screen mode is enabled (its feature flag is enabled).
     */
    fun isDuckChatFullScreenModeEnabled(): Boolean

    /**
     * Returns whether dedicated Duck.ai contextual mode is enabled (its feature flag is enabled).
     */
    fun isDuckChatContextualModeEnabled(): Boolean

    /**
     * Checks whether DuckChat is enabled based on remote config flag.
     */
    fun isDuckChatFeatureEnabled(): Boolean

    /**
     * Returns whether chat sync feature is enabled.
     */
    fun isChatSyncFeatureEnabled(): Boolean

    /**
     * Returns whether Duck.ai in contextual mode should auto attach context (its feature flag is enabled and user setting is enabled).
     */
    fun isAutomaticContextAttachmentEnabled(): Boolean

    /**
     * This method takes a [url] and returns `true` or `false`.
     * @return `true` if the given [url] can be handled in the duck ai webview and `false` otherwise.
     */
    fun canHandleOnAiWebView(url: String): Boolean

    /**
     * Indicates whether Input Screen will present the input box at the bottom, if user has the omnibar also set to the bottom position.
     * Otherwise, the input box will be at the top.
     */
    val inputScreenBottomBarEnabled: StateFlow<Boolean>

    /**
     * Indicates whether the three main button should be shown in the Input Screen
     */
    val showMainButtonsInInputScreen: StateFlow<Boolean>
}

enum class ChatState(
    val value: String,
) {
    START_STREAM_NEW_PROMPT("start_stream:new_prompt"),
    LOADING("loading"),
    STREAMING("streaming"),
    ERROR("error"),
    READY("ready"),
    BLOCKED("blocked"),
    HIDE("hide"),
    SHOW("show"),
    ;

    companion object {
        fun fromValue(v: String?): ChatState? = entries.firstOrNull { it.value == v }
    }
}

enum class ReportMetric(
    val metric: String,
) {
    USER_DID_SUBMIT_PROMPT("userDidSubmitPrompt"),
    USER_DID_SUBMIT_FIRST_PROMPT("userDidSubmitFirstPrompt"),
    USER_DID_OPEN_HISTORY("userDidOpenHistory"),
    USER_DID_SELECT_FIRST_HISTORY_ITEM("userDidSelectFirstHistoryItem"),
    USER_DID_CREATE_NEW_CHAT("userDidCreateNewChat"),
    USER_DID_TAP_KEYBOARD_RETURN_KEY("userDidTapKeyboardReturnKey"),
    ;

    companion object {
        fun fromValue(v: String?): ReportMetric? = ReportMetric.entries.firstOrNull { it.metric == v }
    }
}

data class DuckChatSettingJson(
    val aiChatURL: String?,
    val aiChatBangs: List<String>?,
    val aiChatBangRegex: String?,
    val addressBarEntryPoint: Boolean,
    val sessionTimeoutMinutes: Int,
)

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = DuckChat::class)
@ContributesBinding(AppScope::class, boundType = DuckAiFeatureState::class)
@ContributesBinding(AppScope::class, boundType = DuckChatInternal::class)
@ContributesMultibinding(AppScope::class, boundType = PrivacyConfigCallbackPlugin::class)
class RealDuckChat @Inject constructor(
    private val duckChatFeatureRepository: DuckChatFeatureRepository,
    private val duckChatFeature: DuckChatFeature,
    private val moshi: Moshi,
    private val dispatchers: DispatcherProvider,
    private val globalActivityStarter: GlobalActivityStarter,
    private val context: Context,
    @IsMainProcess private val isMainProcess: Boolean,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val pixel: Pixel,
    private val imageUploadFeature: AIChatImageUploadFeature,
    private val browserNav: BrowserNav,
    private val newAddressBarOptionBottomSheetDialogFactory: NewAddressBarOptionBottomSheetDialogFactory,
    private val duckAiContextualOnboardingBottomSheetDialogFactory: DuckAiContextualOnboardingBottomSheetDialogFactory,
    private val deviceSyncState: DeviceSyncState,
) : DuckChatInternal,
    DuckAiFeatureState,
    PrivacyConfigCallbackPlugin {
    private val closeChatFlow = MutableSharedFlow<Unit>(replay = 0)
    private val _showSettings = MutableStateFlow(false)
    private val _showInputScreen = MutableStateFlow(false)
    private val _showInputScreenAutomaticallyOnNewTab = MutableStateFlow(false)
    private val _inputScreenBottomBarEnabled = MutableStateFlow(false)
    private val _showPopupMenuShortcut = MutableStateFlow(false)
    private val _showOmnibarShortcutOnNtpAndOnFocus = MutableStateFlow(false)
    private val _showOmnibarShortcutInAllStates = MutableStateFlow(false)
    private val _showNewAddressBarOptionChoiceScreen = MutableStateFlow(false)
    private val _showClearDuckAIChatHistory = MutableStateFlow(true)
    private val _showMainButtonsInInputScreen = MutableStateFlow(false)

    private val _chatState = MutableStateFlow(ChatState.HIDE)
    private val _showInputScreenOnSystemSearchLaunch = MutableStateFlow(false)
    private val _showVoiceSearchToggle = MutableStateFlow(false)
    private val _showFullScreenMode = MutableStateFlow(false)
    private val _showFullScreenModeToggle = MutableStateFlow(false)
    private val _showContextualMode = MutableStateFlow(false)

    private val jsonAdapter: JsonAdapter<DuckChatSettingJson> by lazy {
        moshi.adapter(DuckChatSettingJson::class.java)
    }

    private var isDuckChatFeatureEnabled = false
    private var isDuckAiInBrowserEnabled = false
    private var duckAiInputScreenOpenAutomaticallyEnabled = false
    private var duckAiInputScreen = false
    private var duckAiInputScreenBottomBarEnabled = false
    private var showAIChatAddressBarChoiceScreen = false
    private var isDuckChatUserEnabled = false
    private var isChatSyncFeatureEnabled = false
    private var duckChatLink = DUCK_CHAT_WEB_LINK
    private var bangRegex: Regex? = null
    private var isAddressBarEntryPointEnabled: Boolean = false
    private var isVoiceSearchEntryPointEnabled: Boolean = false
    private var isImageUploadEnabled: Boolean = false
    private var isStandaloneMigrationEnabled: Boolean = false
    private var keepSessionAliveInMinutes: Int = DEFAULT_SESSION_ALIVE
    private var clearChatHistory: Boolean = true
    private var inputScreenMainButtonsEnabled = false
    private var showInputScreenOnSystemSearchLaunchEnabled: Boolean = true
    private var isFullscreenModeEnabled: Boolean = false
    private var isContextualModeEnabled: Boolean = false
    private var isAutomaticContextAttachmentEnabled: Boolean = false

    init {
        if (isMainProcess) {
            cacheConfig()
        }
    }

    override fun onPrivacyConfigDownloaded() {
        cacheConfig()
    }

    override suspend fun setEnableDuckChatUserSetting(enabled: Boolean) {
        duckChatFeatureRepository.setDuckChatUserEnabled(enabled)
        cacheUserSettings()
    }

    override suspend fun setInputScreenUserSetting(enabled: Boolean) {
        duckChatFeatureRepository.setInputScreenUserSetting(enabled)
        cacheUserSettings()
    }

    override suspend fun setCosmeticInputScreenUserSetting(enabled: Boolean) {
        duckChatFeatureRepository.setCosmeticInputScreenUserSetting(enabled)
    }

    override suspend fun setShowInBrowserMenuUserSetting(showDuckChat: Boolean) =
        withContext(dispatchers.io()) {
            duckChatFeatureRepository.setShowInBrowserMenu(showDuckChat)
            cacheUserSettings()
        }

    override suspend fun setShowInAddressBarUserSetting(showDuckChat: Boolean) =
        withContext(dispatchers.io()) {
            duckChatFeatureRepository.setShowInAddressBar(showDuckChat)
            cacheUserSettings()
        }

    override suspend fun setShowInVoiceSearchUserSetting(showToggle: Boolean) =
        withContext(dispatchers.io()) {
            duckChatFeatureRepository.setShowInVoiceSearch(showToggle)
            cacheUserSettings()
        }

    override suspend fun setAutomaticPageContextUserSetting(isEnabled: Boolean) {
        withContext(dispatchers.io()) {
            duckChatFeatureRepository.setAutomaticPageContextAttachment(isEnabled)
            cacheUserSettings()
        }
    }

    override fun isEnabled(): Boolean = isDuckChatFeatureEnabled && isDuckChatUserEnabled

    override fun isInputScreenFeatureAvailable(): Boolean = duckAiInputScreen

    override fun isDuckChatFeatureEnabled(): Boolean = isDuckChatFeatureEnabled

    override fun isChatSyncFeatureEnabled(): Boolean = isChatSyncFeatureEnabled

    override fun isDuckChatFullScreenModeEnabled(): Boolean = isFullscreenModeEnabled

    override fun isDuckChatContextualModeEnabled(): Boolean = isContextualModeEnabled

    override fun isAutomaticContextAttachmentEnabled(): Boolean = isAutomaticContextAttachmentEnabled

    override fun observeEnableDuckChatUserSetting(): Flow<Boolean> = duckChatFeatureRepository.observeDuckChatUserEnabled()

    override fun observeInputScreenUserSettingEnabled(): Flow<Boolean> = duckChatFeatureRepository.observeInputScreenUserSettingEnabled()

    override fun observeCosmeticInputScreenUserSettingEnabled(): Flow<Boolean?> =
        duckChatFeatureRepository.observeCosmeticInputScreenUserSettingEnabled()

    override fun observeAutomaticContextAttachmentUserSettingEnabled(): Flow<Boolean> =
        duckChatFeatureRepository.observeAutomaticContextAttachmentUserSettingEnabled()

    override fun observeShowInBrowserMenuUserSetting(): Flow<Boolean> = duckChatFeatureRepository.observeShowInBrowserMenu()

    override fun observeShowInAddressBarUserSetting(): Flow<Boolean> = duckChatFeatureRepository.observeShowInAddressBar()

    override fun observeShowInVoiceSearchUserSetting(): Flow<Boolean> = duckChatFeatureRepository.observeShowInVoiceSearch()

    override fun openDuckChatSettings() {
        val intent = globalActivityStarter.startIntent(context, DuckChatSettingsNoParams)
        intent?.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
        appCoroutineScope.launch {
            closeChatFlow.emit(Unit)
        }
    }

    override fun closeDuckChat() {
        browserNav.closeDuckChat(context).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(this)
        }
    }

    override fun observeCloseEvent(
        lifecycleOwner: LifecycleOwner,
        onClose: () -> Unit,
    ) {
        lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                closeChatFlow.collect {
                    onClose()
                }
            }
        }
    }

    override fun canHandleOnAiWebView(url: String): Boolean {
        return runCatching { HOST_DUCK_AI == url.toHttpUrl().topPrivateDomain() || url == REVOKE_URL }.getOrElse { false }
    }

    override fun isAddressBarEntryPointEnabled(): Boolean = isAddressBarEntryPointEnabled
    override fun isVoiceSearchEntryPointEnabled(): Boolean = isVoiceSearchEntryPointEnabled

    override fun isDuckChatUserEnabled(): Boolean = isDuckChatUserEnabled

    override fun updateChatState(state: ChatState) {
        _chatState.value = state
    }

    override val showSettings: StateFlow<Boolean> = _showSettings.asStateFlow()

    override val showInputScreen: StateFlow<Boolean> = _showInputScreen.asStateFlow()

    override val showInputScreenAutomaticallyOnNewTab: StateFlow<Boolean> = _showInputScreenAutomaticallyOnNewTab.asStateFlow()

    override val inputScreenBottomBarEnabled: StateFlow<Boolean> = _inputScreenBottomBarEnabled.asStateFlow()

    override val showPopupMenuShortcut: StateFlow<Boolean> = _showPopupMenuShortcut.asStateFlow()

    override val showOmnibarShortcutOnNtpAndOnFocus: StateFlow<Boolean> = _showOmnibarShortcutOnNtpAndOnFocus.asStateFlow()

    override val showOmnibarShortcutInAllStates: StateFlow<Boolean> = _showOmnibarShortcutInAllStates.asStateFlow()

    override val showNewAddressBarOptionChoiceScreen: StateFlow<Boolean> = _showNewAddressBarOptionChoiceScreen.asStateFlow()

    override val showMainButtonsInInputScreen: StateFlow<Boolean> = _showMainButtonsInInputScreen.asStateFlow()

    override val showClearDuckAIChatHistory: StateFlow<Boolean> = _showClearDuckAIChatHistory.asStateFlow()

    override val showInputScreenOnSystemSearchLaunch: StateFlow<Boolean> = _showInputScreenOnSystemSearchLaunch.asStateFlow()

    override val showVoiceSearchToggle: StateFlow<Boolean> = _showVoiceSearchToggle.asStateFlow()

    override val showFullScreenMode: StateFlow<Boolean> = _showFullScreenMode.asStateFlow()

    override val showFullScreenModeToggle: StateFlow<Boolean> = _showFullScreenModeToggle.asStateFlow()

    override val showContextualMode: StateFlow<Boolean> = _showContextualMode.asStateFlow()

    override val chatState: StateFlow<ChatState> = _chatState.asStateFlow()

    override fun isImageUploadEnabled(): Boolean = isImageUploadEnabled

    override fun isStandaloneMigrationEnabled(): Boolean = isStandaloneMigrationEnabled

    override fun keepSessionIntervalInMinutes() = keepSessionAliveInMinutes

    override fun openDuckChat() {
        logcat { "Duck.ai: openDuckChat" }
        openDuckChat(emptyMap())
    }

    override fun openDuckChatWithAutoPrompt(query: String) {
        logcat { "Duck.ai: openDuckChatWithAutoPrompt query $query" }
        val parameters = addChatParameters(query, autoPrompt = true, sidebar = false)
        openDuckChat(parameters, forceNewSession = true)
    }

    override fun openDuckChatWithPrefill(query: String) {
        logcat { "Duck.ai: openDuckChatWithPrefill query $query" }
        val parameters = addChatParameters(query, autoPrompt = false, sidebar = false)
        openDuckChat(parameters, forceNewSession = true)
    }

    override fun getDuckChatUrl(
        query: String,
        autoPrompt: Boolean,
        sidebar: Boolean,
    ): String {
        val parameters = addChatParameters(query, autoPrompt = autoPrompt, sidebar = sidebar)
        val url = appendParameters(parameters, duckChatLink)
        return url
    }

    private fun addChatParameters(
        query: String,
        autoPrompt: Boolean,
        sidebar: Boolean,
    ): Map<String, String> {
        val hasDuckChatBang = isDuckChatBang(query.toUri())
        val cleanedQuery = stripBang(query)
        return mutableMapOf<String, String>().apply {
            if (cleanedQuery.isNotEmpty()) {
                put(QUERY, cleanedQuery)
                if (hasDuckChatBang) {
                    put(BANG_QUERY_NAME, BANG_QUERY_VALUE)
                }
                if (autoPrompt) {
                    put(PROMPT_QUERY_NAME, PROMPT_QUERY_VALUE)
                }
            }
            if (sidebar) {
                put(PLACEMENT_QUERY_NAME, PLACEMENT_QUERY_VALUE)
            }
        }
    }

    private fun stripBang(query: String): String {
        val bangPattern = Regex("!\\w+")
        return query.replace(bangPattern, "").trim()
    }

    override fun openNewDuckChatSession() {
        openDuckChat(emptyMap(), forceNewSession = true)
    }

    private fun openDuckChat(
        parameters: Map<String, String>,
        forceNewSession: Boolean = false,
    ) {
        val url = appendParameters(parameters, duckChatLink)
        appCoroutineScope.launch(dispatchers.io()) {
            val hasSessionActive =
                when {
                    forceNewSession -> false
                    else -> hasActiveSession()
                }

            withContext(dispatchers.main()) {
                logcat { "Duck.ai: restoring Duck.ai session $url hasSessionActive $hasSessionActive" }
                openDuckChatSession(url, hasSessionActive)
            }
        }
    }

    private fun openDuckChatSession(
        url: String,
        hasSessionActive: Boolean,
    ) {
        // if a new query was submitted we force a new session
        // we want to lose the context of the previous one if the user wanted a new query from outside Duck.ai
        browserNav
            .openDuckChat(context, duckChatUrl = url, hasSessionActive = hasSessionActive)
            .apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(this)
            }
    }

    private fun appendParameters(
        parameters: Map<String, String>,
        url: String,
    ): String {
        if (parameters.isEmpty()) return url
        return runCatching {
            val uri = url.toUri()
            uri
                .buildUpon()
                .apply {
                    clearQuery()
                    parameters.forEach { (key, value) ->
                        appendQueryParameter(key, value)
                    }
                    uri.queryParameterNames
                        .filterNot { it in parameters.keys }
                        .forEach { appendQueryParameter(it, uri.getQueryParameter(it)) }
                }.build()
                .toString()
        }.getOrElse {
            logcat { "Duck.ai: parameters $url" }
            url
        }
    }

    override fun isDuckChatUrl(uri: Uri): Boolean {
        if (!isDuckChatFeatureEnabled) return false

        if (isDuckChatBang(uri)) return true

        if (uri.host == DUCK_AI_HOST || uri.toString() == DUCK_AI_HOST) return true
        if (uri.host != DUCKDUCKGO_HOST) return false

        return runCatching {
            val queryParameters = uri.queryParameterNames
            queryParameters.contains(CHAT_QUERY_NAME) && uri.getQueryParameter(CHAT_QUERY_NAME) == CHAT_QUERY_VALUE
        }.getOrDefault(false)
    }

    private fun isDuckChatBang(uri: Uri): Boolean = bangRegex?.containsMatchIn(uri.toString()) == true

    override suspend fun wasOpenedBefore(): Boolean = duckChatFeatureRepository.wasOpenedBefore()

    override fun showNewAddressBarOptionChoiceScreen(
        context: Context,
        isDarkThemeEnabled: Boolean,
    ) {
        newAddressBarOptionBottomSheetDialogFactory
            .create(
                context = context,
                isDarkThemeEnabled = isDarkThemeEnabled,
                newAddressBarCallback =
                object : NewAddressBarCallback {
                    override fun onDisplayed() {
                        pixel.fire(DUCK_CHAT_NEW_ADDRESS_BAR_PICKER_DISPLAYED)
                    }

                    override fun onConfirmed(selection: NewAddressBarSelection) {
                        if (selection == SEARCH_AND_AI) {
                            appCoroutineScope.launch {
                                setInputScreenUserSetting(true)
                            }
                        }
                        pixel.fire(
                            pixel = DUCK_CHAT_NEW_ADDRESS_BAR_PICKER_CONFIRMED,
                            parameters = mapOf(NEW_ADDRESS_BAR_SELECTION to selection.value),
                        )
                    }

                    override fun onNotNow() {
                        pixel.fire(DUCK_CHAT_NEW_ADDRESS_BAR_PICKER_NOT_NOW)
                    }

                    override fun onCancelled() {
                        pixel.fire(DUCK_CHAT_NEW_ADDRESS_BAR_PICKER_CANCELLED)
                    }
                },
            ).show()
    }

    override fun showContextualOnboarding(
        context: Context,
        onConfirmed: () -> Unit,
    ) {
        val dialog = duckAiContextualOnboardingBottomSheetDialogFactory.create(context)
        dialog.eventListener = object : DuckAiContextualOnboardingBottomSheetDialog.EventListener {
            override fun onConfirmed() {
                onConfirmed()
            }
        }
        dialog.show()
    }

    override suspend fun isContextualOnboardingCompleted(): Boolean {
        return duckChatFeatureRepository.isContextualOnboardingCompleted()
    }

    private suspend fun hasActiveSession(): Boolean {
        val now = System.currentTimeMillis()
        val lastSession = duckChatFeatureRepository.lastSessionTimestamp()
        logcat { "Duck.ai lastSessionTimestamp $lastSession" }

        val timeDifference = (now - lastSession) / 60000L
        logcat { "Duck.ai difference in minutes between now and last session is $timeDifference sessionTimeout $keepSessionAliveInMinutes" }

        return timeDifference <= keepSessionAliveInMinutes
    }

    private fun cacheConfig() {
        appCoroutineScope.launch(dispatchers.io()) {
            val featureEnabled = duckChatFeature.self().isEnabled()
            isDuckChatFeatureEnabled = featureEnabled
            _showSettings.value = featureEnabled
            isDuckAiInBrowserEnabled = duckChatFeature.duckAiButtonInBrowser().isEnabled()
            duckAiInputScreen = duckChatFeature.duckAiInputScreen().isEnabled()
            duckAiInputScreenOpenAutomaticallyEnabled = duckChatFeature.showInputScreenAutomaticallyOnNewTab().isEnabled()
            duckAiInputScreenBottomBarEnabled = duckChatFeature.inputScreenBottomBarSupport().isEnabled()
            clearChatHistory = duckChatFeature.clearHistory().isEnabled()
            showAIChatAddressBarChoiceScreen = duckChatFeature.showAIChatAddressBarChoiceScreen().isEnabled()
            showInputScreenOnSystemSearchLaunchEnabled = duckChatFeature.showInputScreenOnSystemSearchLaunch().isEnabled()
            inputScreenMainButtonsEnabled = duckChatFeature.showMainButtonsInInputScreen().isEnabled()
            isChatSyncFeatureEnabled = deviceSyncState.isDuckChatSyncFeatureEnabled()

            val showMainButtons = duckChatFeature.showMainButtonsInInputScreen().isEnabled()
            _showMainButtonsInInputScreen.emit(showMainButtons)

            val settingsString = duckChatFeature.self().getSettings()
            val settingsJson =
                settingsString?.let {
                    runCatching { jsonAdapter.fromJson(it) }.getOrNull()
                }

            duckChatLink = settingsJson?.aiChatURL ?: DUCK_CHAT_WEB_LINK
            settingsJson
                ?.aiChatBangs
                ?.takeIf { it.isNotEmpty() }
                ?.let { bangs ->
                    val bangAlternation = bangs.joinToString("|") { it }
                    bangRegex = settingsJson.aiChatBangRegex?.replace("{bangs}", bangAlternation)?.toRegex()
                }
            isAddressBarEntryPointEnabled = settingsJson?.addressBarEntryPoint ?: false
            isVoiceSearchEntryPointEnabled = duckChatFeature.duckAiVoiceSearch().isEnabled()
            isImageUploadEnabled = imageUploadFeature.self().isEnabled()
            isStandaloneMigrationEnabled = duckChatFeature.standaloneMigration().isEnabled()

            keepSessionAliveInMinutes = settingsJson?.sessionTimeoutMinutes ?: DEFAULT_SESSION_ALIVE

            cacheUserSettings()
        }
    }

    private suspend fun cacheUserSettings() =
        withContext(dispatchers.io()) {
            isDuckChatUserEnabled = duckChatFeatureRepository.isDuckChatUserEnabled()

            val showInputScreen =
                isInputScreenFeatureAvailable() && isDuckChatFeatureEnabled && isDuckChatUserEnabled &&
                    duckChatFeatureRepository.isInputScreenUserSettingEnabled()
            _showInputScreen.emit(showInputScreen)

            _showInputScreenAutomaticallyOnNewTab.value = showInputScreen && duckAiInputScreenOpenAutomaticallyEnabled

            _inputScreenBottomBarEnabled.value = showInputScreen && duckAiInputScreenBottomBarEnabled

            _showInputScreenOnSystemSearchLaunch.value = showInputScreen && showInputScreenOnSystemSearchLaunchEnabled

            val showInBrowserMenu =
                duckChatFeatureRepository.shouldShowInBrowserMenu() &&
                    isDuckChatFeatureEnabled && isDuckChatUserEnabled
            _showPopupMenuShortcut.emit(showInBrowserMenu)

            val showInAddressBar =
                duckChatFeatureRepository.shouldShowInAddressBar() &&
                    isDuckChatFeatureEnabled && isDuckChatUserEnabled && isAddressBarEntryPointEnabled
            _showOmnibarShortcutOnNtpAndOnFocus.emit(showInAddressBar)

            val showOmnibarShortcutInAllStates = showInAddressBar && isDuckAiInBrowserEnabled
            _showOmnibarShortcutInAllStates.emit(showOmnibarShortcutInAllStates)

            _showNewAddressBarOptionChoiceScreen.emit(showAIChatAddressBarChoiceScreen)

            val showClearChatHistory = clearChatHistory
            _showClearDuckAIChatHistory.emit(showClearChatHistory)

            val showVoiceSearchToggle =
                duckChatFeatureRepository.shouldShowInVoiceSearch() &&
                    isDuckChatFeatureEnabled && isDuckChatUserEnabled && isVoiceSearchEntryPointEnabled
            _showVoiceSearchToggle.emit(showVoiceSearchToggle)

            val showFullScreenMode = isDuckChatFeatureEnabled && isDuckChatUserEnabled &&
                (duckChatFeature.fullscreenMode().isEnabled() || duckChatFeatureRepository.isFullScreenModeUserSettingEnabled())
            isFullscreenModeEnabled = showFullScreenMode
            _showFullScreenMode.emit(showFullScreenMode)

            val showContextualMode = isDuckChatFeatureEnabled && isDuckChatUserEnabled && duckChatFeature.contextualMode().isEnabled()
            isContextualModeEnabled = showContextualMode
            _showContextualMode.emit(showContextualMode)

            isAutomaticContextAttachmentEnabled = showContextualMode &&
                duckChatFeature.automaticContextAttachment()
                    .isEnabled() && duckChatFeatureRepository.isAutomaticPageContextAttachmentUserSettingEnabled()
        }

    companion object {
        private const val DUCK_CHAT_WEB_LINK = "https://duckduckgo.com/?q=DuckDuckGo+AI+Chat&ia=chat&duckai=5"
        private const val DUCKDUCKGO_HOST = "duckduckgo.com"
        private const val DUCK_AI_HOST = "duck.ai"
        private const val CHAT_QUERY_NAME = "ia"
        private const val CHAT_QUERY_VALUE = "chat"
        private const val PROMPT_QUERY_NAME = "prompt"
        private const val PROMPT_QUERY_VALUE = "1"
        private const val PLACEMENT_QUERY_NAME = "placement"
        private const val PLACEMENT_QUERY_VALUE = "sidebar"
        private const val BANG_QUERY_NAME = "bang"
        private const val BANG_QUERY_VALUE = "true"
        private const val DEFAULT_SESSION_ALIVE = 60
        private const val REVOKE_URL = "https://duckduckgo.com/revoke-duckai-access"
    }
}
