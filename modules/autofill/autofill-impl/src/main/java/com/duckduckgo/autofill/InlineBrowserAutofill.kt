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

package com.duckduckgo.autofill

import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.core.net.toUri
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.utils.ConflatedJob
import com.duckduckgo.autofill.domain.AutofillDataResponse
import com.duckduckgo.autofill.domain.AutofillRequestParser
import com.duckduckgo.autofill.domain.CredentialSuccessResponse
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class InlineBrowserAutofill(val autofillInterface: AutofillJavascriptInterface) : BrowserAutofill {

    val enabled: Boolean = true

    override fun isEnabled(): Boolean {
        Timber.i("BrowserAutofill: Inline browser autofill, isEnabled=%s", enabled)
        return enabled
    }

    override fun addBrowserAutofillInterface(webView: WebView) {
        Timber.v(
            "Injecting BrowserAutofill interface.\nthis class: %s,\nwebView:%s,\ninterface:%s",
            this, webView, autofillInterface
        )
        webView.addJavascriptInterface(autofillInterface, AutofillJavascriptInterface.INTERFACE_NAME)
        autofillInterface.webView = webView
    }

}

class AutofillJavascriptInterface(
    private val moshi: Moshi,
    private val requestParser: AutofillRequestParser,
    @AppCoroutineScope private val coroutineScope: CoroutineScope
) {

    var webView: WebView? = null
    private val getAutofillDataJob = ConflatedJob()

    @JavascriptInterface
    fun getAutofillData(requestString: String) {
        Timber.i("BrowserAutofill: getAutofillData called:\n%s", requestString)
        getAutofillDataJob += coroutineScope.launch {
            val request = requestParser.parseRequest(requestString)
            Timber.i("Parsed request\ninputType: %s\nsubType: %s", request.mainType, request.subType)

            withContext(Dispatchers.Main) {
                webView?.let {
                    WebViewCompat.postWebMessage(it, generateResponse(), "https://privacy-test-pages.glitch.me".toUri())
                }
                // webView?.postWebMessage(generateResponse(), "*".toUri())
            }
        }
    }

    private fun generateResponse(): WebMessageCompat {
        val credentialsResponse = CredentialSuccessResponse("foo", "bar")
        val response = AutofillDataResponse(success = credentialsResponse)
        val adapter = moshi.adapter(AutofillDataResponse::class.java).indent("  ")
        val json = adapter.toJson(response).also { Timber.v("Response:\n$it") }
        return WebMessageCompat(json)
    }

    @JavascriptInterface
    fun getAvailableInputTypes(): String {
        // determined by interrogating the secure storage for the current url
        val credentialsAvailable = true
        return generateAvailableInputTypes(credentialsAvailable).also {
            Timber.i("BrowserAutofill: getAvailableInputTypes called. Responding with\n$it")
        }
    }

    @JavascriptInterface
    fun getRuntimeConfiguration(): String {
        Timber.i("BrowserAutofill: getRuntimeConfiguration called")
        return generateRuntimeConfiguration()
    }

    /**
     * Indicating availability of data for the current user and website
     * E.g., returning credentials: true means we have saved credentials for the current page
     */
    private fun generateAvailableInputTypes(credentialsAvailable: Boolean): String {
        return """
            {
              "success": {
                "credentials": $credentialsAvailable
              }
            }
        """.trimIndent()
    }

    /**
     * contentScope: dump of the most up-to-date privacy remote config, untouched by Android code
     * userUnprotectedDomains: any sites for which the user has chosen to disable privacy protections (leave empty for now)
     */
    private fun generateRuntimeConfiguration(): String {
        return """
            {
              "success": {
                "contentScope": {
                  "features": {
                    "autofill": {
                      "state": "enabled",
                      "exceptions": []
                    }
                  },
                  "unprotectedTemporary": []
                },
                "userUnprotectedDomains": [],
                "userPreferences": {
                  "debug": false,
                  "platform": {
                    "name": "android"
                  },
                  "features": {
                    "autofill": {
                      "settings": {
                        "featureToggles": {
                          "inputType_credentials": true,
                          "inputType_identities": false,
                          "inputType_creditCards": false,
                          "emailProtection": true,
                          "password_generation": false,
                          "credentials_saving": true
                        }
                      }
                    }
                  }
                }
              }
            }
        """.trimIndent()
    }

    companion object {
        const val INTERFACE_NAME = "BrowserAutofill"
    }
}
