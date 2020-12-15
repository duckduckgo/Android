/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.app.browser.useragent

import android.net.Uri
import com.duckduckgo.app.global.UriString
import com.duckduckgo.app.global.baseHost
import com.duckduckgo.app.global.isMobileSite
import javax.inject.Inject

class MobileUrlReWriter @Inject constructor() {

    fun isStrictlyMobileSite(uri: Uri?): Boolean {
        if (uri == null) return false
        val host = uri.host
        return if (!uri.isMobileSite && host != null) {
            strictlyMobileSiteHosts.any { UriString.sameOrSubdomain(host, it) }
        } else {
            false
        }
    }

    fun getMobileSite(uri: Uri): String? {
        val host = uri.host ?: return null
        val baseHost = uri.baseHost ?: return null
        val newHost = "m.$baseHost"
        return uri.toString().replace(host, newHost)
    }

    companion object {
        val strictlyMobileSiteHosts = listOf("facebook.com")
    }
}
