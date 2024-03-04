/*
 * Copyright (c) 2024 DuckDuckGo
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

import com.duckduckgo.app.statistics.api.BrowserFeatureReporterPlugin
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter
import com.duckduckgo.app.statistics.pixels.toBinaryString
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.voice.api.VoiceSearchAvailability
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

interface VoiceSearchStateReporterPlugin

@ContributesMultibinding(scope = AppScope::class, boundType = BrowserFeatureReporterPlugin::class)
@ContributesBinding(scope = AppScope::class, boundType = VoiceSearchStateReporterPlugin::class)
class RealVoiceSearchStateReporterPlugin @Inject constructor(
    private val voiceSearchAvailability: VoiceSearchAvailability,
) : VoiceSearchStateReporterPlugin, BrowserFeatureReporterPlugin {
    override fun feature(): Pair<String, String> {
        return Pair(voiceSearchAvailability.isVoiceSearchAvailable.toBinaryString(), PixelParameter.VOICE_SEARCH)
    }
}
