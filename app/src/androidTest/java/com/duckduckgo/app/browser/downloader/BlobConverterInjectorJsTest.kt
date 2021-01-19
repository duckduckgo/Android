/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.app.browser.downloader

import android.webkit.WebView
import androidx.test.annotation.UiThreadTest
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.browser.R
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.verify
import org.junit.Before
import org.junit.Test

class BlobConverterInjectorJsTest {
    lateinit var testee: BlobConverterInjectorJs
    private val blobUrl = "blob:https://example.com/283nasdho23jkasdAjd"
    private val contentType = "application/plain"

    @Before
    fun setup() {
        testee = BlobConverterInjectorJs()
    }

    @UiThreadTest
    @Test
    fun whenConvertBlobIntoDataUriAndDownloadThenInjectJsCode() {
        val jsToEvaluate = getJsToEvaluate().replace("%blobUrl%", blobUrl).replace("%contentType%", contentType)
        val webView = spy(WebView(InstrumentationRegistry.getInstrumentation().targetContext))

        testee.convertBlobIntoDataUriAndDownload(webView, blobUrl, contentType)

        verify(webView).evaluateJavascript(jsToEvaluate, null)
    }

    private fun getJsToEvaluate(): String {
        val js = InstrumentationRegistry.getInstrumentation().targetContext.resources.openRawResource(
            R.raw.blob_converter
        )
            .bufferedReader()
            .use { it.readText() }
        return "javascript:$js"
    }
}
