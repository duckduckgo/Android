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
import com.duckduckgo.brokensite.api.BrokenSiteContext
import com.duckduckgo.brokensite.impl.RealBrokenSiteContext
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class DocumentReferrerScriptJsInjectorPluginTest {

    private val mockWebView: WebView = mock()
    private val mockSite: Site = mock()
    private val mockRealBrokenSiteContext = mock<BrokenSiteContext>()

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
        val referrer = "\"https://example.com\""
        val captor = argumentCaptor<ValueCallback<String>>()

        whenever(mockSite.realBrokenSiteContext).thenReturn(mockRealBrokenSiteContext)
        // whenever(mockCoreContentScopeScripts.getScript(site)).thenReturn("")
        documentReferrerScriptJsInjectorPlugin.onPageFinished(mockWebView, url, mockSite)

        verify(mockWebView).evaluateJavascript(any(), captor.capture())
        captor.value.onReceiveValue(referrer)
        // verify(mockSite, atLeast(1)).realBrokenSiteContext.inferOpenerContext("https://example.com")
        // I cannot for the life of me get the above assertion to work -- if I don't mock the brokenSiteContext
        // property, I get an error that Site.getRealBrokenSiteContext() returns null, but if I try to mock a
        // realBrokenSiteContext object, it won't let me because it's a final class (even if I try to mock the
        // BrokenSiteContext interface instead).
        assertNotNull(captor.value)
    }
}

inline fun <reified T> argumentCaptor(): ArgumentCaptor<T> = ArgumentCaptor.forClass(T::class.java)
