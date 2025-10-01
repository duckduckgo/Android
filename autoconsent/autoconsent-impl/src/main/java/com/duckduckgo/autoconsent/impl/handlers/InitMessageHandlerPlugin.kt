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

package com.duckduckgo.autoconsent.impl.handlers

import android.webkit.WebView
import androidx.core.net.toUri
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.autoconsent.api.AutoconsentCallback
import com.duckduckgo.autoconsent.impl.MessageHandlerPlugin
import com.duckduckgo.autoconsent.impl.adapters.JSONObjectAdapter
import com.duckduckgo.autoconsent.impl.remoteconfig.AutoconsentFeature
import com.duckduckgo.autoconsent.impl.remoteconfig.AutoconsentFeatureModels.AutoconsentSettings
import com.duckduckgo.autoconsent.impl.store.AutoconsentSettingsRepository
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.isHttp
import com.duckduckgo.common.utils.isHttps
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.logcat
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class InitMessageHandlerPlugin @Inject constructor(
    @AppCoroutineScope val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val settingsRepository: AutoconsentSettingsRepository,
    private val autoconsentFeature: AutoconsentFeature,
) : MessageHandlerPlugin {

    private val moshi = Moshi.Builder().add(JSONObjectAdapter()).build()

    override fun process(messageType: String, jsonString: String, webView: WebView, autoconsentCallback: AutoconsentCallback) {
        if (supportedTypes.contains(messageType)) {
            appCoroutineScope.launch(dispatcherProvider.io()) {
                try {
                    val message: InitMessage = parseMessage(jsonString) ?: return@launch
                    val url = message.url
                    val uri = url.toUri()

                    if (!uri.isHttp && !uri.isHttps) {
                        return@launch
                    }

                    // Remove comment to promote feature and remove @Ignore from tests
                    val isAutoconsentDisabled = !settingsRepository.userSetting // && settingsRepository.firstPopupHandled

                    if (isAutoconsentDisabled) {
                        return@launch
                    }

                    // Reset site
                    autoconsentCallback.onResultReceived(consentManaged = false, optOutFailed = false, selfTestFailed = false, isCosmetic = false)

                    val settingsAdapter = moshi.adapter(AutoconsentSettings::class.java)
                    val settingsJson = autoconsentFeature.self().getSettings() ?: return@launch
                    val settings = settingsAdapter.fromJson(settingsJson) ?: return@launch

                    val autoAction = getAutoAction()
                    val enablePreHide = settingsRepository.userSetting
                    val detectRetries = 20
                    val disabledCmps = settings.disabledCMPs
                    val config = Config(enabled = true, autoAction, disabledCmps, enablePreHide, detectRetries, enableCosmeticRules = true)
                    val initResp = InitResp(config = config, rules = AutoconsentRuleset(settings.compactRuleList))

                    val response = ReplyHandler.constructReply(getMessage(initResp))

                    withContext(dispatcherProvider.main()) {
                        webView.evaluateJavascript("javascript:$response", null)
                    }
                } catch (e: Exception) {
                    logcat { e.localizedMessage }
                }
            }
        }
    }

    override val supportedTypes: List<String> = listOf("init")

    private fun getAutoAction(): String {
        // Remove comment to promote feature
        // return if (!settingsRepository.firstPopupHandled) null else "optOut"
        return "optOut"
    }

    private fun parseMessage(jsonString: String): InitMessage? {
        val jsonAdapter: JsonAdapter<InitMessage> = moshi.adapter(InitMessage::class.java)
        return jsonAdapter.fromJson(jsonString)
    }

    private fun getMessage(initResp: InitResp): String {
        val jsonAdapter: JsonAdapter<InitResp> = moshi.adapter(InitResp::class.java).serializeNulls()
        return jsonAdapter.toJson(initResp).toString()
    }

    data class InitMessage(val type: String, val url: String)

    data class Config(
        val enabled: Boolean,
        val autoAction: String?,
        val disabledCmps: List<String>,
        val enablePrehide: Boolean,
        val detectRetries: Int,
        val enableCosmeticRules: Boolean,
    )

    data class AutoconsentRuleset(val compact: Any?)

    data class InitResp(val type: String = "initResp", val config: Config, val rules: AutoconsentRuleset)
}
