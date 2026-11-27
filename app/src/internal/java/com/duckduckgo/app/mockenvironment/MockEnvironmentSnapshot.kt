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

package com.duckduckgo.app.mockenvironment

import android.content.Context
import java.security.MessageDigest
import javax.inject.Inject

/**
 * The pinned tracker data and surrogates a mocked test environment runs against (assets/mockenvironment).
 */
class MockEnvironmentSnapshot @Inject constructor(
    private val context: Context,
) {

    val tds: ByteArray by lazy { read(TDS_FILE) }
    val surrogates: ByteArray by lazy { read(SURROGATES_FILE) }

    /**
     * Derived from the TDS content at runtime, so refreshing the snapshot changes it without a manual bump.
     */
    val tdsEtag: String by lazy {
        val sha256 = MessageDigest.getInstance("SHA-256").digest(tds).joinToString("") { "%02x".format(it) }
        "pinned-${sha256.take(ETAG_HASH_LENGTH)}"
    }

    private fun read(name: String): ByteArray = context.assets.open("$DIR/$name").use { it.readBytes() }

    private companion object {
        const val DIR = "mockenvironment"
        const val TDS_FILE = "tds.json"
        const val SURROGATES_FILE = "surrogates.txt"
        const val ETAG_HASH_LENGTH = 16
    }
}
