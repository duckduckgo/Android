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

import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.voice.impl.remoteconfig.Manufacturer
import com.duckduckgo.voice.impl.remoteconfig.VoiceSearchFeature
import com.duckduckgo.voice.impl.remoteconfig.VoiceSearchFeatureRepository
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever

class RealVoiceSearchAvailabilityTest {
    @Mock
    private lateinit var configProvider: VoiceSearchAvailabilityConfigProvider

    @Mock
    private lateinit var voiceSearchFeature: VoiceSearchFeature

    @Mock
    private lateinit var voiceSearchFeatureRepository: VoiceSearchFeatureRepository

    private lateinit var testee: RealVoiceSearchAvailability

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        testee = RealVoiceSearchAvailability(configProvider, voiceSearchFeature, voiceSearchFeatureRepository)
    }

    @Test
    fun whenDeviceHasValidConfigThenIsVoiceSearchSupportedTrue() {
        setupRemoteConfig(voiceSearchEnabled = true, minSdk = 30, excludedManufacturers = emptyArray())
        setupDeviceConfig(manufacturer = "Google", sdkInt = 31, languageTag = "en-US", isOnDeviceSpeechRecognitionAvailable = true)

        assertTrue(testee.isVoiceSearchSupported)
    }

    @Test
    fun whenDeviceHasValidConfigAndMinVersionIsNullThenIsVoiceSearchSupportedTrue() {
        setupRemoteConfig(voiceSearchEnabled = true, minSdk = null, excludedManufacturers = emptyArray())
        setupDeviceConfig(manufacturer = "Google", sdkInt = 31, languageTag = "en-US", isOnDeviceSpeechRecognitionAvailable = true)

        assertTrue(testee.isVoiceSearchSupported)
    }

    @Test
    fun whenDeviceHasInvalidLanguageThenIsVoiceSearchSupportedFalse() {
        setupRemoteConfig(voiceSearchEnabled = true, minSdk = 30, excludedManufacturers = emptyArray())
        setupDeviceConfig(manufacturer = "Google", sdkInt = 31, languageTag = "en-UK", isOnDeviceSpeechRecognitionAvailable = true)

        assertFalse(testee.isVoiceSearchSupported)
    }

    @Test
    fun whenDeviceHasInvalidSdkThenIsVoiceSearchSupportedFalse() {
        setupRemoteConfig(voiceSearchEnabled = true, minSdk = 33, excludedManufacturers = emptyArray())
        setupDeviceConfig(manufacturer = "Google", sdkInt = 31, languageTag = "en-UK", isOnDeviceSpeechRecognitionAvailable = true)

        assertFalse(testee.isVoiceSearchSupported)
    }

    @Test
    fun whenDeviceHasNoSupportForOnDeviceSpeechRecognitionThenIsVoiceSearchSupportedFalse() {
        setupRemoteConfig(voiceSearchEnabled = true, minSdk = 30, excludedManufacturers = emptyArray())
        setupDeviceConfig(manufacturer = "Google", sdkInt = 31, languageTag = "en-US", isOnDeviceSpeechRecognitionAvailable = false)

        assertFalse(testee.isVoiceSearchSupported)
    }

    @Test
    fun whenManufacturerIsInExcludedManufacturersListThenIsVoiceSearchSupportedFalse() {
        setupRemoteConfig(voiceSearchEnabled = true, minSdk = 30, excludedManufacturers = arrayOf("Google"))
        setupDeviceConfig(manufacturer = "Google", sdkInt = 31, languageTag = "en-US", isOnDeviceSpeechRecognitionAvailable = true)

        assertFalse(testee.isVoiceSearchSupported)
    }

    @Test
    fun whenVoiceSearchNotSupportedThenShouldShowVoiceSearchFalse() {
        setupRemoteConfig(voiceSearchEnabled = true, minSdk = 30, excludedManufacturers = emptyArray())
        setupDeviceConfig(manufacturer = "Google", sdkInt = 31, languageTag = "en-US", isOnDeviceSpeechRecognitionAvailable = false)

        val result = testee.shouldShowVoiceSearch(
            isEditing = true,
            urlLoaded = "https://duckduckgo.com/?q=hello",
        )

        assertFalse(result)
    }

    @Test
    fun whenVoiceSearchSupportedAndIsEditingUrlThenShouldShowVoiceSearchTrue() {
        setupRemoteConfig(voiceSearchEnabled = true, minSdk = 30, excludedManufacturers = emptyArray())
        setupDeviceConfig(manufacturer = "Google", sdkInt = 31, languageTag = "en-US", isOnDeviceSpeechRecognitionAvailable = true)

        val result = testee.shouldShowVoiceSearch(
            isEditing = true,
            urlLoaded = "www.fb.com",
        )

        assertTrue(result)
    }

    @Test
    fun whenVoiceSearchSupportedAndIsEditingSERPThenShouldShowVoiceSearchTrue() {
        setupRemoteConfig(voiceSearchEnabled = true, minSdk = 30, excludedManufacturers = emptyArray())
        setupDeviceConfig(manufacturer = "Google", sdkInt = 31, languageTag = "en-US", isOnDeviceSpeechRecognitionAvailable = true)

        val result = testee.shouldShowVoiceSearch(
            isEditing = true,
            urlLoaded = "https://duckduckgo.com/?q=hello",
        )

        assertTrue(result)
    }

    @Test
    fun whenVoiceSearchSupportedAndUrlShownInAddressBarThenShouldShowVoiceSearchFalse() {
        setupRemoteConfig(voiceSearchEnabled = true, minSdk = 30, excludedManufacturers = emptyArray())
        setupDeviceConfig(manufacturer = "Google", sdkInt = 31, languageTag = "en-US", isOnDeviceSpeechRecognitionAvailable = true)

        val result = testee.shouldShowVoiceSearch(
            isEditing = false,
            urlLoaded = "www.fb.com",
        )

        assertFalse(result)
    }

    @Test
    fun whenVoiceSearchSupportedAndSERPShownThenShouldShowVoiceSearchTrue() {
        setupRemoteConfig(voiceSearchEnabled = true, minSdk = 30, excludedManufacturers = emptyArray())
        setupDeviceConfig(manufacturer = "Google", sdkInt = 31, languageTag = "en-US", isOnDeviceSpeechRecognitionAvailable = true)

        val result = testee.shouldShowVoiceSearch(
            isEditing = false,
            urlLoaded = "https://duckduckgo.com/?q=hello",
        )

        assertTrue(result)
    }

    @Test
    fun whenVoiceSearchSupportedAndUrlEmptyThenShouldShowVoiceSearchTrue() {
        setupRemoteConfig(voiceSearchEnabled = true, minSdk = 30, excludedManufacturers = emptyArray())
        setupDeviceConfig(manufacturer = "Google", sdkInt = 31, languageTag = "en-US", isOnDeviceSpeechRecognitionAvailable = true)

        val result = testee.shouldShowVoiceSearch(
            isEditing = false,
            urlLoaded = "",
        )

        assertTrue(result)
    }

    @Test
    fun whenFeatureIsDisabledThenShouldShowVoiceSearchFalse() {
        setupRemoteConfig(voiceSearchEnabled = false, minSdk = 30, excludedManufacturers = emptyArray())
        setupDeviceConfig(manufacturer = "Google", sdkInt = 31, languageTag = "en-US", isOnDeviceSpeechRecognitionAvailable = true)

        val result = testee.shouldShowVoiceSearch(
            isEditing = false,
            urlLoaded = "",
        )

        assertFalse(result)
    }

    private fun setupRemoteConfig(voiceSearchEnabled: Boolean, minSdk: Int?, excludedManufacturers: Array<String>) {
        whenever(voiceSearchFeature.self()).thenReturn(
            object : Toggle {
                override fun isEnabled(): Boolean {
                    return voiceSearchEnabled
                }

                override fun setEnabled(state: State) {
                    TODO("Not yet implemented")
                }

                override fun getRawStoredState(): State? {
                    TODO("Not yet implemented")
                }
            },
        )
        whenever(voiceSearchFeatureRepository.minVersion).thenReturn(minSdk)
        whenever(voiceSearchFeatureRepository.manufacturerExceptions).thenReturn(CopyOnWriteArrayList(excludedManufacturers.map { Manufacturer(it) }))
    }

    private fun setupDeviceConfig(manufacturer: String, sdkInt: Int, languageTag: String, isOnDeviceSpeechRecognitionAvailable: Boolean) {
        whenever(configProvider.get()).thenReturn(
            VoiceSearchAvailabilityConfig(manufacturer, sdkInt, languageTag, isOnDeviceSpeechRecognitionAvailable),
        )
    }
}
