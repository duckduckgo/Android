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

package com.duckduckgo.pagetranslation.impl.session

import com.duckduckgo.pagetranslation.impl.messaging.PageUnit

/** Units translated and applied together. [isViewport] when they belong to the part of the page the user sees first. */
data class TranslationBatch(val units: List<PageUnit>, val isViewport: Boolean)

/**
 * Splits a page's units into batches: the viewport's units first, then the rest, each in page order. A batch holds only plain or
 * only HTML units, and units are added until the next one would take it past [maxCharacters]. A longer unit goes alone, and no unit
 * is ever split. HTML is counted without its tags. When no unit is in the viewport, the first batch counts as the viewport.
 */
fun planBatches(units: List<PageUnit>, maxCharacters: Int = MAX_BATCH_CHARACTERS): List<TranslationBatch> {
    val (viewport, rest) = units.partition { it.isInViewport }
    val viewportBatches = pack(viewport, maxCharacters).map { TranslationBatch(it, isViewport = true) }
    val restBatches = pack(rest, maxCharacters).mapIndexed { i, batch ->
        TranslationBatch(batch, isViewport = viewportBatches.isEmpty() && i == 0)
    }

    return viewportBatches + restBatches
}

private fun pack(units: List<PageUnit>, maxCharacters: Int): List<List<PageUnit>> {
    val batches = mutableListOf<MutableList<PageUnit>>()
    var characters = 0
    units.forEach { unit ->
        val current = batches.lastOrNull()
        val unitCharacters = unit.countCharacters()
        if (current == null || current.first().isHtml != unit.isHtml || characters + unitCharacters > maxCharacters) {
            batches += mutableListOf(unit)
            characters = unitCharacters
        } else {
            current += unit
            characters += unitCharacters
        }
    }

    return batches
}

private fun PageUnit.countCharacters(): Int = if (isHtml) text.replace(HTML_TAG, "").length else text.length

private val HTML_TAG = Regex("<[^>]*>")

private const val MAX_BATCH_CHARACTERS = 2_000
