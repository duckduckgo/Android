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
import com.duckduckgo.autoconsent.api.AutoconsentCallback
import com.duckduckgo.autoconsent.impl.MessageHandlerPlugin
import com.duckduckgo.autoconsent.impl.adapters.JSONObjectAdapter
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import timber.log.Timber
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class SelfTestResultMessageHandlerPlugin @Inject constructor() : MessageHandlerPlugin {

    private val moshi = Moshi.Builder().add(JSONObjectAdapter()).build()

    override fun process(messageType: String, jsonString: String, webView: WebView, autoconsentCallback: AutoconsentCallback) {
        if (supportedTypes.contains(messageType)) {
            try {
                val message: SelfTestResultMessage = parseMessage(jsonString) ?: return
                autoconsentCallback.onResultReceived(consentManaged = true, optOutFailed = false, selfTestFailed = message.result)
            } catch (e: Exception) {
                Timber.d(e.localizedMessage)
            }
        }
    }

    override val supportedTypes: List<String> = listOf("selfTestResult")

    private fun parseMessage(jsonString: String): SelfTestResultMessage? {
        val jsonAdapter: JsonAdapter<SelfTestResultMessage> = moshi.adapter(SelfTestResultMessage::class.java)
        return jsonAdapter.fromJson(jsonString)
    }

    data class SelfTestResultMessage(val type: String, val cmp: String, val result: Boolean, val url: String)
}
