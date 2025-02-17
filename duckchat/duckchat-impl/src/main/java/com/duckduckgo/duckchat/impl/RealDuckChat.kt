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
import com.duckduckgo.browser.api.ui.BrowserScreens.WebViewActivityWithParams
import com.duckduckgo.common.utils.AppUrl.ParamKey.QUERY
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.DuckChat
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

interface DuckChatInternal : DuckChat {
    /**
     * Set user setting to determine whether DuckChat should be shown in browser menu.
     * Sets IO dispatcher.
     */
    suspend fun setShowInBrowserMenuUserSetting(showDuckChat: Boolean)

    /**
     * Observes whether DuckChat should be shown in browser menu based on user settings only.
     */
    fun observeShowInBrowserMenuUserSetting(): Flow<Boolean>
}

data class DuckChatSettingJson(
    val aiChatURL: String,
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

    private val jsonAdapter: JsonAdapter<DuckChatSettingJson> by lazy {
        moshi.adapter(DuckChatSettingJson::class.java)
    }

    /** Cached DuckChat is enabled flag */
    private var isDuckChatEnabled = false

    /** Cached value of whether we should show DuckChat in the menu or not */
    private var showInBrowserMenu = false

    /** Cached DuckChat web link */
    private var duckChatLink = DUCK_CHAT_WEB_LINK

    init {
        if (isMainProcess) {
            cacheDuckChatLink()
            cacheShowInBrowser()
        }
    }

    override fun onPrivacyConfigDownloaded() {
        cacheDuckChatLink()
        cacheShowInBrowser()
    }

    override suspend fun setShowInBrowserMenuUserSetting(showDuckChat: Boolean) = withContext(dispatchers.io()) {
        if (showDuckChat) {
            pixel.fire(DuckChatPixelName.DUCK_CHAT_MENU_SETTING_ON)
        } else {
            pixel.fire(DuckChatPixelName.DUCK_CHAT_MENU_SETTING_OFF)
        }

        duckChatFeatureRepository.setShowInBrowserMenu(showDuckChat)
        cacheShowInBrowser()
    }

    override fun isEnabled(): Boolean {
        return isDuckChatEnabled
    }

    override fun observeShowInBrowserMenuUserSetting(): Flow<Boolean> {
        return duckChatFeatureRepository.observeShowInBrowserMenu()
    }

    override fun showInBrowserMenu(): Boolean {
        return showInBrowserMenu
    }

    override fun openDuckChat(query: String?) {
        val parameters = query?.let {
            mapOf(QUERY to it)
        } ?: emptyMap()
        openDuckChat(parameters)
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
    }

    private fun startDuckChatActivity(url: String) {
        val intent = globalActivityStarter.startIntent(
            context,
            WebViewActivityWithParams(
                url = url,
                screenTitle = context.getString(R.string.duck_chat_title),
                supportNewWindows = true,
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
        if (uri.host != DUCKDUCKGO_HOST) {
            return false
        }
        return runCatching {
            val queryParameters = uri.queryParameterNames
            queryParameters.contains(CHAT_QUERY_NAME) && uri.getQueryParameter(CHAT_QUERY_NAME) == CHAT_QUERY_VALUE
        }.getOrDefault(false)
    }

    private fun cacheDuckChatLink() {
        appCoroutineScope.launch(dispatchers.io()) {
            duckChatLink = duckChatFeature.self().getSettings()?.let {
                runCatching {
                    val settingsJson = jsonAdapter.fromJson(it)
                    settingsJson?.aiChatURL
                }.getOrDefault(DUCK_CHAT_WEB_LINK)
            } ?: DUCK_CHAT_WEB_LINK
        }
    }

    private fun cacheShowInBrowser() {
        appCoroutineScope.launch(dispatchers.io()) {
            isDuckChatEnabled = duckChatFeature.self().isEnabled()
            showInBrowserMenu = duckChatFeatureRepository.shouldShowInBrowserMenu() && isDuckChatEnabled
        }
    }

    companion object {
        /** Default link to DuckChat that identifies Android as the source */
        private const val DUCK_CHAT_WEB_LINK = "https://duckduckgo.com/?q=DuckDuckGo+AI+Chat&ia=chat&duckai=5"
        private const val DUCKDUCKGO_HOST = "duckduckgo.com"
        private const val CHAT_QUERY_NAME = "ia"
        private const val CHAT_QUERY_VALUE = "chat"
        private const val PROMPT_QUERY_NAME = "prompt"
        private const val PROMPT_QUERY_VALUE = "1"
    }
}
