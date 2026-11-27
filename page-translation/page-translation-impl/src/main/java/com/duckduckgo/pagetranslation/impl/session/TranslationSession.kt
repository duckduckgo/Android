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

import com.duckduckgo.pagetranslation.impl.engine.BatchTranslation
import com.duckduckgo.pagetranslation.impl.engine.TranslationEngine
import com.duckduckgo.pagetranslation.impl.engine.TranslationUnit
import com.duckduckgo.pagetranslation.impl.language.LanguagePair
import com.duckduckgo.pagetranslation.impl.messaging.PageTranslationMessaging
import com.duckduckgo.pagetranslation.impl.messaging.UnitTranslation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Translates the document whose `performance.timeOrigin` is [timeOrigin], one batch at a time, and writes its progress to [state].
 * It runs on [scope], which must be the main thread's, so that once [end] returns nothing it started can still write.
 */
class TranslationSession(
    private val timeOrigin: Double,
    private val messaging: PageTranslationMessaging,
    private val engine: TranslationEngine,
    private val isVisible: StateFlow<Boolean>,
    private val state: MutableStateFlow<TranslationState>,
    private val scope: CoroutineScope,
) {
    private var job: Job? = null

    /** Translates the document into [pair] if it hasn't been yet. */
    fun translate(pair: LanguagePair) {
        if (state.value != TranslationState.Idle) return

        state.value = TranslationState.Translating
        job = scope.launch { translatePage(pair) }
    }

    /** Stops translating and drops the request in flight. Results that arrive later are ignored. */
    fun end() {
        job?.cancel()
        messaging.cancel()
    }

    private suspend fun translatePage(pair: LanguagePair) {
        messaging.inject(timeOrigin)
        val (viewport, rest) = planBatches(messaging.extract().orEmpty()).partition { it.isViewport }

        var written = 0
        for (batch in viewport) {
            val count = translateBatch(pair, batch)
            if (count == null) {
                state.value = TranslationState.Failed(hasAppliedUnits = written > 0)
                return
            }
            written += count
        }
        if (written == 0) {
            state.value = TranslationState.Failed(hasAppliedUnits = false)
            return
        }

        state.value = TranslationState.Translated
        for (batch in rest) translateBatch(pair, batch) ?: return
    }

    private suspend fun translateBatch(pair: LanguagePair, batch: TranslationBatch): Int? {
        isVisible.first { it }
        val result = engine.translate(pair, batch.units.map { TranslationUnit(it.text, it.isHtml) }) { isVisible.value }
        if (result !is BatchTranslation.Translated) return null

        val updates = batch.units.zip(result.translations).mapNotNull { (unit, translation) ->
            translation?.let { UnitTranslation(unit.id, it, unit.isHtml) }
        }
        return messaging.apply(pair, updates)
    }
}
