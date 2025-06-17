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
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.di.IsMainProcess
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.tabs.BrowserNav
import com.duckduckgo.common.ui.experiments.visual.store.VisualDesignExperimentDataStore
import com.duckduckgo.common.utils.AppUrl.ParamKey.QUERY
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.api.DuckChatSettingsNoParams
import com.duckduckgo.duckchat.impl.feature.AIChatImageUploadFeature
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelParameters
import com.duckduckgo.duckchat.impl.repository.DuckChatFeatureRepository
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
     * Opens DuckChat settings.
     */
    fun openDuckChatSettings()

    /**
     * Closes DuckChat.
     */
    fun closeDuckChat()

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

data class DuckChatSettingJson(
    val aiChatURL: String?,
    val aiChatBangs: List<String>?,
    val aiChatBangRegex: String?,
    val addressBarEntryPoint: Boolean,

)

@SingleInstanceIn(AppScope::class)

@ContributesBinding(AppScope::class, boundType = DuckChat::class)
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
) : DuckChatInternal, PrivacyConfigCallbackPlugin {

    private val _showInBrowserMenu = MutableStateFlow(false)
    private val _showInAddressBar = MutableStateFlow(false)
    private val _chatState = MutableStateFlow(ChatState.HIDE)

    private val jsonAdapter: JsonAdapter<DuckChatSettingJson> by lazy {
        moshi.adapter(DuckChatSettingJson::class.java)
    }

    private var isDuckChatEnabled = false
    private var isDuckChatUserEnabled = false
    private var duckChatLink = DUCK_CHAT_WEB_LINK
    private var bangRegex: Regex? = null
    private var isAddressBarEntryPointEnabled: Boolean = false
    private var isImageUploadEnabled: Boolean = false

    init {
        if (isMainProcess) {
            cacheConfig()
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

    override fun observeEnableDuckChatUserSetting(): Flow<Boolean> {
        return duckChatFeatureRepository.observeDuckChatUserEnabled()
    }

    override fun observeShowInBrowserMenuUserSetting(): Flow<Boolean> {
        return duckChatFeatureRepository.observeShowInBrowserMenu()
    }

    override fun observeShowInAddressBarUserSetting(): Flow<Boolean> {
        return duckChatFeatureRepository.observeShowInAddressBar()
    }

    override fun openDuckChatSettings() {
        // todo what happens with this interaction? closeDuckChat() would go back to browser activity
        val intent = globalActivityStarter.startIntent(context, DuckChatSettingsNoParams)
        intent?.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
        closeDuckChat()
    }

    override fun closeDuckChat() {
        browserNav.closeDuckChat(context).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(this)
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

    override val showInBrowserMenu: StateFlow<Boolean> get() = _showInBrowserMenu.asStateFlow()

    override val showInAddressBar: StateFlow<Boolean> get() = _showInAddressBar.asStateFlow()

    override val chatState: StateFlow<ChatState> get() = _chatState.asStateFlow()

    override fun isImageUploadEnabled(): Boolean = isImageUploadEnabled
    override fun keepSessionIntervalInMinutes(): Int {
        return 60
    }

    override fun openDuckChat(query: String?) {
        val parameters = query?.let { originalQuery ->
            val hasDuckChatBang = isDuckChatBang(originalQuery.toUri())
            val cleanedQuery = if (hasDuckChatBang) {
                stripBang(originalQuery)
            } else {
                originalQuery
            }
            mutableMapOf<String, String>().apply {
                if (cleanedQuery.isNotEmpty()) {
                    put(QUERY, cleanedQuery)
                    if (hasDuckChatBang) {
                        put(BANG_QUERY_NAME, BANG_QUERY_VALUE)
                    }
                }
            }
        } ?: emptyMap()
        openDuckChat(parameters)
    }

    private fun stripBang(query: String): String {
        val bangPattern = Regex("!\\w+")
        return query.replace(bangPattern, "").trim()
    }

    override fun openDuckChatWithAutoPrompt(query: String) {
        val parameters = mapOf(
            QUERY to query,
            PROMPT_QUERY_NAME to PROMPT_QUERY_VALUE,
        )
        openDuckChat(parameters)
    }

    private fun openDuckChat(parameters: Map<String, String>) {
        val url = appendParameters(parameters, duckChatLink)

        appCoroutineScope.launch {
            val sessionDelta = duckChatFeatureRepository.sessionDeltaTimestamp()
            val params = mapOf(DuckChatPixelParameters.DELTA_TIMESTAMP_PARAMETERS to sessionDelta.toString())
            pixel.fire(DuckChatPixelName.DUCK_CHAT_OPEN, parameters = params)

            duckChatFeatureRepository.registerOpened()
        }

        startDuckChatActivity(url)
    }

    private fun startDuckChatActivity(url: String) {
        browserNav.openDuckChat(context, duckChatUrl = url)
            .apply {
                // TODO fix DuckAi POC
                // if (experimentDataStore.isDuckAIPoCEnabled.value && experimentDataStore.isExperimentEnabled.value) {
                //     setClass(context, DuckChatWebViewPoCActivity::class.java)
                // }
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
        }.getOrElse { url }
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

    override suspend fun shouldKeepSessionAlive(): Boolean {
        return duckChatFeatureRepository.lastSessionTimestamp() >= keepSessionIntervalInMinutes() * 60 * 1000
    }

    private fun cacheConfig() {
        appCoroutineScope.launch(dispatchers.io()) {
            isDuckChatEnabled = duckChatFeature.self().isEnabled()

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
            cacheUserSettings()
        }
    }

    private suspend fun cacheUserSettings() = withContext(dispatchers.io()) {
        isDuckChatUserEnabled = duckChatFeatureRepository.isDuckChatUserEnabled()

        val showInBrowserMenu = duckChatFeatureRepository.shouldShowInBrowserMenu() &&
            isDuckChatEnabled && isDuckChatUserEnabled
        _showInBrowserMenu.emit(showInBrowserMenu)

        val showInAddressBar = duckChatFeatureRepository.shouldShowInAddressBar() &&
            isDuckChatEnabled && isDuckChatUserEnabled && isAddressBarEntryPointEnabled
        _showInAddressBar.emit(showInAddressBar)
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
    }
}
