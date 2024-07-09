/*
 * Copyright (c) 2024 DuckDuckGo
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

import android.webkit.ValueCallback
import android.webkit.WebView
import com.duckduckgo.app.global.model.Site
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions


class DocumentReferrerScriptJsInjectorPluginTest {

    private val mockWebView: WebView = mock()
    private val mockSite: Site = mock()

    private lateinit var documentReferrerScriptJsInjectorPlugin: DocumentReferrerScriptJsInjectorPlugin

    @Before
    fun setUp() {
        documentReferrerScriptJsInjectorPlugin = DocumentReferrerScriptJsInjectorPlugin()
    }

    @Test
    fun whenUrlAboutBlankThenDoNothing() {
        val url = "about:blank"
        documentReferrerScriptJsInjectorPlugin.onPageFinished(mockWebView, url, mockSite)

        verifyNoInteractions(mockWebView)
        verifyNoInteractions(mockSite)
    }

    @Test
    fun whenUrlNotAboutBlankThenInferOpenerContextFromReferrer() {
        val url = ""
        val referrer = "\"http://example.com\""
        val captor = argumentCaptor<ValueCallback<String>>()
        documentReferrerScriptJsInjectorPlugin.onPageFinished(mockWebView, url, mockSite)

        verify(mockWebView).evaluateJavascript(any(), captor.capture())
        captor.value.onReceiveValue(referrer)
        verify(mockSite).inferOpenerContext("http://example.com")
    }
}

inline fun <reified T> argumentCaptor(): ArgumentCaptor<T> = ArgumentCaptor.forClass(T::class.java)
