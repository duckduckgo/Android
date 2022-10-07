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

package com.duckduckgo.site.permissions.api

/** Public interface for managing site permissions data */
interface SitePermissionsManager {

    /**
     * Returns an array of already granted site permissions. That could be:
     *     - Permission is always allowed for this website
     *     - Permission has been granted within 24h for same site and same tab
     *
     * @param url unmodified URL taken from the URL bar (containing subdomains, query params etc...)
     * @param tabId id from the tab where this method is called from
     * @param resources array of permissions that have been requested by the website
     */
    suspend fun getSitePermissionsGranted(url: String, tabId: String, resources: Array<String>): Array<String>

    /**
     * Returns an array of permissions that we support and user didn't deny for given website
     *
     * @param url unmodified URL taken from the URL bar (containing subdomains, query params etc...)
     * @param resources array of permissions that have been requested by the website
     */
    suspend fun getSitePermissionsAllowedToAsk(url: String, resources: Array<String>): Array<String>

    /**
     * Deletes all site permissions but the ones that are fireproof
     *
     * @param fireproofDomains list of domains that are fireproof
     */
    suspend fun clearAllButFireproof(fireproofDomains: List<String>)
}
