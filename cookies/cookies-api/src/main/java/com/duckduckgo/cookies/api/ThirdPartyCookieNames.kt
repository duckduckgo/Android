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

package com.duckduckgo.cookies.api

/**
 * Checks if the given cookie string contains any excluded third party cookie names.
 *
 * @param cookieString The string representation of cookies to check.
 * @return `true` if the `cookieString` contains any of the excluded cookie names, `false` otherwise.
 */
interface ThirdPartyCookieNames {
    fun hasExcludedCookieName(cookieString: String): Boolean
}
