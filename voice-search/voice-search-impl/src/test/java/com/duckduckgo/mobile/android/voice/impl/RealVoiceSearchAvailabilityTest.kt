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

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever

class RealVoiceSearchAvailabilityTest {
    @Mock
    private lateinit var configProvider: VoiceSearchAvailabilityConfigProvider

    private lateinit var testee: RealVoiceSearchAvailability

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        testee = RealVoiceSearchAvailability(configProvider)
    }

    @Test
    fun whenDeviceHasValidConfigThenIsVoiceSearchSupportedTrue() {
        whenever(configProvider.get()).thenReturn(
            VoiceSearchAvailabilityConfig(
                sdkInt = 31,
                languageTag = "en-US",
                isOnDeviceSpeechRecognitionSupported = true
            )
        )

        assertTrue(testee.isVoiceSearchSupported)
    }

    @Test
    fun whenDeviceHasInvalidLanguageThenIsVoiceSearchSupportedFalse() {
        whenever(configProvider.get()).thenReturn(
            VoiceSearchAvailabilityConfig(
                sdkInt = 31,
                languageTag = "en-UK",
                isOnDeviceSpeechRecognitionSupported = true
            )
        )

        assertFalse(testee.isVoiceSearchSupported)
    }

    @Test
    fun whenDeviceHasInvalidSdkThenIsVoiceSearchSupportedFalse() {
        whenever(configProvider.get()).thenReturn(
            VoiceSearchAvailabilityConfig(
                sdkInt = 30,
                languageTag = "en-US",
                isOnDeviceSpeechRecognitionSupported = true
            )
        )

        assertFalse(testee.isVoiceSearchSupported)
    }

    @Test
    fun whenDeviceHasNoSupportForOnDeviceSpeechRecognitionThenIsVoiceSearchSupportedFalse() {
        whenever(configProvider.get()).thenReturn(
            VoiceSearchAvailabilityConfig(
                sdkInt = 32,
                languageTag = "en-US",
                isOnDeviceSpeechRecognitionSupported = false
            )
        )

        assertFalse(testee.isVoiceSearchSupported)
    }

    @Test
    fun whenVoiceSearchNotSupportedThenShouldShowVoiceSearchFalse() {
        whenever(configProvider.get()).thenReturn(
            VoiceSearchAvailabilityConfig(
                sdkInt = 32,
                languageTag = "en-US",
                isOnDeviceSpeechRecognitionSupported = false
            )
        )

        val result = testee.shouldShowVoiceSearch(
            isEditing = true,
            urlLoaded = "https://duckduckgo.com/?q=hello"
        )

        assertFalse(result)
    }

    @Test
    fun whenVoiceSearchSupportedAndIsEditingUrlThenShouldShowVoiceSearchTrue() {
        whenever(configProvider.get()).thenReturn(
            VoiceSearchAvailabilityConfig(
                sdkInt = 32,
                languageTag = "en-US",
                isOnDeviceSpeechRecognitionSupported = true
            )
        )

        val result = testee.shouldShowVoiceSearch(
            isEditing = true,
            urlLoaded = "www.fb.com"
        )

        assertTrue(result)
    }

    @Test
    fun whenVoiceSearchSupportedAndIsEditingSERPThenShouldShowVoiceSearchTrue() {
        whenever(configProvider.get()).thenReturn(
            VoiceSearchAvailabilityConfig(
                sdkInt = 32,
                languageTag = "en-US",
                isOnDeviceSpeechRecognitionSupported = true
            )
        )

        val result = testee.shouldShowVoiceSearch(
            isEditing = true,
            urlLoaded = "https://duckduckgo.com/?q=hello"
        )

        assertTrue(result)
    }

    @Test
    fun whenVoiceSearchSupportedAndUrlShownInAddressBarThenShouldShowVoiceSearchFalse() {
        whenever(configProvider.get()).thenReturn(
            VoiceSearchAvailabilityConfig(
                sdkInt = 32,
                languageTag = "en-US",
                isOnDeviceSpeechRecognitionSupported = true
            )
        )

        val result = testee.shouldShowVoiceSearch(
            isEditing = false,
            urlLoaded = "www.fb.com"
        )

        assertFalse(result)
    }

    @Test
    fun whenVoiceSearchSupportedAndSERPShownThenShouldShowVoiceSearchTrue() {
        whenever(configProvider.get()).thenReturn(
            VoiceSearchAvailabilityConfig(
                sdkInt = 32,
                languageTag = "en-US",
                isOnDeviceSpeechRecognitionSupported = true
            )
        )

        val result = testee.shouldShowVoiceSearch(
            isEditing = false,
            urlLoaded = "https://duckduckgo.com/?q=hello"
        )

        assertTrue(result)
    }

    @Test
    fun whenVoiceSearchSupportedAndUrlEmptyThenShouldShowVoiceSearchTrue() {
        whenever(configProvider.get()).thenReturn(
            VoiceSearchAvailabilityConfig(
                sdkInt = 32,
                languageTag = "en-US",
                isOnDeviceSpeechRecognitionSupported = true
            )
        )

        val result = testee.shouldShowVoiceSearch(
            isEditing = false,
            urlLoaded = ""
        )

        assertTrue(result)
    }
}
