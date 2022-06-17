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

package com.duckduckgo.app.global

import android.net.Uri
import androidx.core.util.PatternsCompat

class UriString {

    companion object {

        private const val localhost = "localhost"
        private const val space = " "
        private val webUrlRegex by lazy { PatternsCompat.WEB_URL.toRegex() }
        private val domainRegex by lazy { PatternsCompat.DOMAIN_NAME.toRegex() }

        fun host(uriString: String): String? {
            return Uri.parse(uriString).baseHost
        }

        fun sameOrSubdomain(
            child: String,
            parent: String
        ): Boolean {
            val childHost = host(child) ?: return false
            val parentHost = host(parent) ?: return false
            return parentHost == childHost || childHost.endsWith(".$parentHost")
        }

        fun isWebUrl(inputQuery: String): Boolean {
            if (inputQuery.contains(space)) return false
            val uri = Uri.parse(inputQuery).withScheme()
            if (uri.scheme != UrlScheme.http && uri.scheme != UrlScheme.https) return false
            if (uri.userInfo != null) return false

            val host = uri.host ?: return false
            if (host == localhost) return true
            if (host.contains(space)) return false
            if (host.contains("!")) return false
            return (webUrlRegex.containsMatchIn(host))
        }

        fun isValidDomain(domain: String): Boolean {
            return domainRegex.matches(domain)
        }
    }
}
