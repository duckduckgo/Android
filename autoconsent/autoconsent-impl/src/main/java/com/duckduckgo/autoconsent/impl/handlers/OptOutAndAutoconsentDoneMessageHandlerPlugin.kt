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
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.logcat
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class OptOutAndAutoconsentDoneMessageHandlerPlugin @Inject constructor(
    @AppCoroutineScope val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) : MessageHandlerPlugin {

    private val moshi = Moshi.Builder().add(JSONObjectAdapter()).build()
    private var selfTest = false

    override fun process(messageType: String, jsonString: String, webView: WebView, autoconsentCallback: AutoconsentCallback) {
        if (supportedTypes.contains(messageType)) {
            when (messageType) {
                OPT_OUT -> processOptOutResult(jsonString, autoconsentCallback)
                RESULT_MESSAGE -> processAutoconsentDone(jsonString, webView, autoconsentCallback)
                else -> return
            }
        }
    }

    override val supportedTypes: List<String> = listOf(OPT_OUT, RESULT_MESSAGE)

    private fun processOptOutResult(jsonString: String, autoconsentCallback: AutoconsentCallback) {
        try {
            val message: OptOutResultMessage = parseOptOutMessage(jsonString) ?: return

            if (!message.result) {
                autoconsentCallback.onResultReceived(consentManaged = true, optOutFailed = true, selfTestFailed = false, isCosmetic = null)
            } else if (message.scheduleSelfTest) {
                selfTest = true
            }
        } catch (e: Exception) {
            logcat { e.localizedMessage }
        }
    }

    private fun processAutoconsentDone(jsonString: String, webView: WebView, autoconsentCallback: AutoconsentCallback) {
        try {
            val message: AutoconsentDoneMessage = parseAutoconsentDoneMessage(jsonString) ?: return
            message.url.toUri().host ?: return

            autoconsentCallback.onPopUpHandled(message.isCosmetic)
            autoconsentCallback.onResultReceived(consentManaged = true, optOutFailed = false, selfTestFailed = false, isCosmetic = message.isCosmetic)

            if (selfTest) {
                appCoroutineScope.launch(dispatcherProvider.main()) {
                    webView.evaluateJavascript("javascript:${ReplyHandler.constructReply("""{ "type": "selfTest" }""")}", null)
                }
            }
            selfTest = false
        } catch (e: Exception) {
            logcat { e.localizedMessage }
        }
    }

    private fun parseOptOutMessage(jsonString: String): OptOutResultMessage? {
        val jsonAdapter: JsonAdapter<OptOutResultMessage> = moshi.adapter(OptOutResultMessage::class.java)
        return jsonAdapter.fromJson(jsonString)
    }

    private fun parseAutoconsentDoneMessage(jsonString: String): AutoconsentDoneMessage? {
        val jsonAdapter: JsonAdapter<AutoconsentDoneMessage> = moshi.adapter(AutoconsentDoneMessage::class.java)
        return jsonAdapter.fromJson(jsonString)
    }

    data class OptOutResultMessage(val type: String, val cmp: String, val result: Boolean, val scheduleSelfTest: Boolean, val url: String)

    data class AutoconsentDoneMessage(val type: String, val cmp: String, val url: String, val isCosmetic: Boolean)

    companion object {
        const val OPT_OUT = "optOutResult"
        const val RESULT_MESSAGE = "autoconsentDone"
    }
}
