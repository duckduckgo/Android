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

package com.duckduckgo.autoconsent.impl.handlers

import android.webkit.WebView
import com.duckduckgo.autoconsent.api.AutoconsentCallback
import com.duckduckgo.autoconsent.impl.MessageHandlerPlugin
import com.duckduckgo.autoconsent.impl.adapters.JSONObjectAdapter
import com.duckduckgo.autoconsent.impl.pixels.AutoConsentPixel
import com.duckduckgo.autoconsent.impl.pixels.AutoconsentPixelManager
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import logcat.logcat
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class ReportMessageHandlerPlugin @Inject constructor(
    private val autoconsentPixelManager: AutoconsentPixelManager,
) : MessageHandlerPlugin {

    private val moshi = Moshi.Builder().add(JSONObjectAdapter()).build()

    override fun process(messageType: String, jsonString: String, webView: WebView, autoconsentCallback: AutoconsentCallback) {
        if (supportedTypes.contains(messageType)) {
            try {
                val message: ReportMessage = parseMessage(jsonString) ?: return

                val heuristicMatch = message.state.heuristicPatterns.isNotEmpty() || message.state.heuristicSnippets.isNotEmpty()
                val hasDetectedPopups = message.state.detectedPopups.isNotEmpty()

                if (heuristicMatch && !autoconsentPixelManager.isDetectedByPatternsProcessed(message.instanceId)) {
                    autoconsentPixelManager.markDetectedByPatternsProcessed(message.instanceId)
                    autoconsentPixelManager.fireDailyPixel(AutoConsentPixel.AUTOCONSENT_DETECTED_BY_PATTERNS_DAILY)
                }

                if (hasDetectedPopups) {
                    if (heuristicMatch && !autoconsentPixelManager.isDetectedByBothProcessed(message.instanceId)) {
                        autoconsentPixelManager.markDetectedByBothProcessed(message.instanceId)
                        autoconsentPixelManager.fireDailyPixel(AutoConsentPixel.AUTOCONSENT_DETECTED_BY_BOTH_DAILY)
                    } else if (!heuristicMatch && !autoconsentPixelManager.isDetectedOnlyRulesProcessed(message.instanceId)) {
                        autoconsentPixelManager.markDetectedOnlyRulesProcessed(message.instanceId)
                        autoconsentPixelManager.fireDailyPixel(AutoConsentPixel.AUTOCONSENT_DETECTED_ONLY_RULES_DAILY)
                    }
                }
            } catch (e: Exception) {
                logcat { e.localizedMessage }
            }
        }
    }

    override val supportedTypes: List<String> = listOf("report")

    private fun parseMessage(jsonString: String): ReportMessage? {
        val jsonAdapter: JsonAdapter<ReportMessage> = moshi.adapter(ReportMessage::class.java)
        return jsonAdapter.fromJson(jsonString)
    }

    data class ReportState(
        val lifecycle: String,
        val detectedCmps: List<String>,
        val heuristicPatterns: List<String>,
        val heuristicSnippets: List<String>,
        val detectedPopups: List<String>,
    )

    data class ReportMessage(
        val type: String,
        val instanceId: String,
        val state: ReportState,
        val url: String,
    )
}
