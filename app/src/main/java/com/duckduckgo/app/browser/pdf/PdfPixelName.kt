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

import com.duckduckgo.app.statistics.pixels.Pixel

enum class PdfPixelName(override val pixelName: String) : Pixel.PixelName {
    PDF_VIEWER_OPENED("m_pdf_viewer_opened"),
    PDF_RENDER_FAILURE("m_pdf_render_failure"),
    PDF_DOWNLOAD_MENU_ITEM_PRESSED("m_nav_pdf_download_menu_item_pressed"),
    PDF_FALLBACK("m_pdf_fallback"),
}
