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

/** Public listener interface for implementing special cases when websites need extra logic
 * when requesting permissions */
interface SitePermissionsGrantedListener {

    /** Loads embedded room url when permission is granted */
    fun permissionsGrantedOnWhereby()

    /**
     * Called when the user grants media capture permissions (camera and/or microphone) for a site.
     *
     * This can be used by the browser to perform additional per-tab adjustments that unblock expected
     * in-page flows (e.g., starting a camera preview) after an explicit user permission grant.
     *
     * @param origin The requesting origin as a string (e.g., "https://example.com")
     * @param grantedResources The granted WebView PermissionRequest resources (e.g., RESOURCE_VIDEO_CAPTURE)
     */
    fun permissionsGrantedForMediaCapture(
        origin: String,
        grantedResources: Array<String>,
    ) {
        // default no-op
    }
}
