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

package com.duckduckgo.voice.impl

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.voice.api.VoiceSearchAvailability
import com.duckduckgo.voice.impl.language.LanguageSupportChecker
import com.duckduckgo.voice.impl.remoteconfig.VoiceSearchFeature
import com.duckduckgo.voice.impl.remoteconfig.VoiceSearchFeatureRepository
import com.duckduckgo.voice.store.VoiceSearchRepository
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class RealVoiceSearchAvailability @Inject constructor(
    private val configProvider: VoiceSearchAvailabilityConfigProvider,
    private val voiceSearchFeature: VoiceSearchFeature,
    private val voiceSearchFeatureRepository: VoiceSearchFeatureRepository,
    private val voiceSearchRepository: VoiceSearchRepository,
    private val languageSupportChecker: LanguageSupportChecker,
) : VoiceSearchAvailability {
    companion object {
        private const val URL_DDG_SERP = "https://duckduckgo.com/?"
    }

    override val isVoiceSearchSupported: Boolean
        get() = configProvider.get().run {
            voiceSearchFeature.self().isEnabled() &&
                hasValidVersion(sdkInt) &&
                isOnDeviceSpeechRecognitionSupported &&
                languageSupportChecker.isLanguageSupported() &&
                hasValidLocale(languageTag) &&
                voiceSearchFeatureRepository.manufacturerExceptions.none { it.name == deviceManufacturer }
        }

    override val isVoiceSearchAvailable: Boolean
        get() = isVoiceSearchSupported && voiceSearchRepository.isVoiceSearchUserEnabled(voiceSearchRepository.getHasAcceptedRationaleDialog())

    private fun hasValidVersion(sdkInt: Int) = voiceSearchFeatureRepository.minVersion?.let { minVersion ->
        sdkInt >= minVersion
    } ?: true

    private fun hasValidLocale(localeLanguageTag: String) = voiceSearchFeatureRepository.localeExceptions.none { it.name == localeLanguageTag }

    override fun shouldShowVoiceSearch(
        hasFocus: Boolean,
        query: String,
        hasQueryChanged: Boolean,
        urlLoaded: String,
    ): Boolean {
        // Show microphone icon only when:
        // - omnibar is focused and query hasn't changed
        // - omnibar is focused and query is empty
        // - url loaded is empty and query hasn't changed
        // - DDG SERP is shown and query hasn't changed
        return if (isVoiceSearchAvailable) {
            hasFocus && query.isNotBlank() && !hasQueryChanged ||
                query.isBlank() && hasFocus ||
                urlLoaded.isEmpty() && !hasQueryChanged ||
                (urlLoaded.startsWith(URL_DDG_SERP) && (!hasQueryChanged || !hasFocus))
        } else {
            false
        }
    }
}
