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

package com.duckduckgo.duckchat.impl.ui.nativeinput.attachment

data class PastedText(
    val id: String,
    val content: String,
) {
    val title: String
        get() = content.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()

    val length: Int
        get() = content.length
}

data class PromptLength(
    val count: Int,
    val limit: Int,
) {
    val isOverLimit: Boolean
        get() = count > limit
}

object PromptLimits {
    const val PASTE_CONVERSION_THRESHOLD = 4_000
    const val MAX_CHARACTERS = 16_000

    fun convertsToPastedText(inserted: CharSequence): Boolean = inserted.length >= PASTE_CONVERSION_THRESHOLD && inserted.isNotBlank()

    fun evaluate(
        typedLength: Int,
        pastedTexts: List<PastedText>,
        hasImage: Boolean,
        maxCharactersWithImage: Int,
    ): PromptLength = PromptLength(
        count = typedLength + pastedTexts.sumOf { it.length },
        limit = if (hasImage) minOf(maxCharactersWithImage, MAX_CHARACTERS) else MAX_CHARACTERS,
    )
}
