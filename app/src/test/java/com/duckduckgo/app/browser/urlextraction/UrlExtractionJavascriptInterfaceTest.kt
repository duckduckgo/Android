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

package com.duckduckgo.app.browser.urlextraction

import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class UrlExtractionJavascriptInterfaceTest {

    @Test
    fun whenUrlExtractedThenInvokeCallbackWithUrl() {
        val onUrlExtracted = mock<(extractedUrl: String?) -> Unit>()
        val urlExtractionInterface = UrlExtractionJavascriptInterface(onUrlExtracted)

        urlExtractionInterface.urlExtracted("example.com")

        verify(onUrlExtracted).invoke("example.com")
    }

    @Test
    fun whenUrlIsUndefinedThenInvokeCallbackWithNull() {
        val onUrlExtracted = mock<(extractedUrl: String?) -> Unit>()
        val urlExtractionInterface = UrlExtractionJavascriptInterface(onUrlExtracted)

        urlExtractionInterface.urlExtracted(null)

        verify(onUrlExtracted).invoke(null)
    }
}
