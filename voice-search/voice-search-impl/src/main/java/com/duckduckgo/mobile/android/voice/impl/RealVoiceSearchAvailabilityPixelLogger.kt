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

import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.voice.api.VoiceSearchAvailabilityPixelLogger
import com.squareup.anvil.annotations.ContributesBinding
import dagger.WrongScope
import javax.inject.Inject

@WrongScope(comment = "This can't be ActivityScoped atm since it is injected in a viewmodel.", correctScope = ActivityScope::class)
@ContributesBinding(AppScope::class)
class RealVoiceSearchAvailabilityPixelLogger @Inject constructor(
    private val pixel: Pixel,
    private val voiceSearchChecksStore: VoiceSearchChecksStore
) : VoiceSearchAvailabilityPixelLogger {

    override fun log() {
        if (!voiceSearchChecksStore.hasLoggedAvailability()) {
            pixel.fire(VoiceSearchPixelNames.VOICE_SEARCH_AVAILABLE)
            voiceSearchChecksStore.saveLoggedAvailability()
        }
    }
}
