/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.savedsites.impl

import com.duckduckgo.app.statistics.pixels.Pixel
enum class SavedSitesPixelName(override val pixelName: String) : Pixel.PixelName {
    BOOKMARK_IMPORT_SUCCESS("m_bi_s"),
    BOOKMARK_IMPORT_ERROR("m_bi_e"),
    BOOKMARK_EXPORT_SUCCESS("m_be_a"),
    BOOKMARK_EXPORT_ERROR("m_be_e"),

    FAVORITE_BOOKMARKS_ITEM_PRESSED("m_fav_b"),
}
