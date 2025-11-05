/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.voice.api

import android.app.Activity
import androidx.activity.result.ActivityResultCaller

interface VoiceSearchLauncher {
    fun registerResultsCallback(
        caller: ActivityResultCaller,
        activity: Activity,
        source: Source,
        onEvent: (Event) -> Unit,
    )

    fun launch(activity: Activity, mode: VoiceSearchMode = VoiceSearchMode.SEARCH)

    enum class VoiceSearchMode(val value: Int) {
        SEARCH(0),
        DUCK_AI(1),
        ;

        companion object {
            fun fromValue(value: Int): VoiceSearchMode {
                return entries.find { it.value == value } ?: SEARCH
            }
        }
    }

    enum class Source(val paramValueName: String) {
        BROWSER("browser"),
        WIDGET("widget"),
    }

    sealed class VoiceRecognitionResult {
        abstract val query: String
        data class SearchResult(override val query: String) : VoiceRecognitionResult()
        data class DuckAiResult(override val query: String) : VoiceRecognitionResult()
    }

    sealed class Event {
        data class VoiceRecognitionSuccess(val result: VoiceRecognitionResult) : Event()
        data object SearchCancelled : Event()
        data object VoiceSearchDisabled : Event()
    }
}
