/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.autoconsent.impl

import android.webkit.WebView
import com.duckduckgo.app.browser.UriString
import com.duckduckgo.app.di.IsMainProcess
import com.duckduckgo.app.privacy.db.UserAllowListRepository
import com.duckduckgo.autoconsent.api.Autoconsent
import com.duckduckgo.autoconsent.api.AutoconsentCallback
import com.duckduckgo.autoconsent.impl.AutoconsentInterface.Companion.AUTOCONSENT_INTERFACE
import com.duckduckgo.autoconsent.impl.adapters.JSONObjectAdapter
import com.duckduckgo.autoconsent.impl.handlers.ReplyHandler
import com.duckduckgo.autoconsent.impl.remoteconfig.AutoconsentExceptionsRepository
import com.duckduckgo.autoconsent.impl.remoteconfig.AutoconsentFeature
import com.duckduckgo.autoconsent.impl.remoteconfig.AutoconsentFeatureModels.AutoconsentSettings
import com.duckduckgo.autoconsent.impl.store.AutoconsentSettingsRepository
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.config.api.PrivacyConfigCallbackPlugin
import com.duckduckgo.privacy.config.api.UnprotectedTemporary
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.moshi.Moshi
import javax.inject.Inject

object SettingsCache {
    var hash = 0
    var settings: AutoconsentSettings? = null
}

@ContributesBinding(
    scope = AppScope::class,
    boundType = Autoconsent::class,
)
@ContributesMultibinding(
    scope = AppScope::class,
    boundType = PrivacyConfigCallbackPlugin::class,
)
class RealAutoconsent @Inject constructor(
    private val messageHandlerPlugins: PluginPoint<MessageHandlerPlugin>,
    private val settingsRepository: AutoconsentSettingsRepository,
    private val autoconsentExceptionsRepository: AutoconsentExceptionsRepository,
    private val autoconsent: AutoconsentFeature,
    private val userAllowlistRepository: UserAllowListRepository,
    private val unprotectedTemporary: UnprotectedTemporary,
    @IsMainProcess isMainProcess: Boolean,
) : Autoconsent, PrivacyConfigCallbackPlugin {

    private lateinit var autoconsentJs: String

    init {
        if (isMainProcess) {
            onPrivacyConfigDownloaded()
        }
    }

    override fun injectAutoconsent(webView: WebView, url: String) {
        if (isAutoconsentEnabled() && !urlInUserAllowList(url) && !isAnException(url)) {
            webView.evaluateJavascript("javascript:${getFunctionsJS()}", null)
        }
    }

    override fun addJsInterface(webView: WebView, autoconsentCallback: AutoconsentCallback) {
        webView.addJavascriptInterface(
            AutoconsentInterface(messageHandlerPlugins, webView, autoconsentCallback),
            AUTOCONSENT_INTERFACE,
        )
    }

    override fun changeSetting(setting: Boolean) {
        settingsRepository.userSetting = setting
    }

    override fun isSettingEnabled(): Boolean {
        return settingsRepository.userSetting
    }

    override fun isAutoconsentEnabled(): Boolean {
        return isEnabled() && isSettingEnabled()
    }

    override fun setAutoconsentOptOut(webView: WebView) {
        settingsRepository.userSetting = true
        webView.evaluateJavascript("javascript:${ReplyHandler.constructReply("""{ "type": "optOut" }""")}", null)
    }

    override fun setAutoconsentOptIn() {
        settingsRepository.userSetting = false
    }

    override fun firstPopUpHandled() {
        settingsRepository.firstPopupHandled = true
    }

    private fun urlInUserAllowList(url: String): Boolean {
        return try {
            userAllowlistRepository.isUrlInUserAllowList(url)
        } catch (e: Exception) {
            false
        }
    }

    private fun isEnabled(): Boolean {
        return autoconsent.self().isEnabled()
    }

    private fun isAnException(url: String): Boolean {
        return matches(url) || unprotectedTemporary.isAnException(url)
    }

    private fun matches(url: String): Boolean {
        return autoconsentExceptionsRepository.exceptions.any { UriString.sameOrSubdomain(url, it.domain) }
    }

    private fun getFunctionsJS(): String {
        if (!this::autoconsentJs.isInitialized) {
            autoconsentJs = JsReader.loadJs("autoconsent-bundle.js")
        }
        return autoconsentJs
    }

    override fun onPrivacyConfigDownloaded() {
        settingsRepository.invalidateCache()
        val settingsJson = autoconsent.self().getSettings()
        if (SettingsCache.hash != settingsJson.hashCode()) {
            settingsJson?.let {
                updateSettingsCache(it)
            }
        }
    }

    companion object {
        fun updateSettingsCache(settingsJson: String) {
            val moshi = Moshi.Builder().add(JSONObjectAdapter()).build()
            val settingsAdapter = moshi.adapter(AutoconsentSettings::class.java)
            SettingsCache.settings = settingsAdapter.fromJson(settingsJson)
            SettingsCache.hash = settingsJson.hashCode()
        }
    }
}
