/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.app.drm

import android.net.Uri
import android.webkit.PermissionRequest
import com.duckduckgo.app.global.baseHost
import javax.inject.Inject

class DrmRequestManager @Inject constructor() {

    fun drmPermissionsForRequest(request: PermissionRequest): Array<String> {
        val perms = mutableSetOf<String>()
        if (shouldEnableDrmForUri(request.origin)) {
            val resources = request.resources
            resources.find { (it == PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID) }?.let {
                perms.add(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID)
            }
        }
        return perms.toTypedArray()
    }

    private fun shouldEnableDrmForUri(uri: Uri): Boolean {
        return domainsThatAllowDrm.contains(uri.baseHost)
    }

    companion object {
        val domainsThatAllowDrm = listOf(
            "open.spotify.com",
            "netflix.com"
        )
    }
}
