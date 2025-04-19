/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.clipboard

/**
 * Used for copying to the clipboard.
 */
interface ClipboardInteractor {
    /**
     * Copies the given text to the clipboard.
     * @param toCopy The text to copy.
     * @param isSensitive Whether the text is sensitive or not.
     * @return Returns true if a notification was shown automatically to the user. This happens on some Android versions, and we don't want to double-notify.
     */
    fun copyToClipboard(toCopy: String, isSensitive: Boolean): Boolean
}
