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

import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.voice.impl.language.LanguageSupportChecker
import com.duckduckgo.voice.impl.remoteconfig.Locale
import com.duckduckgo.voice.impl.remoteconfig.Manufacturer
import com.duckduckgo.voice.impl.remoteconfig.VoiceSearchFeature
import com.duckduckgo.voice.impl.remoteconfig.VoiceSearchFeatureRepository
import com.duckduckgo.voice.store.VoiceSearchRepository
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.CopyOnWriteArrayList

class RealVoiceSearchAvailabilityTest {
    @Mock
    private lateinit var configProvider: VoiceSearchAvailabilityConfigProvider

    private val voiceSearchFeature = FakeFeatureToggleFactory.create(VoiceSearchFeature::class.java)

    @Mock
    private lateinit var voiceSearchFeatureRepository: VoiceSearchFeatureRepository

    @Mock
    private lateinit var voiceSearchRepository: VoiceSearchRepository

    @Mock
    private lateinit var languageSupportChecker: LanguageSupportChecker

    private lateinit var testee: RealVoiceSearchAvailability

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        testee = RealVoiceSearchAvailability(
            configProvider,
            voiceSearchFeature,
            voiceSearchFeatureRepository,
            voiceSearchRepository,
            languageSupportChecker,
        )
        whenever(languageSupportChecker.isLanguageSupported()).thenReturn(true)
    }

    @Test
    fun whenDeviceHasValidConfigThenIsVoiceSearchSupportedTrue() {
        setupRemoteConfig(voiceSearchEnabled = true, minSdk = 30, excludedManufacturers = emptyArray(), excludedLocales = emptyArray())
        setupDeviceConfig(manufacturer = "Google", sdkInt = 31, languageTag = "en-US", isOnDeviceSpeechRecognitionAvailable = true)
        setupUserSettings(true)

        assertTrue(testee.isVoiceSearchSupported)
    }

    @Test
    fun whenDeviceHasValidConfigAndLanguageIsNotSupportedThenIsVoiceSearchSupportedFalse() {
        whenever(languageSupportChecker.isLanguageSupported()).thenReturn(false)

        setupRemoteConfig(voiceSearchEnabled = true, minSdk = 30, excludedManufacturers = emptyArray(), excludedLocales = emptyArray())
        setupDeviceConfig(manufacturer = "Google", sdkInt = 31, languageTag = "en-US", isOnDeviceSpeechRecognitionAvailable = true)
        setupUserSettings(true)

        assertFalse(testee.isVoiceSearchSupported)
    }

    @Test
    fun whenDeviceHasValidConfigAndMinVersionIsNullThenIsVoiceSearchSupportedTrue() {
        setupRemoteConfig(voiceSearchEnabled = true, minSdk = null, excludedManufacturers = emptyArray(), excludedLocales = emptyArray())
        setupDeviceConfig(manufacturer = "Google", sdkInt = 31, languageTag = "en-US", isOnDeviceSpeechRecognitionAvailable = true)
        setupUserSettings(true)

        assertTrue(testee.isVoiceSearchSupported)
    }

    @Test
    fun whenDeviceHasInvalidLanguageThenIsVoiceSearchSupportedFalse() {
        setupRemoteConfig(voiceSearchEnabled = true, minSdk = 30, excludedManufacturers = emptyArray(), excludedLocales = arrayOf("en-UK"))
        setupDeviceConfig(manufacturer = "Google", sdkInt = 31, languageTag = "en-UK", isOnDeviceSpeechRecognitionAvailable = true)
        setupUserSettings(true)

        assertFalse(testee.isVoiceSearchSupported)
    }

    @Test
    fun whenDeviceHasInvalidSdkThenIsVoiceSearchSupportedFalse() {
        setupRemoteConfig(voiceSearchEnabled = true, minSdk = 33, excludedManufacturers = emptyArray(), excludedLocales = emptyArray())
        setupDeviceConfig(manufacturer = "Google", sdkInt = 31, languageTag = "en-UK", isOnDeviceSpeechRecognitionAvailable = true)
        setupUserSettings(true)

        assertFalse(testee.isVoiceSearchSupported)
    }

    @Test
    fun whenDeviceHasNoSupportForOnDeviceSpeechRecognitionThenIsVoiceSearchSupportedFalse() {
        setupRemoteConfig(voiceSearchEnabled = true, minSdk = 30, excludedManufacturers = emptyArray(), excludedLocales = emptyArray())
        setupDeviceConfig(manufacturer = "Google", sdkInt = 31, languageTag = "en-US", isOnDeviceSpeechRecognitionAvailable = false)
        setupUserSettings(true)

        assertFalse(testee.isVoiceSearchSupported)
    }

    @Test
    fun whenManufacturerIsInExcludedManufacturersListThenIsVoiceSearchSupportedFalse() {
        setupRemoteConfig(voiceSearchEnabled = true, minSdk = 30, excludedManufacturers = arrayOf("Google"), excludedLocales = emptyArray())
        setupDeviceConfig(manufacturer = "Google", sdkInt = 31, languageTag = "en-US", isOnDeviceSpeechRecognitionAvailable = true)
        setupUserSettings(true)

        assertFalse(testee.isVoiceSearchSupported)
    }

    @Test
    fun whenVoiceSearchNotSupportedThenShouldShowVoiceSearchFalse() {
        setupRemoteConfig(voiceSearchEnabled = true, minSdk = 30, excludedManufacturers = emptyArray(), excludedLocales = emptyArray())
        setupDeviceConfig(manufacturer = "Google", sdkInt = 31, languageTag = "en-US", isOnDeviceSpeechRecognitionAvailable = false)
        setupUserSettings(true)

        val result = testee.shouldShowVoiceSearch(
            hasFocus = true,
            query = "",
            hasQueryChanged = false,
            urlLoaded = "https://duckduckgo.com/?q=hello",
        )

        assertFalse(result)
    }

    @Test
    fun whenVoiceSearchSupportedAndIsEditingUrlAndUserDisabledThenShouldShowVoiceSearchFalse() {
        setupRemoteConfig(voiceSearchEnabled = true, minSdk = 30, excludedManufacturers = emptyArray(), excludedLocales = emptyArray())
        setupDeviceConfig(manufacturer = "Google", sdkInt = 31, languageTag = "en-US", isOnDeviceSpeechRecognitionAvailable = true)
        setupUserSettings(false)

        val result = testee.shouldShowVoiceSearch(
            hasFocus = true,
            query = "",
            hasQueryChanged = false,
            urlLoaded = "www.fb.com",
        )

        assertFalse(result)
    }

    @Test
    fun whenVoiceSearchSupportedAndIsEditingUrlWithUnchangedQueryThenShouldShowVoiceSearchTrue() {
        setupRemoteConfig(voiceSearchEnabled = true, minSdk = 30, excludedManufacturers = emptyArray(), excludedLocales = emptyArray())
        setupDeviceConfig(manufacturer = "Google", sdkInt = 31, languageTag = "en-US", isOnDeviceSpeechRecognitionAvailable = true)

        setupUserSettings(true)

        val result = testee.shouldShowVoiceSearch(
            hasFocus = true,
            query = "http://www.fb.com",
            hasQueryChanged = false,
            urlLoaded = "http://www.fb.com",
        )

        assertTrue(result)
    }

    @Test
    fun whenVoiceSearchSupportedAndIsEditingUrlWithEmptyQueryThenShouldShowVoiceSearchTrue() {
        setupRemoteConfig(voiceSearchEnabled = true, minSdk = 30, excludedManufacturers = emptyArray(), excludedLocales = emptyArray())
        setupDeviceConfig(manufacturer = "Google", sdkInt = 31, languageTag = "en-US", isOnDeviceSpeechRecognitionAvailable = true)
        setupUserSettings(true)

        val result = testee.shouldShowVoiceSearch(
            hasFocus = true,
            query = "",
            hasQueryChanged = true,
            urlLoaded = "http://www.fb.com",
        )

        assertTrue(result)
    }

    @Test
    fun whenVoiceSearchSupportedAndIsEditingUrlWithChangedQueryThenShouldShowVoiceSearchTrue() {
        setupRemoteConfig(voiceSearchEnabled = true, minSdk = 30, excludedManufacturers = emptyArray(), excludedLocales = emptyArray())
        setupDeviceConfig(manufacturer = "Google", sdkInt = 31, languageTag = "en-US", isOnDeviceSpeechRecognitionAvailable = true)

        setupUserSettings(true)

        val result = testee.shouldShowVoiceSearch(
            hasFocus = true,
            query = "http://www.fb.com",
            hasQueryChanged = true,
            urlLoaded = "http://www.fb.com",
        )

        assertFalse(result)
    }

    @Test
    fun whenVoiceSearchSupportedAndUrlShownInAddressBarWithNoFocusThenShouldShowVoiceSearchFalse() {
        setupRemoteConfig(voiceSearchEnabled = true, minSdk = 30, excludedManufacturers = emptyArray(), excludedLocales = emptyArray())
        setupDeviceConfig(manufacturer = "Google", sdkInt = 31, languageTag = "en-US", isOnDeviceSpeechRecognitionAvailable = true)
        setupUserSettings(true)

        val result = testee.shouldShowVoiceSearch(
            hasFocus = false,
            query = "",
            hasQueryChanged = false,
            urlLoaded = "www.fb.com",
        )

        assertFalse(result)
    }

    @Test
    fun whenVoiceSearchSupportedAndUrlShownInAddressBarWithFocusAndEmptyUnchangedQueryThenShouldShowVoiceSearchTrue() {
        setupRemoteConfig(voiceSearchEnabled = true, minSdk = 30, excludedManufacturers = emptyArray(), excludedLocales = emptyArray())
        setupDeviceConfig(manufacturer = "Google", sdkInt = 31, languageTag = "en-US", isOnDeviceSpeechRecognitionAvailable = true)
        setupUserSettings(true)

        val result = testee.shouldShowVoiceSearch(
            hasFocus = true,
            query = "",
            hasQueryChanged = false,
            urlLoaded = "www.fb.com",
        )

        assertTrue(result)
    }

    @Test
    fun whenVoiceSearchSupportedAndUrlShownInAddressBarWithFocusAndEmptyChangedQueryThenShouldShowVoiceSearchTrue() {
        setupRemoteConfig(voiceSearchEnabled = true, minSdk = 30, excludedManufacturers = emptyArray(), excludedLocales = emptyArray())
        setupDeviceConfig(manufacturer = "Google", sdkInt = 31, languageTag = "en-US", isOnDeviceSpeechRecognitionAvailable = true)
        setupUserSettings(true)

        val result = testee.shouldShowVoiceSearch(
            hasFocus = true,
            query = "",
            hasQueryChanged = true,
            urlLoaded = "www.fb.com",
        )

        assertTrue(result)
    }

    @Test
    fun whenVoiceSearchSupportedAndUrlShownInAddressBarWithFocusAndNonEmptyChangedQueryThenShouldShowVoiceSearchFalse() {
        setupRemoteConfig(voiceSearchEnabled = true, minSdk = 30, excludedManufacturers = emptyArray(), excludedLocales = emptyArray())
        setupDeviceConfig(manufacturer = "Google", sdkInt = 31, languageTag = "en-US", isOnDeviceSpeechRecognitionAvailable = true)
        setupUserSettings(true)

        val result = testee.shouldShowVoiceSearch(
            hasFocus = true,
            query = "duck",
            hasQueryChanged = true,
            urlLoaded = "www.fb.com",
        )

        assertFalse(result)
    }

    @Test
    fun whenVoiceSearchSupportedAndUrlEmptyThenShouldShowVoiceSearchTrue() {
        setupRemoteConfig(voiceSearchEnabled = true, minSdk = 30, excludedManufacturers = emptyArray(), excludedLocales = emptyArray())
        setupDeviceConfig(manufacturer = "Google", sdkInt = 31, languageTag = "en-US", isOnDeviceSpeechRecognitionAvailable = true)
        setupUserSettings(true)

        val result = testee.shouldShowVoiceSearch(
            hasFocus = true,
            query = "",
            urlLoaded = "",
            hasQueryChanged = false,
        )

        assertTrue(result)
    }

    @Test
    fun whenVoiceSearchSupportedAndUrlEmptyWithNoFocusThenShouldShowVoiceSearchTrue() {
        setupRemoteConfig(voiceSearchEnabled = true, minSdk = 30, excludedManufacturers = emptyArray(), excludedLocales = emptyArray())
        setupDeviceConfig(manufacturer = "Google", sdkInt = 31, languageTag = "en-US", isOnDeviceSpeechRecognitionAvailable = true)
        setupUserSettings(true)

        val result = testee.shouldShowVoiceSearch(
            hasFocus = false,
            query = "",
            urlLoaded = "",
            hasQueryChanged = false,
        )

        assertTrue(result)
    }

    @Test
    fun whenFeatureIsDisabledThenShouldShowVoiceSearchFalse() {
        setupRemoteConfig(voiceSearchEnabled = false, minSdk = 30, excludedManufacturers = emptyArray(), excludedLocales = emptyArray())
        setupDeviceConfig(manufacturer = "Google", sdkInt = 31, languageTag = "en-US", isOnDeviceSpeechRecognitionAvailable = true)
        setupUserSettings(true)

        val result = testee.shouldShowVoiceSearch(
            hasFocus = false,
            query = "",
            hasQueryChanged = false,
            urlLoaded = "",
        )

        assertFalse(result)
    }

    @Test
    fun whenVoiceSearchSupportedAndSERPShownThenShouldShowVoiceSearchTrue() {
        setupRemoteConfig(voiceSearchEnabled = true, minSdk = 30, excludedManufacturers = emptyArray(), excludedLocales = emptyArray())
        setupDeviceConfig(manufacturer = "Google", sdkInt = 31, languageTag = "en-US", isOnDeviceSpeechRecognitionAvailable = true)
        setupUserSettings(true)

        val result = testee.shouldShowVoiceSearch(
            hasFocus = false,
            query = "",
            hasQueryChanged = false,
            urlLoaded = "https://duckduckgo.com/?q=hello",
        )

        assertTrue(result)
    }

    @Test
    fun whenVoiceSearchSupportedAndIsEditingSERPWithUnchangedQueryThenShouldShowVoiceSearchTrue() {
        setupRemoteConfig(voiceSearchEnabled = true, minSdk = 30, excludedManufacturers = emptyArray(), excludedLocales = emptyArray())
        setupDeviceConfig(manufacturer = "Google", sdkInt = 31, languageTag = "en-US", isOnDeviceSpeechRecognitionAvailable = true)
        setupUserSettings(true)

        val result = testee.shouldShowVoiceSearch(
            hasFocus = true,
            query = "duck",
            hasQueryChanged = false,
            urlLoaded = "https://duckduckgo.com/?q=hello",
        )

        assertTrue(result)
    }

    @Test
    fun whenVoiceSearchSupportedAndIsEditingSERPWithChangedQueryThenShouldShowVoiceSearchFalse() {
        setupRemoteConfig(voiceSearchEnabled = true, minSdk = 30, excludedManufacturers = emptyArray(), excludedLocales = emptyArray())
        setupDeviceConfig(manufacturer = "Google", sdkInt = 31, languageTag = "en-US", isOnDeviceSpeechRecognitionAvailable = true)
        setupUserSettings(true)

        val result = testee.shouldShowVoiceSearch(
            hasFocus = true,
            query = "duck",
            hasQueryChanged = true,
            urlLoaded = "https://duckduckgo.com/?q=hello",
        )

        assertFalse(result)
    }

    @Test
    fun whenVoiceSearchSupportedAndIsEditingSERPWithEmptyQueryThenShouldShowVoiceSearchTrue() {
        setupRemoteConfig(voiceSearchEnabled = true, minSdk = 30, excludedManufacturers = emptyArray(), excludedLocales = emptyArray())
        setupDeviceConfig(manufacturer = "Google", sdkInt = 31, languageTag = "en-US", isOnDeviceSpeechRecognitionAvailable = true)
        setupUserSettings(true)

        val result = testee.shouldShowVoiceSearch(
            hasFocus = true,
            query = "",
            hasQueryChanged = true,
            urlLoaded = "https://duckduckgo.com/?q=hello",
        )

        assertTrue(result)
    }

    @Test
    fun whenModelIsPixel6ThenDefaultUserSettingsTrue() {
        setupRemoteConfig(voiceSearchEnabled = true, minSdk = 30, excludedManufacturers = emptyArray(), excludedLocales = emptyArray())
        setupDeviceConfig(manufacturer = "Google", sdkInt = 31, languageTag = "en-US", isOnDeviceSpeechRecognitionAvailable = true)
        whenever(voiceSearchRepository.getHasAcceptedRationaleDialog()).thenReturn(true)

        testee.isVoiceSearchAvailable

        verify(voiceSearchRepository).isVoiceSearchUserEnabled(eq(true))
    }

    @Test
    fun whenDeviceHadNotPreviouslyAcceptedVoiceRationaleThenDefaultUserSettingsFalse() {
        setupRemoteConfig(voiceSearchEnabled = true, minSdk = 30, excludedManufacturers = emptyArray(), excludedLocales = emptyArray())
        setupDeviceConfig(manufacturer = "Google", sdkInt = 31, languageTag = "en-US", isOnDeviceSpeechRecognitionAvailable = true)
        whenever(voiceSearchRepository.getHasAcceptedRationaleDialog()).thenReturn(false)

        testee.isVoiceSearchAvailable

        verify(voiceSearchRepository).isVoiceSearchUserEnabled(eq(false))
    }

    private fun setupRemoteConfig(voiceSearchEnabled: Boolean, minSdk: Int?, excludedManufacturers: Array<String>, excludedLocales: Array<String>) {
        voiceSearchFeature.self().setRawStoredState(State(voiceSearchEnabled))
        whenever(voiceSearchFeatureRepository.minVersion).thenReturn(minSdk)
        whenever(voiceSearchFeatureRepository.manufacturerExceptions).thenReturn(CopyOnWriteArrayList(excludedManufacturers.map { Manufacturer(it) }))
        whenever(voiceSearchFeatureRepository.localeExceptions).thenReturn(CopyOnWriteArrayList(excludedLocales.map { Locale(it) }))
    }

    private fun setupDeviceConfig(
        manufacturer: String,
        sdkInt: Int,
        languageTag: String,
        isOnDeviceSpeechRecognitionAvailable: Boolean,
    ) {
        whenever(configProvider.get()).thenReturn(
            VoiceSearchAvailabilityConfig(manufacturer, sdkInt, languageTag, isOnDeviceSpeechRecognitionAvailable),
        )
    }

    private fun setupUserSettings(enabled: Boolean) {
        whenever(voiceSearchRepository.isVoiceSearchUserEnabled(any())).thenReturn(enabled)
    }
}
