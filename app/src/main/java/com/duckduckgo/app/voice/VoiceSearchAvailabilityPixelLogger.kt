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
import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import javax.inject.Inject

class VoiceSearchAvailabilityPixelLogger @Inject constructor(
    val context: Context,
    private val pixel: Pixel
) {
    companion object {
        const val FILENAME = "com.duckduckgo.app.voice"
        const val KEY_VOICE_SEARCH_AVAILABILITY_LOGGED = "KEY_VOICE_SEARCH_AVAILABILITY_LOGGED"
    }

    private val preferences: SharedPreferences
        get() = context.getSharedPreferences(FILENAME, Context.MODE_PRIVATE)

    fun log() {
        if (!hasLoggedPixel()) {
            pixel.fire(AppPixelName.VOICE_SEARCH_AVAILABLE)
            savePixelAlreadyLogged()
        }
    }

    private fun hasLoggedPixel() = preferences.getBoolean(KEY_VOICE_SEARCH_AVAILABILITY_LOGGED, false)

    private fun savePixelAlreadyLogged() = preferences.edit { putBoolean(KEY_VOICE_SEARCH_AVAILABILITY_LOGGED, true) }
}
