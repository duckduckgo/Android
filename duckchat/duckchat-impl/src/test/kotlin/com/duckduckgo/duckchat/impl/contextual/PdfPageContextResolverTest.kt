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

package com.duckduckgo.duckchat.impl.contextual

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.browser.api.pdf.PdfPageContext
import com.duckduckgo.browser.api.pdf.PdfPageContextProvider
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.duckduckgo.duckchat.impl.pixel.PageContextExtractionPixels
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*

@RunWith(AndroidJUnit4::class)
class PdfPageContextResolverTest {
    private val toggle: Toggle = mock { on { isEnabled() } doReturn true }
    private val feature: DuckChatFeature = mock { on { pdfPageContext() } doReturn toggle }
    private val provider: PdfPageContextProvider = mock()
    private val pixels: PageContextExtractionPixels = mock()
    private val testee = PdfPageContextResolver(feature, provider, pixels)
    private val document = PdfPageContext("document.pdf", "https://example.com/document.pdf", PdfPageContext.Status.AVAILABLE)

    @Test fun `automatic attachment off sends PDF title without bytes`() = runTest {
        whenever(provider.get("tab", false)).thenReturn(document)
        val result = JSONObject(testee.resolve("tab", null, false)!!)
        assertEquals("document.pdf", result.getString("title"))
        assertEquals("application/pdf", result.getString("mimeType"))
        assertFalse(result.has("data"))
        verify(provider, never()).get("tab", true)
        verifyNoInteractions(pixels)
    }

    @Test fun `explicit attach sends base64 PDF`() = runTest {
        whenever(provider.get("tab", true)).thenReturn(document.copy(data = "JVBERi0="))
        val result = JSONObject(testee.resolve("tab", null, true)!!)
        assertEquals("JVBERi0=", result.getString("data"))
        assertTrue(result.getBoolean("attachable"))
        assertEquals("", result.getString("content"))
        assertFalse(result.getBoolean("truncated"))
        assertEquals(0, result.getInt("fullContentLength"))
        assertEquals(0, result.getJSONArray("favicon").length())
    }

    @Test fun `too large is not attachable and has no bytes`() = runTest {
        whenever(provider.get("tab", true)).thenReturn(document.copy(status = PdfPageContext.Status.TOO_LARGE))
        val result = JSONObject(testee.resolve("tab", null, true)!!)
        assertFalse(result.getBoolean("attachable"))
        assertFalse(result.has("data"))
    }

    @Test fun `stale PDF never becomes hidden web page context`() = runTest {
        whenever(provider.get("tab", true)).thenReturn(document.copy(status = PdfPageContext.Status.STALE))
        assertNull(testee.resolve("tab", "{\"content\":\"hidden page\"}", true))
    }

    @Test fun `disabled flag preserves markdown without reading PDF`() = runTest {
        whenever(toggle.isEnabled()).thenReturn(false)
        assertEquals("markdown", testee.resolve("tab", "markdown", true))
        verifyNoInteractions(provider, pixels)
    }
}
