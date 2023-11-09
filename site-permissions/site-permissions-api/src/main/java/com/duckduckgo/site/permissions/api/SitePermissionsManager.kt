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

import android.webkit.PermissionRequest

/** Public interface for managing site permissions data */
interface SitePermissionsManager {
    /**
     * Returns an array of permissions that we support and the user has to manually handle
     *
     * @param tabId the tab where the request was originated
     * @param request original permission request
     * @return array of strings with the permissions the user needs to handle
     */
    suspend fun getSitePermissionsForUserToHandle(tabId: String, request: PermissionRequest): Array<String>

    /**
     * Deletes all site permissions but the ones that are fireproof
     *
     * @param fireproofDomains list of domains that are fireproof
     */
    suspend fun clearAllButFireproof(fireproofDomains: List<String>)
}
