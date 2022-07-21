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
import androidx.annotation.UiThread
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

interface AutoconsentMessageHandler {
    @UiThread
    fun initResp(webView: WebView)
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealAutoconsentMessageHandler @Inject constructor(
    @AppCoroutineScope val appCoroutineScope: CoroutineScope,
    val dispatcher: CoroutineDispatcher
) : AutoconsentMessageHandler {

    private lateinit var rules: String

    override fun initResp(webView: WebView) {
        appCoroutineScope.launch(Dispatchers.Main) {
            try {
                Timber.d("MARCOS receiving init and responding")
                webView.evaluateJavascript("javascript:${initRespJs()}", null)
            } catch (e: Exception) {
                Timber.d("MARCOS exception is ${e.localizedMessage}")
            }
        }
    }

    private fun selftTest(): String {
        return """
            (function() {
                window.autoconsentMessageCallback({type: 'selfTest'}, window.origin);
            })();
        """.trimIndent()
    }

    private fun initRespJs(): String {
        return """
            (function() {
                window.autoconsentMessageCallback({type: "initResp", rules: ${getRules()}, config: {"enabled": true, "autoAction": true, "disabledCmps": false, "enablePrehide": true}}, window.origin);
            })();
        """.trimIndent()
    }

    private fun getRules(): String {
        if (!this::rules.isInitialized) {
            rules = JsReader.loadJs("rules.json")
        }
        return rules
    }
}
