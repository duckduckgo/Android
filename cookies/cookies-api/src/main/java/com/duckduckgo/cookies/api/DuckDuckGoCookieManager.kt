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

package com.duckduckgo.cookies.api

/** Public interface for DuckDuckGoCookieManager */
interface DuckDuckGoCookieManager {
    /**
     * This method deletes all the cookies that are not related with DDG settings or fireproofed websites
     * Note: The Fire Button does not delete the user's DuckDuckGo search settings, which are saved as cookies.
     * Removing these cookies would reset them and have undesired consequences, i.e. changing the theme, default language, etc.
     * These cookies are not stored in a personally identifiable way. For example, the large size setting is stored as 's=l.'
     * More info in https://duckduckgo.com/privacy
     */
    suspend fun removeExternalCookies()

    /**
     * This method calls the flush method from the Cookie Manager
     */
    fun flush()
}
