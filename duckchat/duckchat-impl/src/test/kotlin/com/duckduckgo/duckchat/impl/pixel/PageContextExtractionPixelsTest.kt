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

package com.duckduckgo.duckchat.impl.pixel

import com.duckduckgo.app.statistics.pixels.Pixel
import org.junit.Test
import org.mockito.kotlin.*

class PageContextExtractionPixelsTest {
    private val pixel: Pixel = mock()
    private val testee = PageContextExtractionPixels(pixel)

    @Test fun `daily variants use name only and preserve first context type`() {
        testee.success(PageContextExtractionPixels.ContextType.PDF)
        verify(pixel).fire("m_aichat_page_context_extraction_success_d", mapOf("context_type" to "pdf"), type = Pixel.PixelType.Daily())
        verify(pixel).fire("m_aichat_page_context_extraction_success_c", mapOf("context_type" to "pdf"))
    }

    @Test fun `failure uses bounded latency and context type`() {
        testee.failed(
            PageContextExtractionPixels.Reason.DOCUMENT_UNAVAILABLE,
            PageContextExtractionPixels.Trigger.USER_REQUEST,
            PageContextExtractionPixels.ContextType.PDF,
            1000,
        )
        verify(pixel).fire(
            "m_aichat_page_context_extraction_failed_c",
            mapOf(
                "reason" to "document_unavailable",
                "trigger" to "user_request",
                "context_type" to "pdf",
                "latency" to "1_to_5s",
            ),
        )
    }

    @Test fun `prevented omits context type and latency`() {
        testee.prevented(PageContextExtractionPixels.Category.DOCUMENT_TOO_LARGE, PageContextExtractionPixels.Trigger.AUTO)
        verify(pixel).fire(
            "m_aichat_page_context_extraction_prevented_c",
            mapOf(
                "reason" to "non_attachable",
                "trigger" to "auto",
                "category" to "documentTooLarge",
            ),
        )
    }
}
