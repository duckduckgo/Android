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

package com.duckduckgo.app.voice

import android.content.Context
import android.os.Build
import android.speech.SpeechRecognizer
import java.util.Locale

object VoiceSearchAvailabilityUtil {
    private const val LANGUAGE_TAG_ENG_US = "en-US"

    fun shouldShowVoiceSearchEntry(context: Context): Boolean =
        hasValidVersion() && SpeechRecognizer.isOnDeviceRecognitionAvailable(context) && hasValidLocale()

    private fun hasValidVersion() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    private fun hasValidLocale() = Locale.getDefault().toLanguageTag() == LANGUAGE_TAG_ENG_US
}
