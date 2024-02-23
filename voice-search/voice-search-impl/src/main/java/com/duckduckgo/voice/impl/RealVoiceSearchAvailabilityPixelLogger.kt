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

import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.voice.api.VoiceSearchAvailabilityPixelLogger
import com.duckduckgo.voice.store.VoiceSearchRepository
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

@ContributesBinding(ActivityScope::class)
class RealVoiceSearchAvailabilityPixelLogger @Inject constructor(
    private val pixel: Pixel,
    private val voiceSearchRepository: VoiceSearchRepository,
    private val configProvider: VoiceSearchAvailabilityConfigProvider,
) : VoiceSearchAvailabilityPixelLogger {

    override fun log() {
        if (!voiceSearchRepository.getHasLoggedAvailability()) {
            val params = mapOf(
                Pixel.PixelParameter.LOCALE to configProvider.get().languageTag,
                Pixel.PixelParameter.MANUFACTURER to configProvider.get().deviceManufacturer,
                Pixel.PixelParameter.MODEL to configProvider.get().deviceModel,
            )
            pixel.fire(VoiceSearchPixelNames.VOICE_SEARCH_AVAILABLE, params)
            voiceSearchRepository.saveLoggedAvailability()
        }
    }
}
