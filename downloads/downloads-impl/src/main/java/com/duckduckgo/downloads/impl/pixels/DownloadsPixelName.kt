/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.downloads.impl.pixels

import com.duckduckgo.app.statistics.pixels.Pixel

enum class DownloadsPixelName(override val pixelName: String) : Pixel.PixelName {
    DOWNLOAD_REQUEST_STARTED("m_download_request_started"),
    DOWNLOAD_REQUEST_SUCCEEDED("m_download_request_succeeded"),
    DOWNLOAD_REQUEST_FAILED("m_download_request_failed"),

    DOWNLOAD_FILE_DEFAULT_GUESSED_NAME("m_df_dgn"),
}
