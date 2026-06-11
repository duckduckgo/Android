/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.app.browser

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
@SuppressLint("SetJavaScriptEnabled", "RequiresFeature", "AddDocumentStartJavaScriptUsage", "AddWebMessageListenerUsage")
class DocumentStartJavaScriptSmokeTest {

    @Test
    fun whenDocumentStartJavaScriptIsRegisteredThenInlinePageScriptSeesIt() {
        assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))

        val pageLoaded = CountDownLatch(1)
        var documentStartSeenByInlineScript: String? = null

        ActivityScenario.launch(DocumentStartJavaScriptTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val webView = activity.createWebView()
                webView.webViewClient =
                    object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String?) {
                            view.evaluateJavascript("window.__ddgInlineSawDocumentStart === true") { value ->
                                documentStartSeenByInlineScript = value
                                pageLoaded.countDown()
                            }
                        }
                    }
                activity.setContentView(webView)

                WebViewCompat.addDocumentStartJavaScript(
                    webView,
                    "window.__ddgDocumentStartProbe = true;",
                    setOf("*"),
                )

                webView.loadDataWithBaseURL(
                    "https://example.com/",
                    """
                    <!doctype html>
                    <script>
                        window.__ddgInlineSawDocumentStart = window.__ddgDocumentStartProbe === true;
                    </script>
                    """.trimIndent(),
                    "text/html",
                    "UTF-8",
                    null,
                )
            }

            assertTrue(pageLoaded.await(WEBVIEW_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        }

        assertEquals("true", documentStartSeenByInlineScript)
    }

    @Test
    fun whenWebMessageListenerIsRegisteredThenDocumentStartJavaScriptCanPostMessage() {
        assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))
        assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER))

        val messageReceived = CountDownLatch(1)
        var documentStartMessage: String? = null

        ActivityScenario.launch(DocumentStartJavaScriptTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val webView = activity.createWebView()
                activity.setContentView(webView)

                WebViewCompat.addWebMessageListener(
                    webView,
                    CONTENT_SCOPE_OBJECT_NAME,
                    setOf("*"),
                ) { _, message, _, _, _ ->
                    documentStartMessage = message.data
                    messageReceived.countDown()
                }
                WebViewCompat.addDocumentStartJavaScript(
                    webView,
                    """
                    if (window.$CONTENT_SCOPE_OBJECT_NAME) {
                        window.$CONTENT_SCOPE_OBJECT_NAME.postMessage("document-start-message");
                    }
                    """.trimIndent(),
                    setOf("*"),
                )

                webView.loadDataWithBaseURL(
                    "https://example.com/",
                    "<!doctype html><title>document-start smoke</title>",
                    "text/html",
                    "UTF-8",
                    null,
                )
            }

            assertTrue(messageReceived.await(WEBVIEW_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        }

        assertEquals("document-start-message", documentStartMessage)
    }

    private fun DocumentStartJavaScriptTestActivity.createWebView(): WebView {
        return WebView(this).apply {
            settings.javaScriptEnabled = true
        }
    }

    private companion object {
        const val CONTENT_SCOPE_OBJECT_NAME = "contentScopeAdsjs"
        const val WEBVIEW_TIMEOUT_SECONDS = 5L
    }
}
