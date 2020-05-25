/*
 * Copyright (c) 2020 DuckDuckGo
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
import android.webkit.WebResourceRequest
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginDetectorTest {
    @Test
    fun evaluate() {
        val loginDetector = LoginDetector()
        val webResourceRequest = SimpleWebResourceRequest("https://mobile.twitter.com/sessions")
        assertTrue(loginDetector.interceptPost(webResourceRequest))
    }

    @Test
    fun evaluate2() {
        val loginDetector = LoginDetector()
        val webResourceRequest =
            SimpleWebResourceRequest("https://accounts.google.com/_/signin/challenge?hl=es&TL=AM3QAYaHUODLXx3ybvJJ2oGBqXFvnaEgrdVVkXN3cWywNkFtwJ8fIpu1loVwN-bx&_reqid=138085&rt=j")
        assertTrue(loginDetector.interceptPost(webResourceRequest))
    }

    class SimpleWebResourceRequest(val url: String) : WebResourceRequest {
        override fun getUrl(): Uri {
            return Uri.parse(url)
        }

        override fun isRedirect(): Boolean {
            TODO("Not yet implemented")
        }

        override fun getMethod(): String {
            return "POST"
        }

        override fun getRequestHeaders(): MutableMap<String, String> {
            TODO("Not yet implemented")
        }

        override fun hasGesture(): Boolean {
            TODO("Not yet implemented")
        }

        override fun isForMainFrame(): Boolean {
            TODO("Not yet implemented")
        }
    }
}