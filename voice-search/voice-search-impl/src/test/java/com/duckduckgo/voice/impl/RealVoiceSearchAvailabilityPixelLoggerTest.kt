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
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RealVoiceSearchAvailabilityPixelLoggerTest {
    @Mock
    private lateinit var pixel: Pixel

    @Mock
    private lateinit var voiceSearchChecksStore: VoiceSearchChecksStore

    private lateinit var testee: RealVoiceSearchAvailabilityPixelLogger

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        testee = RealVoiceSearchAvailabilityPixelLogger(pixel, voiceSearchChecksStore)
    }

    @Test
    fun whenHasNotLoggedAvailabilityThenLogPixel() {
        whenever(voiceSearchChecksStore.hasLoggedAvailability()).thenReturn(false)

        testee.log()

        verify(pixel).fire(VoiceSearchPixelNames.VOICE_SEARCH_AVAILABLE)
        verify(voiceSearchChecksStore).saveLoggedAvailability()
    }

    @Test
    fun whenHasLoggedAvailabilityThenDoNothing() {
        whenever(voiceSearchChecksStore.hasLoggedAvailability()).thenReturn(true)

        testee.log()

        verify(pixel, never()).fire(VoiceSearchPixelNames.VOICE_SEARCH_AVAILABLE)
        verify(voiceSearchChecksStore, never()).saveLoggedAvailability()
    }
}
