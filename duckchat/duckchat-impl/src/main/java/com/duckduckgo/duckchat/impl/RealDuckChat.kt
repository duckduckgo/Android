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
import com.duckduckgo.common.ui.experiments.visual.store.ExperimentalThemingDataStore
import com.duckduckgo.common.utils.AppUrl.ParamKey.QUERY
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.DuckAiFeatureState
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.api.DuckChatSettingsNoParams
import com.duckduckgo.duckchat.impl.feature.AIChatImageUploadFeature
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelParameters
import com.duckduckgo.duckchat.impl.repository.DuckChatFeatureRepository
import com.duckduckgo.duckchat.impl.ui.DuckChatWebViewActivityWithParams
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.privacy.config.api.PrivacyConfigCallbackPlugin
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.logcat

interface DuckChatInternal : DuckChat {
    /**
     * Set user setting to determine whether DuckChat should be enabled or disabled.
     */
    suspend fun setEnableDuckChatUserSetting(enabled: Boolean)

    /**
     * Set user setting to determine whether dedicated Duck.ai input screen with a mode switch should be used.
     */
    suspend fun setInputScreenUserSetting(enabled: Boolean)

    /**
     * Set user setting to determine whether DuckChat should be shown in browser menu.
     */
    suspend fun setShowInBrowserMenuUserSetting(showDuckChat: Boolean)

    /**
     * Set user setting to determine whether DuckChat should be shown in address bar.
     */
    suspend fun setShowInAddressBarUserSetting(showDuckChat: Boolean)

    /**
     * Observes whether DuckChat is user enabled or disabled.
     */
    fun observeEnableDuckChatUserSetting(): Flow<Boolean>

    /**
     * Observes whether Duck.ai input screen with a mode switch is enabled or disabled.
     */
    fun observeInputScreenUserSettingEnabled(): Flow<Boolean>

    /**
     * Observes whether DuckChat should be shown in browser menu based on user settings only.
     */
    fun observeShowInBrowserMenuUserSetting(): Flow<Boolean>

    /**
     * Observes whether DuckChat should be shown in address bar based on user settings only.
     */
    fun observeShowInAddressBarUserSetting(): Flow<Boolean>

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
     * Returns the time a Duck Chat session should be kept alive
     */
    fun keepSessionIntervalInMinutes(): Int

    /**
     * Returns whether dedicated Duck.ai input screen feature is available (its feature flag is enabled).
     */
    fun isInputScreenFeatureAvailable(): Boolean
}

enum class ChatState(val value: String) {
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
        fun fromValue(v: String?): ChatState? =
            entries.firstOrNull { it.value == v }
    }
}

enum class ReportMetric(val metric: String) {
    USER_DID_SUBMIT_PROMPT("userDidSubmitPrompt"),
    USER_DID_SUBMIT_FIRST_PROMPT("userDidSubmitFirstPrompt"),
    USER_DID_OPEN_HISTORY("userDidOpenHistory"),
    USER_DID_SELECT_FIRST_HISTORY_ITEM("userDidSelectFirstHistoryItem"),
    USER_DID_CREATE_NEW_CHAT("userDidCreateNewChat"),
    ;

