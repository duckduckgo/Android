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

package com.duckduckgo.cookies.impl

import com.duckduckgo.app.statistics.pixels.Pixel

enum class CookiesPixelName(override val pixelName: String) : Pixel.PixelName {
    COOKIE_DB_OPEN_ERROR("m_cookie_db_open_error"),
    COOKIE_EXPIRE_ERROR("m_cookie_db_expire_error"),
    COOKIE_DELETE_ERROR("m_cookie_db_delete_error"),
}
