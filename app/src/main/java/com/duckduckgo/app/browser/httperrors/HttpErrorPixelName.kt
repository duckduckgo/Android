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

package com.duckduckgo.app.browser.httperrors

import com.duckduckgo.app.statistics.pixels.Pixel

enum class HttpErrorPixelName(override val pixelName: String) : Pixel.PixelName {
    WEBVIEW_RECEIVED_HTTP_ERROR_400_DAILY("m_webview_received_http_error_400_daily"),
    WEBVIEW_RECEIVED_HTTP_ERROR_4XX_DAILY("m_webview_received_http_error_4xx_daily"),
    WEBVIEW_RECEIVED_HTTP_ERROR_5XX_DAILY("m_webview_received_http_error_5xx_daily"),
}

object HttpErrorPixelParameters {
    const val HTTP_ERROR_CODE_COUNT = "count"
}
