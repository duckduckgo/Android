/*
 * Copyright (c) 2018 DuckDuckGo
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

import android.net.Uri
import android.support.test.InstrumentationRegistry
import android.support.test.annotation.UiThreadTest
import android.webkit.WebResourceRequest
import android.webkit.WebView
import com.duckduckgo.app.analyticsSurrogates.AnalyticsSurrogates
import com.duckduckgo.app.analyticsSurrogates.SurrogateResponse
import com.duckduckgo.app.httpsupgrade.HttpsUpgrader
import com.duckduckgo.app.trackerdetection.Client
import com.duckduckgo.app.trackerdetection.TrackerDetector
import com.duckduckgo.app.trackerdetection.model.ResourceType
import com.duckduckgo.app.trackerdetection.model.TrackingEvent
import org.junit.Before
import org.mockito.MockitoAnnotations

class WebViewRequestInterceptorTest {

    private lateinit var testee: WebViewRequestInterceptor

    private lateinit var stubRequestRewriter: RequestRewriter
    private lateinit var stubTrackDetector: TrackerDetector
    private lateinit var stubHttpsUpgrader: HttpsUpgrader
    private lateinit var stubAnalyticsSurrogates: AnalyticsSurrogates

    private lateinit var webView: WebView

    @UiThreadTest
    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)

        stubTrackDetector = object: TrackerDetector {
            private val clients: MutableList<Client> = mutableListOf()

            override fun addClient(client: Client) {
                clients.add(client)
            }

            override fun evaluate(url: String, documentUrl: String, resourceType: ResourceType): TrackingEvent? {
                return null
            }
        }

        stubHttpsUpgrader = object: HttpsUpgrader {
            override fun shouldUpgrade(uri: Uri): Boolean {
                return false
            }
        }

        stubAnalyticsSurrogates = object: AnalyticsSurrogates {
            override fun loadSurrogates(urls: List<SurrogateResponse>) {
                // do nothing
            }

            override fun get(uri: Uri): SurrogateResponse {
                return if(uri.toString().contains("good")) {
                    SurrogateResponse(name = "foo.com", mimeType = "application/javascript", jsFunction = "")
                } else {
                    SurrogateResponse(responseAvailable = false)
                }
            }

            override fun getAll(): List<SurrogateResponse> {
                return emptyList()
            }
        }

        stubRequestRewriter = object: RequestRewriter{
            override fun addCustomQueryParams(builder: Uri.Builder) {}
            override fun shouldRewriteRequest(uri: Uri): Boolean = true
            override fun rewriteRequestWithCustomQueryParams(request: Uri): Uri = request
        }

        testee = WebViewRequestInterceptor(
                trackerDetector = stubTrackDetector,
                httpsUpgrader = stubHttpsUpgrader,
                analyticsSurrogates = stubAnalyticsSurrogates
        )

        val context = InstrumentationRegistry.getTargetContext()

        webView = WebView(context)
    }

    private fun getRequest(url: String, forMainFrame: Boolean): WebResourceRequest {
        return object : WebResourceRequest {

            override fun isRedirect(): Boolean = false

            override fun getMethod(): String = "GET"

            override fun getRequestHeaders(): MutableMap<String, String> = mutableMapOf()

            override fun hasGesture(): Boolean = false

            override fun isForMainFrame(): Boolean = forMainFrame

            override fun getUrl(): Uri = Uri.parse(url)

        }
    }
}