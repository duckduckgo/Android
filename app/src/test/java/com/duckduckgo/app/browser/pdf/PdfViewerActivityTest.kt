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

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.browser.api.ui.BrowserScreens.PdfViewerActivityParams
import com.duckduckgo.browser.api.ui.BrowserScreens.PdfViewerSource
import com.duckduckgo.navigation.api.getActivityParams
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfViewerActivityTest {

    @Test
    fun `activity params round-trip through the intent`() {
        val uri = "file:///data/user/0/com.example/cache/pdf_cache/doc.pdf"
        val params = PdfViewerActivityParams(uri, "doc.pdf", PdfViewerSource.EXTERNAL_INTENT)
        val intent = intentWithParams(params)

        assertEquals(params, intent.getActivityParams(PdfViewerActivityParams::class.java))
    }

    private fun intentWithParams(params: PdfViewerActivityParams): Intent =
        Intent().putExtra("ACTIVITY_SERIALIZABLE_PARAMETERS_ARG", params)
}
