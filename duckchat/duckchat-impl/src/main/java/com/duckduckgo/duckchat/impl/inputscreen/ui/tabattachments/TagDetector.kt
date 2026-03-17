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

package com.duckduckgo.duckchat.impl.inputscreen.ui.tabattachments

data class TagQuery(
    val atIndex: Int,
    val query: String,
)

object TagDetector {

    fun detect(text: String, cursorPosition: Int): TagQuery? {
        if (cursorPosition < 1 || cursorPosition > text.length) return null

        val beforeCursor = text.take(cursorPosition)
        val atIndex = beforeCursor.lastIndexOf('@')
        if (atIndex < 0) return null

        // @ must be at position 0 or preceded by whitespace
        if (atIndex > 0 && !beforeCursor[atIndex - 1].isWhitespace()) return null

        val query = beforeCursor.substring(atIndex + 1)

        // If query starts with space, the tag was cancelled
        if (query.startsWith(' ')) return null

        return TagQuery(atIndex = atIndex, query = query)
    }
}
