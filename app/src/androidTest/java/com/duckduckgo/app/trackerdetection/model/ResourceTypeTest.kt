/*
 * Copyright (c) 2017 DuckDuckGo
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

package com.duckduckgo.app.trackerdetection.model

import android.net.Uri
import android.webkit.WebResourceRequest
import com.duckduckgo.app.trackerdetection.model.ResourceType.*
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Assert.assertEquals
import org.junit.Test


class ResourceTypeTest {

    companion object {
        private const val unknownHeader = "text/plain"
        private const val imageHeader = "application/vnd.api+json, image/png"
        private const val cssHeader = "application/vnd.api+json, text/css"
        private const val scriptHeader = "application/vnd.api+json, application/javascript"

        private val unknownUrl = Uri.parse("http://www.example.com/index.html")
        private val imageUrl = Uri.parse("http://www.example.com/a.png?param=value")
        private val cssUrl = Uri.parse("http://www.example.com/a.css#anchor")
        private val scriptUrl = Uri.parse("http://www.example.com/a.js?param=value")
    }

    @Test
    fun whenRequestContainsImageHeaderThenResourceTypeIsImage() {
        val requestMock = buildRequestMock(imageHeader, unknownUrl)
        assertEquals(IMAGE, ResourceType.from(requestMock))
    }

    @Test
    fun whenRequestContainsImageUrlThenResourceTypeIsImage() {
        val requestMock = buildRequestMock(unknownHeader, imageUrl)
        assertEquals(IMAGE, ResourceType.from(requestMock))
    }

    @Test
    fun whenRequestContainsCssHeaderThenResourceTypeIsCss() {
        val requestMock = buildRequestMock(cssHeader, unknownUrl)
        assertEquals(CSS, ResourceType.from(requestMock))
    }

    @Test
    fun whenRequestContainsCssUrlThenResourceTypeIsCss() {
        val requestMock = buildRequestMock(unknownHeader, cssUrl)
        assertEquals(CSS, ResourceType.from(requestMock))
    }

    @Test
    fun whenRequestContainsScriptHeaderThenResourceTypeIsScript() {
        val requestMock = buildRequestMock(scriptHeader, unknownUrl)
        assertEquals(SCRIPT, ResourceType.from(requestMock))
    }

    @Test
    fun whenRequestContainsScriptUrlThenResourceTypeIsScript() {
        val requestMock = buildRequestMock(unknownHeader, scriptUrl)
        assertEquals(SCRIPT, ResourceType.from(requestMock))
    }

    @Test
    fun whenRequestContainsUnknownHeaderAndUrlThenResourceTypeIsUnknown() {
        val requestMock = buildRequestMock(unknownHeader, unknownUrl)
        assertEquals(UNKNOWN, ResourceType.from(requestMock))
    }

    @Test
    fun whenRequestContainsNoHeaderOrUrlThenResourceTypeIsUnknown() {
        val requestMock: WebResourceRequest = mock()
        assertEquals(UNKNOWN, ResourceType.from(requestMock))
    }

    private fun buildRequestMock(acceptHeader: String, url: Uri): WebResourceRequest {
        val requestMock: WebResourceRequest = mock()
        val headers: HashMap<String, String> = hashMapOf("Accept" to acceptHeader)
        whenever(requestMock.url).thenReturn(url)
        whenever(requestMock.requestHeaders).thenReturn(headers)
        return requestMock
    }


}
