/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.sync.impl

import android.util.Base64

internal fun String.encodeB64(): String {
    return Base64.encodeToString(this.toByteArray(), Base64.NO_WRAP)
}

internal fun String.decodeB64(): String {
    return String(Base64.decode(this, Base64.DEFAULT))
}

/**
 * This assumes the string is already base64-encoded
 */
internal fun String.applyUrlSafetyFromB64(): String {
    return this
        .replace('+', '-')
        .replace('/', '_')
        .trimEnd('=')
}

internal fun String.removeUrlSafetyToRestoreB64(): String {
    return this
        .replace('-', '+')
        .replace('_', '/')
        .restoreBase64Padding()
}

private fun String.restoreBase64Padding(): String {
    return when (length % 4) {
        2 -> "$this=="
        3 -> "$this="
        else -> this
    }
}
