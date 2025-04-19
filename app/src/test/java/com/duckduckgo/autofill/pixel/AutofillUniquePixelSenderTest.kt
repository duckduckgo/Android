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

package com.duckduckgo.autofill.pixel

import android.content.Context
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_DEVICE_CAPABILITY_CAPABLE
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_DEVICE_CAPABILITY_DEVICE_AUTH_DISABLED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_DEVICE_CAPABILITY_SECURE_STORAGE_UNAVAILABLE
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_DEVICE_CAPABILITY_SECURE_STORAGE_UNAVAILABLE_AND_DEVICE_AUTH_DISABLED
import com.duckduckgo.autofill.impl.pixel.AutofillUniquePixelSender
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.test.api.InMemorySharedPreferences
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AutofillUniquePixelSenderTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val mockPixel: Pixel = mock()
    private val context: Context = mock()
    private val fakePreferences = InMemorySharedPreferences()

    private val testee = AutofillUniquePixelSender(
        pixel = mockPixel,
        context = context,
        appCoroutineScope = TestScope(),
        dispatchers = coroutineTestRule.testDispatcherProvider,
    )

    @Before
    fun before() {
        whenever(context.getSharedPreferences(SHARED_PREFS_FILE, Context.MODE_PRIVATE)).thenReturn(fakePreferences)
    }

    @Test
    fun whenSharedPreferencesHasNoValueThenReturnsFalse() = runTest {
        configureSharePreferencesMissingKey()
        assertFalse(testee.hasDeterminedCapabilities())
    }

    @Test
    fun whenPixelSentThenSharedPreferencesRecordsPixelWasSentBefore() = runTest {
        testee.sendCapabilitiesPixel(secureStorageAvailable = true, deviceAuthAvailable = true)
        assertTrue(testee.hasDeterminedCapabilities())
    }

    @Test
    fun whenSecureStorageIsAvailableAndDeviceAuthEnabledThenSendCorrectPixel() {
        testee.sendCapabilitiesPixel(secureStorageAvailable = true, deviceAuthAvailable = true)
        verify(mockPixel).fire(AUTOFILL_DEVICE_CAPABILITY_CAPABLE)
    }

    @Test
    fun whenSecureStorageIsAvailableButDeviceAuthDisabledThenSendCorrectPixel() {
        testee.sendCapabilitiesPixel(secureStorageAvailable = true, deviceAuthAvailable = false)
        verify(mockPixel).fire(AUTOFILL_DEVICE_CAPABILITY_DEVICE_AUTH_DISABLED)
    }

    @Test
    fun whenDeviceAuthEnabledButSecureStorageIsNotAvailableThenSendCorrectPixel() {
        testee.sendCapabilitiesPixel(secureStorageAvailable = false, deviceAuthAvailable = true)
        verify(mockPixel).fire(AUTOFILL_DEVICE_CAPABILITY_SECURE_STORAGE_UNAVAILABLE)
    }

    @Test
    fun whenDeviceAuthDisabledAndSecureStorageIsNotAvailableThenSendCorrectPixel() {
        testee.sendCapabilitiesPixel(secureStorageAvailable = false, deviceAuthAvailable = false)
        verify(mockPixel).fire(AUTOFILL_DEVICE_CAPABILITY_SECURE_STORAGE_UNAVAILABLE_AND_DEVICE_AUTH_DISABLED)
    }

    private fun configureSharePreferencesMissingKey() {
        fakePreferences.remove(KEY_CAPABILITIES_DETERMINED)
    }

    companion object {
        private const val SHARED_PREFS_FILE = "com.duckduckgo.autofill.pixel.AutofillPixelSender"
        private const val KEY_CAPABILITIES_DETERMINED = "KEY_CAPABILITIES_DETERMINED"
    }
}