    companion object {
        fun fromValue(v: String?): ReportMetric? =
            ReportMetric.entries.firstOrNull { it.metric == v }
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
    private val experimentalThemingDataStore: ExperimentalThemingDataStore,
    private val moshi: Moshi,
    private val dispatchers: DispatcherProvider,
    private val globalActivityStarter: GlobalActivityStarter,
    private val context: Context,
    @IsMainProcess private val isMainProcess: Boolean,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val pixel: Pixel,
    private val imageUploadFeature: AIChatImageUploadFeature,
    private val browserNav: BrowserNav,
) : DuckChatInternal, DuckAiFeatureState, PrivacyConfigCallbackPlugin {

    private val closeChatFlow = MutableSharedFlow<Unit>(replay = 0)
    private val _showSettings = MutableStateFlow(false)
    private val _showInputScreen = MutableStateFlow(false)
    private val _showInBrowserMenu = MutableStateFlow(false)
    private val _showInAddressBar = MutableStateFlow(false)
    private val _showOmnibarShortcutInAllStates = MutableStateFlow(false)
    private val _chatState = MutableStateFlow(ChatState.HIDE)
    private val _keepSession = MutableStateFlow(false)

    private val jsonAdapter: JsonAdapter<DuckChatSettingJson> by lazy {
        moshi.adapter(DuckChatSettingJson::class.java)
    }

    private var isDuckChatEnabled = false
    private var isDuckAiInBrowserEnabled = false
    private var duckAiInputScreen = false
    private var isDuckChatUserEnabled = false
    private var duckChatLink = DUCK_CHAT_WEB_LINK
    private var bangRegex: Regex? = null
    private var isAddressBarEntryPointEnabled: Boolean = false
    private var isImageUploadEnabled: Boolean = false
    private var keepSessionAliveInMinutes: Int = DEFAULT_SESSION_ALIVE

    init {
        if (isMainProcess) {
            cacheConfig()
            experimentalThemingDataStore.isSingleOmnibarEnabled.onEach { isExperimentEnabled ->
                if (!isExperimentEnabled) {
                    // the new input screen feature is only available when the visual design experiment is enabled
                    setInputScreenUserSetting(false)
                }
            }.launchIn(appCoroutineScope)
        }
    }

    override fun onPrivacyConfigDownloaded() {
        cacheConfig()
    }

    override suspend fun setEnableDuckChatUserSetting(enabled: Boolean) {
        if (enabled) {
            pixel.fire(DuckChatPixelName.DUCK_CHAT_USER_ENABLED)
        } else {
            pixel.fire(DuckChatPixelName.DUCK_CHAT_USER_DISABLED)
        }
        duckChatFeatureRepository.setDuckChatUserEnabled(enabled)
        cacheUserSettings()
    }

    override suspend fun setInputScreenUserSetting(enabled: Boolean) {
        if (enabled) {
            pixel.fire(DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_ADDRESS_BAR_SETTING_ON)
        } else {
            pixel.fire(DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_ADDRESS_BAR_SETTING_OFF)
        }
        duckChatFeatureRepository.setInputScreenUserSetting(enabled)
        cacheUserSettings()
    }

    override suspend fun setShowInBrowserMenuUserSetting(showDuckChat: Boolean) = withContext(dispatchers.io()) {
        if (showDuckChat) {
            pixel.fire(DuckChatPixelName.DUCK_CHAT_MENU_SETTING_ON)
        } else {
            pixel.fire(DuckChatPixelName.DUCK_CHAT_MENU_SETTING_OFF)
        }
        duckChatFeatureRepository.setShowInBrowserMenu(showDuckChat)
        cacheUserSettings()
    }

    override suspend fun setShowInAddressBarUserSetting(showDuckChat: Boolean) = withContext(dispatchers.io()) {
        if (showDuckChat) {
            pixel.fire(DuckChatPixelName.DUCK_CHAT_SEARCHBAR_SETTING_ON)
        } else {
            pixel.fire(DuckChatPixelName.DUCK_CHAT_SEARCHBAR_SETTING_OFF)
        }
        duckChatFeatureRepository.setShowInAddressBar(showDuckChat)
        cacheUserSettings()
    }

    override fun isEnabled(): Boolean {
        return isDuckChatEnabled
    }

    override fun isInputScreenFeatureAvailable(): Boolean {
        return duckAiInputScreen
    }

    override fun observeEnableDuckChatUserSetting(): Flow<Boolean> {
        return duckChatFeatureRepository.observeDuckChatUserEnabled()
    }

    override fun observeInputScreenUserSettingEnabled(): Flow<Boolean> {
        return duckChatFeatureRepository.observeInputScreenUserSettingEnabled()
    }

    override fun observeShowInBrowserMenuUserSetting(): Flow<Boolean> {
        return duckChatFeatureRepository.observeShowInBrowserMenu()
    }

    override fun observeShowInAddressBarUserSetting(): Flow<Boolean> {
        return duckChatFeatureRepository.observeShowInAddressBar()
    }

    override fun openDuckChatSettings() {
        val intent = globalActivityStarter.startIntent(context, DuckChatSettingsNoParams)
        intent?.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
        appCoroutineScope.launch {
            closeChatFlow.emit(Unit)
        }
    }

    override fun closeDuckChat() {
        if (_keepSession.value) {
            browserNav.closeDuckChat(context).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(this)
            }
        } else {
            appCoroutineScope.launch {
                closeChatFlow.emit(Unit)
            }
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

    override fun isAddressBarEntryPointEnabled(): Boolean {
        return isAddressBarEntryPointEnabled
    }

    override fun isDuckChatUserEnabled(): Boolean {
        return isDuckChatUserEnabled
    }

    override fun updateChatState(state: ChatState) {
        _chatState.value = state
    }

    override val showSettings: StateFlow<Boolean> = _showSettings.asStateFlow()

    override val showInputScreen: StateFlow<Boolean> = _showInputScreen.asStateFlow()

    override val showPopupMenuShortcut: StateFlow<Boolean> = _showInBrowserMenu.asStateFlow()

    override val showOmnibarShortcutOnNtpAndOnFocus: StateFlow<Boolean> = _showInAddressBar.asStateFlow()

    override val showOmnibarShortcutInAllStates: StateFlow<Boolean> = _showOmnibarShortcutInAllStates.asStateFlow()

    override val chatState: StateFlow<ChatState> = _chatState.asStateFlow()

    override fun isImageUploadEnabled(): Boolean = isImageUploadEnabled
    override fun keepSessionIntervalInMinutes() = keepSessionAliveInMinutes

    override fun openDuckChat() {
        logcat { "Duck.ai: openDuckChat" }
        openDuckChat(emptyMap())
    }

    override fun openDuckChatWithAutoPrompt(query: String) {
        logcat { "Duck.ai: openDuckChatWithAutoPrompt query $query" }
        val parameters = addChatParameters(query, autoPrompt = true)
        openDuckChat(parameters, forceNewSession = true)
    }

    override fun openDuckChatWithPrefill(query: String) {
        logcat { "Duck.ai: openDuckChatWithPrefill query $query" }
        val parameters = addChatParameters(query, autoPrompt = false)
        openDuckChat(parameters, forceNewSession = true)
    }

    private fun addChatParameters(
        query: String,
        autoPrompt: Boolean,
    ): Map<String, String> {
        val hasDuckChatBang = isDuckChatBang(query.toUri())
        logcat { "Duck.ai: hasDuckChatBang $hasDuckChatBang" }

        val cleanedQuery = stripBang(query)
        logcat { "Duck.ai: cleaned query $cleanedQuery" }

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
            val sessionDelta = duckChatFeatureRepository.sessionDeltaInMinutes()
            val params = mapOf(DuckChatPixelParameters.DELTA_TIMESTAMP_PARAMETERS to sessionDelta.toString())

            val hasSessionActive = when {
                forceNewSession -> false
                _keepSession.value -> hasActiveSession()
                else -> false
            }

            duckChatFeatureRepository.registerOpened()

            withContext(dispatchers.main()) {
                pixel.fire(DuckChatPixelName.DUCK_CHAT_OPEN, parameters = params)
                if (_keepSession.value) {
                    logcat { "Duck.ai: restoring Duck.ai session $url hasSessionActive $hasSessionActive" }
                    openDuckChatSession(url, hasSessionActive)
                } else {
                    logcat { "Duck.ai: opening standalone Duck.ai screen $url" }
                    startDuckChatActivity(url)
                }
            }
        }
    }

    private fun openDuckChatSession(
        url: String,
        hasSessionActive: Boolean,
    ) {
        // if a new query was submitted we force a new session
        // we want to lose the context of the previous one if the user wanted a new query from outside Duck.ai
        browserNav.openDuckChat(context, duckChatUrl = url, hasSessionActive = hasSessionActive)
            .apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(this)
            }
    }

