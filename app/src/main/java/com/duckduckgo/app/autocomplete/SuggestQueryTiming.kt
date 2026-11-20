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

package com.duckduckgo.app.autocomplete

import com.duckduckgo.di.scopes.AppScope
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dev.zacsweers.metro.ContributesBinding
import javax.inject.Inject

/**
 * When suggest queries go out while the user types. Phase 1 sends one query at each of [phase1MarksMillis], measured from
 * the first keystroke of the edit session, whatever the typing speed. After the last mark, phase 2 waits for a pause of
 * [debounceMillis].
 */
data class SuggestQueryTiming(
    val phase1MarksMillis: List<Long>,
    val debounceMillis: Long,
) {
    fun delayMillis(sessionStartedAtMillis: Long?, nowMillis: Long): Long {
        val startedAt = sessionStartedAtMillis ?: return debounceMillis
        val elapsed = nowMillis - startedAt
        val nextMark = phase1MarksMillis.firstOrNull { it > elapsed } ?: return debounceMillis
        return nextMark - elapsed
    }
}

interface SuggestQueryTimingProvider {
    /**
     * Null when the remote timing is off and the omnibar keeps its built-in timing.
     */
    fun timing(): SuggestQueryTiming?
}

@ContributesBinding(AppScope::class)
class RealSuggestQueryTimingProvider @Inject constructor(
    private val autocompleteSpeedFeature: AutocompleteSpeedFeature,
) : SuggestQueryTimingProvider {

    private val adapter by lazy {
        Moshi.Builder().add(KotlinJsonAdapterFactory()).build().adapter(TimingSettings::class.java)
    }

    override fun timing(): SuggestQueryTiming? {
        val toggle = autocompleteSpeedFeature.self()
        if (!toggle.isEnabled()) return null

        val settings = runCatching {
            toggle.getSettings()?.let { adapter.fromJson(it) }
        }.getOrNull()

        val debounceMillis = (settings?.debounceMillis ?: DEFAULT_DEBOUNCE_MILLIS).coerceIn(MIN_DELAY_MILLIS, MAX_DELAY_MILLIS)

        val phase1Marks = (settings?.phase1Marks ?: DEFAULT_PHASE_1_MARKS_MILLIS)
            .filter { it in MIN_DELAY_MILLIS..MAX_DELAY_MILLIS }
            .distinct()
            .sorted()

        return SuggestQueryTiming(phase1MarksMillis = phase1Marks, debounceMillis = debounceMillis)
    }

    private data class TimingSettings(
        val phase1Marks: List<Long>?,
        val debounceMillis: Long?,
    )

    companion object {
        private val DEFAULT_PHASE_1_MARKS_MILLIS = listOf(150L, 450L)
        private const val DEFAULT_DEBOUNCE_MILLIS = 300L
        private const val MIN_DELAY_MILLIS = 50L
        private const val MAX_DELAY_MILLIS = 1000L
    }
}
