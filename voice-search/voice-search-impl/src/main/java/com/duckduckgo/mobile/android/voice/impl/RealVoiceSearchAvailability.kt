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

package com.duckduckgo.mobile.android.voice.impl

import android.os.Build
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.voice.api.VoiceSearchAvailability
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class RealVoiceSearchAvailability @Inject constructor(
    private val configProvider: VoiceSearchAvailabilityConfigProvider
) : VoiceSearchAvailability {
    companion object {
        private const val LANGUAGE_TAG_ENG_US = "en-US"
        private const val URL_DDG_SERP = "https://duckduckgo.com/?"
    }

    override val isVoiceSearchSupported: Boolean
        get() = configProvider.get().run {
            hasValidVersion(sdkInt) && isOnDeviceSpeechRecognitionSupported && hasValidLocale(languageTag)
        }

    private fun hasValidVersion(sdkInt: Int) = sdkInt >= Build.VERSION_CODES.S

    private fun hasValidLocale(localeLanguageTag: String) = localeLanguageTag == LANGUAGE_TAG_ENG_US

    override fun shouldShowVoiceSearch(
        isEditing: Boolean,
        urlLoaded: String
    ): Boolean {
        // Show microphone icon only when:
        // - user is editing the address bar OR
        // - address bar is empty (initial state / new tab) OR
        // - DDG SERP is shown OR (address bar doesn't contain a website)
        return if (isVoiceSearchSupported) {
            isEditing || urlLoaded.isEmpty() || urlLoaded.startsWith(URL_DDG_SERP)
        } else {
            false
        }
    }
}
