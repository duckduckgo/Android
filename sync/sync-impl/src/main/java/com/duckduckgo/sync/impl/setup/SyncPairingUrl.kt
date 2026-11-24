/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.sync.impl.setup

import androidx.core.net.toUri

/**
 * The URLs the pairing instructions tell users to type to open Sync pairing.
 * We match only host and path: query and fragment are ignored, so a pairing code a user was tricked into pasting is never used.
 */
internal enum class SyncPairingUrl(
    private val host: String,
) {
    DuckCom(host = "duck.com"),
    DuckAi(host = "duck.ai"),
    ;

    companion object {
        const val INSTRUCTIONS_URL = "duck.com/pair"

        private val SCHEMES = setOf("http", "https")
        private val PATHS = setOf("/pair", "/pair/")

        fun fromUrl(url: String): SyncPairingUrl? {
            val uri = runCatching { url.toUri() }.getOrNull() ?: return null
            if (uri.scheme?.lowercase() !in SCHEMES) return null
            if (uri.path?.lowercase() !in PATHS) return null
            val host = uri.host?.lowercase()?.removePrefix("www.") ?: return null
            return entries.firstOrNull { it.host == host }
        }
    }
}
