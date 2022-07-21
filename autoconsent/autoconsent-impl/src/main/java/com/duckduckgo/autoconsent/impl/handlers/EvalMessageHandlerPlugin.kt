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
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.autoconsent.impl.MessageHandlerPlugin
import com.duckduckgo.autoconsent.impl.adapters.JSONObjectAdapter
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class EvalMessageHandlerPlugin @Inject constructor(
    @AppCoroutineScope val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider
) : MessageHandlerPlugin {

    private val moshi = Moshi.Builder().add(JSONObjectAdapter()).build()

    override fun process(messageType: String, jsonString: String, webView: WebView) {
        if (messageType == type) {
            appCoroutineScope.launch(dispatcherProvider.main()) {
                try {
                    Timber.d("MARCOS receiving init and responding")
                    val message: EvalMessage = parseMessage(jsonString) ?: return@launch
                    var result = false

                    webView.evaluateJavascript(script("42 == 42")) {
                        result = it.toBoolean()
                    }

                    val evalResp = EvalResp(id = message.id, result = result)
                    val response = ReplyHandler.constructReply(getMessage(evalResp))

                    Timber.d("MARCOS message is $response")
                    webView.evaluateJavascript("javascript:$response", null)
                } catch (e: Exception) {
                    Timber.d("MARCOS exception is ${e.localizedMessage}")
                }
            }
        }
    }

    private fun script(code: String): String {
        return """
        (() => {
        try {
            return !!(\($code))
        } catch (e) {
          // ignore CSP errors
          return;
        }
        })();
        """.trimIndent()
    }

    override val type: String = "eval"

    private fun parseMessage(jsonString: String): EvalMessage? {
        val jsonAdapter: JsonAdapter<EvalMessage> = moshi.adapter(EvalMessage::class.java)
        return jsonAdapter.fromJson(jsonString)
    }

    private fun getMessage(initResp: EvalResp): String {
        val jsonAdapter: JsonAdapter<EvalResp> = moshi.adapter(EvalResp::class.java)
        return jsonAdapter.toJson(initResp).toString()
    }

    data class EvalMessage(val type: String, val id: String, val code: String)

    data class EvalResp(val type: String = "evalResp", val id: String, val result: Boolean)
}
