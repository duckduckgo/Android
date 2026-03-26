/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.duckchat.impl.messaging.sync

/**
 * Converts a standard Base64 string to URL-safe Base64 (base64url) format.
 *
 * This function performs the following transformations:
 * - Replaces '+' with '-'
 * - Replaces '/' with '_'
 * - Removes trailing padding '=' characters
 *
 * This is useful when encoding data that needs to be safely transmitted in URLs
 * or other contexts where standard Base64 characters might cause issues.
 *
 * @return A URL-safe Base64 encoded string
 */
internal fun String.applyUrlSafetyFromB64(): String {
    return this
        .replace('+', '-')
        .replace('/', '_')
        .trimEnd('=')
}

/**
 * Converts a URL-safe Base64 (base64url) string back to standard Base64 format.
 *
 * This function performs the following transformations:
 * - Replaces '-' with '+'
 * - Replaces '_' with '/'
 * - Restores padding '=' characters as needed
 *
 * This is the inverse of [applyUrlSafetyFromB64] and is needed because Android's
 * Base64.URL_SAFE flag does not automatically restore missing padding on decode.
 *
 * @return A standard Base64 encoded string with proper padding
 */
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
