/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.duckchat.impl.inputscreen.ui.metrics.retention

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.api.DuckAiFeatureState
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class InputScreenRetentionMonitorTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val temporaryFolder = TemporaryFolder.builder().assureDeletion().build().also { it.create() }
    private val testDataStore: DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = coroutineTestRule.testScope,
            produceFile = { temporaryFolder.newFile("temp.preferences_pb") },
        )

    private val mockDuckAiFeatureState: DuckAiFeatureState = mock()
    private val mockPixel: Pixel = mock()
    private val mockLifecycleOwner: LifecycleOwner = mock()
    private val mockTimeProvider: TimeProvider = mock()

    private val showInputScreenFlow = MutableStateFlow(true)

    private lateinit var testee: InputScreenRetentionMonitor

    @Before
    fun setup() {
        whenever(mockDuckAiFeatureState.showInputScreen).thenReturn(showInputScreenFlow)

        testee = InputScreenRetentionMonitor(
            coroutineScope = coroutineTestRule.testScope,
            retentionMonitorDataStore = testDataStore,
            duckAiFeatureState = mockDuckAiFeatureState,
            pixel = mockPixel,
            timeProvider = mockTimeProvider,
        )
    }

    @Test
    fun `when feature was enabled and still enabled after 24 hours then pixel fired with still enabled true`() = runTest {
        val initialTime = ZonedDateTime.now(ZoneId.of("America/New_York"))
        val laterTime = initialTime.plusHours(25)

        // First call - feature was enabled in a previous session
        whenever(mockTimeProvider.nowInEasternTime()).thenReturn(initialTime)
        showInputScreenFlow.value = true
        testee.onResume(mockLifecycleOwner)

        // Second call - feature is still enabled after 24+ hours (mock time has advanced)
        whenever(mockTimeProvider.nowInEasternTime()).thenReturn(laterTime)
        testee.onResume(mockLifecycleOwner)

        verify(mockPixel).fire(
            pixel = eq(DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_DAILY_RETENTION),
            parameters = eq(mapOf("still_enabled" to "true")),
            encodedParameters = any(),
            type = any(),
        )
    }

    @Test
    fun `when feature was enabled but now disabled after 24 hours then pixel fired with still enabled false`() = runTest {
        val initialTime = ZonedDateTime.now(ZoneId.of("America/New_York"))
        val laterTime = initialTime.plusHours(25)

        // First call - feature was enabled in a previous session
        whenever(mockTimeProvider.nowInEasternTime()).thenReturn(initialTime)
        showInputScreenFlow.value = true
        testee.onResume(mockLifecycleOwner)

        // Second call - feature is now disabled after 24+ hours (mock time has advanced)
        whenever(mockTimeProvider.nowInEasternTime()).thenReturn(laterTime)
        showInputScreenFlow.value = false
        testee.onResume(mockLifecycleOwner)

        verify(mockPixel).fire(
            pixel = eq(DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_DAILY_RETENTION),
            parameters = eq(mapOf("still_enabled" to "false")),
            encodedParameters = any(),
            type = any(),
        )
    }

    @Test
    fun `when feature was disabled then no pixel fired until enabled`() = runTest {
        val initialTime = ZonedDateTime.now(ZoneId.of("America/New_York"))

        // First call - feature was disabled in a previous session
        whenever(mockTimeProvider.nowInEasternTime()).thenReturn(initialTime)
        val laterTime = initialTime.plusHours(25)
        showInputScreenFlow.value = false
        testee.onResume(mockLifecycleOwner)

        // Second call - feature is now enabled after 24+ hours, but was disabled initially
        whenever(mockTimeProvider.nowInEasternTime()).thenReturn(laterTime)
        showInputScreenFlow.value = true
        testee.onResume(mockLifecycleOwner)

        // Third call - feature is now enabled for more than 24+ hours
        whenever(mockTimeProvider.nowInEasternTime()).thenReturn(laterTime.plusHours(25))
        testee.onResume(mockLifecycleOwner)

        verify(mockPixel).fire(
            pixel = eq(DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_DAILY_RETENTION),
            parameters = eq(mapOf("still_enabled" to "true")),
            encodedParameters = any(),
            type = any(),
        )
    }

    @Test
    fun `when less than 24 hours have passed then no pixel fired and timer is not reset`() = runTest {
        val initialTime = ZonedDateTime.now(ZoneId.of("America/New_York"))
        val earlyTime = initialTime.plusHours(23) // Less than 24 hours
        val laterTime = initialTime.plusHours(25) // More than 24 hours

        // First call - feature was enabled in a previous session
        whenever(mockTimeProvider.nowInEasternTime()).thenReturn(initialTime)
        showInputScreenFlow.value = true
        testee.onResume(mockLifecycleOwner)

        // Second call - still enabled but less than 24 hours have passed
        whenever(mockTimeProvider.nowInEasternTime()).thenReturn(earlyTime)
        testee.onResume(mockLifecycleOwner)

        verify(mockPixel, never()).fire(DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_DAILY_RETENTION)

        // Third call - feature is still enabled after 24+ hours, send the pixel
        whenever(mockTimeProvider.nowInEasternTime()).thenReturn(laterTime)
        showInputScreenFlow.value = true
        testee.onResume(mockLifecycleOwner)

        verify(mockPixel).fire(
            pixel = eq(DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_DAILY_RETENTION),
            parameters = eq(mapOf("still_enabled" to "true")),
            encodedParameters = any(),
            type = any(),
        )
    }

    @Test
    fun `when invalid date time stored then monitor recovers`() = runTest {
        val initialTime = ZonedDateTime.now(ZoneId.of("America/New_York"))
        val laterTime = initialTime.plusHours(25)
        val recoveryTime = laterTime.plusHours(25)

        // First call - establish initial state normally
        whenever(mockTimeProvider.nowInEasternTime()).thenReturn(initialTime)
        testee.onResume(mockLifecycleOwner)

        // Manually corrupt the timestamp to simulate invalid data
        testDataStore.edit { preferences ->
            preferences[stringPreferencesKey("LAST_CHECK_DATE_TIME_ET")] = "invalid-date-time"
        }

        // Second call - feature is now disabled
        whenever(mockTimeProvider.nowInEasternTime()).thenReturn(laterTime)
        showInputScreenFlow.value = false
        testee.onResume(mockLifecycleOwner)

        // No pixel should be fired due to parsing error
        verify(mockPixel, never()).fire(DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_DAILY_RETENTION)

        // Third call - feature is still disabled and recovered from corruption
        whenever(mockTimeProvider.nowInEasternTime()).thenReturn(recoveryTime)
        testee.onResume(mockLifecycleOwner)

        verify(mockPixel).fire(
            pixel = eq(DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_DAILY_RETENTION),
            parameters = eq(mapOf("still_enabled" to "false")),
            encodedParameters = any(),
            type = any(),
        )
    }
}
