/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.privacy.model

import android.net.Uri
import com.duckduckgo.app.browser.UriString
import com.duckduckgo.common.utils.AppUrl

class TrustedSites {

    companion object {

        private val trusted = listOf(
            AppUrl.Url.HOST,
            "duckduckgo.com",
            "donttrack.us",
            "spreadprivacy.com",
            "duckduckhack.com",
            "privatebrowsingmyths.com",
            "duck.co",
        )

        fun isTrusted(url: String): Boolean {
            return trusted.any { UriString.sameOrSubdomain(url, it) }
        }

        fun isTrusted(url: Uri): Boolean {
            return trusted.any { UriString.sameOrSubdomain(url, it) }
        }
    }
}
