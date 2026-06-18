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

package com.duckduckgo.app.browser.pdf

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfViewerActivityTest {

    @Test
    fun `intent builder puts cached URI as extra`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val uri = "file:///data/user/0/com.example/cache/pdf_cache/doc.pdf"
        val intent = PdfViewerActivity.intent(context, uri, "doc.pdf")

        assertEquals(uri, intent.getStringExtra("extra_cached_uri"))
    }

    @Test
    fun `intent builder puts file name as extra`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = PdfViewerActivity.intent(context, "file:///cache/test.pdf", "report.pdf")

        assertEquals("report.pdf", intent.getStringExtra("extra_file_name"))
    }

    @Test
    fun `intent targets PdfViewerActivity class`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = PdfViewerActivity.intent(context, "file:///cache/test.pdf", "test.pdf")

        assertEquals(PdfViewerActivity::class.java.name, intent.component?.className)
    }
}