    private fun startDuckChatActivity(url: String) {
        globalActivityStarter
            .startIntent(context, DuckChatWebViewActivityWithParams(url))
            ?.apply {
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
            uri.buildUpon().apply {
                clearQuery()
                parameters.forEach { (key, value) ->
                    appendQueryParameter(key, value)
                }
                uri.queryParameterNames
                    .filterNot { it in parameters.keys }
                    .forEach { appendQueryParameter(it, uri.getQueryParameter(it)) }
            }.build().toString()
        }.getOrElse {
            logcat { "Duck.ai: parameters $url" }
            url
        }
    }

    override fun isDuckChatUrl(uri: Uri): Boolean {
        if (isDuckChatBang(uri)) return true

        if (uri.host != DUCKDUCKGO_HOST) {
            return false
        }
        return runCatching {
            val queryParameters = uri.queryParameterNames
            queryParameters.contains(CHAT_QUERY_NAME) && uri.getQueryParameter(CHAT_QUERY_NAME) == CHAT_QUERY_VALUE
        }.getOrDefault(false)
    }

    private fun isDuckChatBang(uri: Uri): Boolean {
        return bangRegex?.containsMatchIn(uri.toString()) == true
    }

    override suspend fun wasOpenedBefore(): Boolean {
        return duckChatFeatureRepository.wasOpenedBefore()
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
            isDuckChatEnabled = featureEnabled
            _showSettings.value = featureEnabled
            isDuckAiInBrowserEnabled = duckChatFeature.duckAiButtonInBrowser().isEnabled()
            duckAiInputScreen = duckChatFeature.duckAiInputScreen().isEnabled()

            val settingsString = duckChatFeature.self().getSettings()
            val settingsJson = settingsString?.let {
                runCatching { jsonAdapter.fromJson(it) }.getOrNull()
            }

            duckChatLink = settingsJson?.aiChatURL ?: DUCK_CHAT_WEB_LINK
            settingsJson?.aiChatBangs?.takeIf { it.isNotEmpty() }
                ?.let { bangs ->
                    val bangAlternation = bangs.joinToString("|") { it }
                    bangRegex = settingsJson.aiChatBangRegex?.replace("{bangs}", bangAlternation)?.toRegex()
                }
            isAddressBarEntryPointEnabled = settingsJson?.addressBarEntryPoint ?: false
            isImageUploadEnabled = imageUploadFeature.self().isEnabled()

            _keepSession.value = duckChatFeature.keepSession().isEnabled()
            keepSessionAliveInMinutes = settingsJson?.sessionTimeoutMinutes ?: DEFAULT_SESSION_ALIVE

            cacheUserSettings()
        }
    }

    private suspend fun cacheUserSettings() = withContext(dispatchers.io()) {
        isDuckChatUserEnabled = duckChatFeatureRepository.isDuckChatUserEnabled()

        val showInputScreen = isInputScreenFeatureAvailable() && isDuckChatEnabled && isDuckChatUserEnabled &&
            experimentalThemingDataStore.isSingleOmnibarEnabled.value && duckChatFeatureRepository.isInputScreenUserSettingEnabled()
        _showInputScreen.emit(showInputScreen)

        val showInBrowserMenu = duckChatFeatureRepository.shouldShowInBrowserMenu() &&
            isDuckChatEnabled && isDuckChatUserEnabled
        _showInBrowserMenu.emit(showInBrowserMenu)

        val showInAddressBar = duckChatFeatureRepository.shouldShowInAddressBar() &&
            isDuckChatEnabled && isDuckChatUserEnabled && isAddressBarEntryPointEnabled
        _showInAddressBar.emit(showInAddressBar)

        val showOmnibarShortcutInAllStates = showInAddressBar && isDuckAiInBrowserEnabled
        _showOmnibarShortcutInAllStates.emit(showOmnibarShortcutInAllStates)
    }

    companion object {
        private const val DUCK_CHAT_WEB_LINK = "https://duckduckgo.com/?q=DuckDuckGo+AI+Chat&ia=chat&duckai=5"
        private const val DUCKDUCKGO_HOST = "duckduckgo.com"
        private const val CHAT_QUERY_NAME = "ia"
        private const val CHAT_QUERY_VALUE = "chat"
        private const val PROMPT_QUERY_NAME = "prompt"
        private const val PROMPT_QUERY_VALUE = "1"
        private const val BANG_QUERY_NAME = "bang"
        private const val BANG_QUERY_VALUE = "true"
        private const val DEFAULT_SESSION_ALIVE = 60
    }
}
