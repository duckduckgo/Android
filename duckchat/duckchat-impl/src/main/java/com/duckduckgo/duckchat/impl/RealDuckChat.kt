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
import com.duckduckgo.common.utils.AppUrl.ParamKey.QUERY
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.api.DuckChatSettingsNoParams
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

interface DuckChatInternal : DuckChat {
    /**
     * Set user setting to determine whether DuckChat should be shown in browser menu.
     * Sets IO dispatcher.
     */
    suspend fun setShowInBrowserMenuUserSetting(showDuckChat: Boolean)

    /**
     * Set user setting to determine whether DuckChat should be shown in address bar.
     * Sets IO dispatcher.
     */
    suspend fun setShowInAddressBarUserSetting(showDuckChat: Boolean)

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
     * Calls onClose when a close event is emitted.
     */
    fun observeCloseEvent(lifecycleOwner: LifecycleOwner, onClose: () -> Unit)

    /**
     * Returns whether address bar entry point is enabled or not.
     */
    fun isAddressBarEntryPointEnabled(): Boolean
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
) : DuckChatInternal, PrivacyConfigCallbackPlugin {

    private val closeChatFlow = MutableSharedFlow<Unit>(replay = 0)

    private val jsonAdapter: JsonAdapter<DuckChatSettingJson> by lazy {
        moshi.adapter(DuckChatSettingJson::class.java)
    }

    private var isDuckChatEnabled = false
    private var showInBrowserMenu = false
    private var showInAddressBar = false
    private var duckChatLink = DUCK_CHAT_WEB_LINK
    private var bangRegex: Regex? = null
    private var isAddressBarEntryPointEnabled: Boolean = false

    init {
        if (isMainProcess) {
            cacheConfig()
        }
    }

    override fun onPrivacyConfigDownloaded() {
        cacheConfig()
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
        closeDuckChat()
    }

    override fun closeDuckChat() {
        appCoroutineScope.launch {
            closeChatFlow.emit(Unit)
        }
    }

    override fun observeCloseEvent(lifecycleOwner: LifecycleOwner, onClose: () -> Unit) {
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

    override fun showInBrowserMenu(): Boolean {
        return showInBrowserMenu
    }

    override fun showInAddressBar(): Boolean {
        return showInAddressBar && isAddressBarEntryPointEnabled
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
        startDuckChatActivity(url)
        appCoroutineScope.launch {
            duckChatFeatureRepository.registerOpened()
        }
    }

    private fun startDuckChatActivity(url: String) {
        val intent = globalActivityStarter.startIntent(
            context,
            DuckChatWebViewActivityWithParams(
                url = url,
            ),
        )
        intent?.let {
            it.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(it)
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
            cacheUserSettings()
        }
    }

    private suspend fun cacheUserSettings() = withContext(dispatchers.io()) {
        showInBrowserMenu = duckChatFeatureRepository.shouldShowInBrowserMenu() && isDuckChatEnabled
        showInAddressBar = duckChatFeatureRepository.shouldShowInAddressBar() && isDuckChatEnabled
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
