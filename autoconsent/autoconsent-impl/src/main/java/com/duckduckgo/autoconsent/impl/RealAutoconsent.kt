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
import com.duckduckgo.app.privacy.db.UserAllowListRepository
import com.duckduckgo.autoconsent.api.Autoconsent
import com.duckduckgo.autoconsent.api.AutoconsentCallback
import com.duckduckgo.autoconsent.api.AutoconsentFeatureName
import com.duckduckgo.autoconsent.impl.AutoconsentInterface.Companion.AUTOCONSENT_INTERFACE
import com.duckduckgo.autoconsent.impl.handlers.ReplyHandler
import com.duckduckgo.autoconsent.store.AutoconsentRepository
import com.duckduckgo.autoconsent.store.AutoconsentSettingsRepository
import com.duckduckgo.common.utils.UriString
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.FeatureToggle
import com.duckduckgo.privacy.config.api.UnprotectedTemporary
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class RealAutoconsent @Inject constructor(
    private val messageHandlerPlugins: PluginPoint<MessageHandlerPlugin>,
    private val settingsRepository: AutoconsentSettingsRepository,
    private val autoconsentRepository: AutoconsentRepository,
    private val featureToggle: FeatureToggle,
    private val userAllowlistRepository: UserAllowListRepository,
    private val unprotectedTemporary: UnprotectedTemporary,
) : Autoconsent {

    private lateinit var autoconsentJs: String

    override fun injectAutoconsent(webView: WebView, url: String) {
        if (canBeInjected() && !urlInUserAllowList(url) && !isAnException(url)) {
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
        return featureToggle.isFeatureEnabled(AutoconsentFeatureName.Autoconsent.value)
    }

    private fun isAnException(url: String): Boolean {
        return matches(url) || unprotectedTemporary.isAnException(url)
    }

    private fun matches(url: String): Boolean {
        return autoconsentRepository.exceptions.any { UriString.sameOrSubdomain(url, it.domain) }
    }

    private fun canBeInjected(): Boolean {
        // Remove comment to promote feature
        // return isEnabled() && (settingsRepository.userSetting || !settingsRepository.firstPopupHandled)
        return isEnabled() && settingsRepository.userSetting
    }

    private fun getFunctionsJS(): String {
        if (!this::autoconsentJs.isInitialized) {
            autoconsentJs = JsReader.loadJs("autoconsent-bundle.js")
        }
        return autoconsentJs
    }
}
