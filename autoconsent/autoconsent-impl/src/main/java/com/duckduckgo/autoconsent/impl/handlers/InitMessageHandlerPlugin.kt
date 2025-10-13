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
import com.duckduckgo.autoconsent.impl.cache.AutoconsentSettingsCache
import com.duckduckgo.autoconsent.impl.remoteconfig.AutoconsentFeature
import com.duckduckgo.autoconsent.impl.remoteconfig.AutoconsentFeatureModels.CompactRules
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
    private val settingsCache: AutoconsentSettingsCache,
    private val autoconsentFeature: AutoconsentFeature,
) : MessageHandlerPlugin {

    private val moshi = Moshi.Builder().add(JSONObjectAdapter()).build()

    override fun process(
        messageType: String,
        jsonString: String,
        webView: WebView,
        autoconsentCallback: AutoconsentCallback,
    ) {
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

                    val settings = settingsCache.getSettings() ?: return@launch
                    val autoAction = getAutoAction()
                    val enablePreHide = settingsRepository.userSetting
                    val detectRetries = 20
                    val disabledCmps = settings.disabledCMPs
                    val config = Config(enabled = true, autoAction, disabledCmps, enablePreHide, detectRetries, enableCosmeticRules = true)
                    val initResp = if (autoconsentFeature.ruleFiltering().isEnabled()) {
                        InitResp(config = config, rules = filterCompactRules(settings.compactRuleList, url))
                    } else {
                        InitResp(config = config, rules = AutoconsentRuleset(settings.compactRuleList))
                    }

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

    private fun filterCompactRules(
        rules: CompactRules,
        url: String,
    ): AutoconsentRuleset {
        // If rule format is unsupported, send an empty ruleset.
        if (rules.v > MAX_SUPPORTED_RULES_VERSION || rules.r == null || rules.s == null || rules.r.isEmpty()) {
            return AutoconsentRuleset(compact = CompactRules(v = MAX_SUPPORTED_RULES_VERSION, s = emptyList(), r = emptyList(), index = null))
        }

        // if an index is available, we can use it to filter more efficiently.
        if (rules.index != null) {
            val genericRules = rules.r.slice(IntRange(rules.index.genericRuleRange[0], rules.index.genericRuleRange[1] - 1))
            val specificRules = rules.r.slice(IntRange(rules.index.specificRuleRange[0], rules.index.specificRuleRange[1] - 1)).filter {
                (it[0] as Double).toInt() <= MAX_SUPPORTED_STEP_VERSION &&
                    (it[4] as Double).toInt() != 1 &&
                    (it[3] == "" || url.matches((it[3] as String).toRegex()))
            }
            if (specificRules.isEmpty()) {
                // no specific rules, return generic rules + strings up to genericStringEnd
                return AutoconsentRuleset(
                    compact = CompactRules(
                        v = rules.v,
                        s = rules.s.slice(IntRange(0, rules.index.genericStringEnd - 1)),
                        r = genericRules,
                        index = null,
                    ),
                )
            }
            // combine generic and specific rules, then filter out strings after genericStringEnd that are not used by matched specificRules.
            val filteredRules = genericRules + specificRules
            val filteredStrings = filterUnusedStrings(specificRules, rules.s, rules.index.genericStringEnd)
            return AutoconsentRuleset(compact = CompactRules(v = rules.v, s = filteredStrings, r = filteredRules, index = null))
        }
        // No index: run rule and string filtering over the entire ruleset.
        val filteredRules = rules.r.filter {
            (it[0] as Double).toInt() <= MAX_SUPPORTED_STEP_VERSION &&
                (it[4] as Double).toInt() != 1 &&
                (it[3] == "" || url.matches((it[3] as String).toRegex()))
        }
        val filteredStrings = filterUnusedStrings(filteredRules, rules.s, 0)
        return AutoconsentRuleset(compact = CompactRules(v = rules.v, s = filteredStrings, r = filteredRules, index = null))
    }

    private fun filterUnusedStrings(
        rules: List<List<Any>>,
        strings: List<String>,
        offset: Int = 0,
    ): List<String> {
        val usedStringIndices = HashSet<Int>()
        val shortKeys = arrayOf("v", "e", "c", "h", "k", "cc", "w", "wv")
        val nestedKeys = arrayOf("then", "else", "any")
        fun addStringIdsFromRuleSteps(steps: List<Map<String, Any>>) {
            for (s in steps) {
                for (k in shortKeys) {
                    if (s.contains(k)) usedStringIndices.add((s[k] as Double).toInt())
                }
                if (s.contains("if")) {
                    addStringIdsFromRuleSteps(listOf(s["if"] as Map<String, Any>))
                }
                for (k in nestedKeys) {
                    if (s.contains(k)) {
                        addStringIdsFromRuleSteps(s[k] as List<Map<String, Any>>)
                    }
                }
            }
        }
        rules.forEach {
            addStringIdsFromRuleSteps(it[6] as List<Map<String, Any>>)
            addStringIdsFromRuleSteps(it[7] as List<Map<String, Any>>)
            addStringIdsFromRuleSteps(it[8] as List<Map<String, Any>>)
            addStringIdsFromRuleSteps(it[9] as List<Map<String, Any>>)
            (it[5] as List<Int>).forEach { usedStringIndices.add(it) }
        }
        return strings.slice(IntRange(start = 0, endInclusive = usedStringIndices.max())).mapIndexed { index, str ->
            if (index <= offset || usedStringIndices.contains(index)) str else ""
        }
    }

    data class InitMessage(
        val type: String,
        val url: String,
    )

    data class Config(
        val enabled: Boolean,
        val autoAction: String?,
        val disabledCmps: List<String>,
        val enablePrehide: Boolean,
        val detectRetries: Int,
        val enableCosmeticRules: Boolean,
    )

    data class AutoconsentRuleset(val compact: Any?)

    data class InitResp(
        val type: String = "initResp",
        val config: Config,
        val rules: AutoconsentRuleset,
    )

    companion object {
        const val MAX_SUPPORTED_RULES_VERSION = 1
        const val MAX_SUPPORTED_STEP_VERSION = 1
    }
}
