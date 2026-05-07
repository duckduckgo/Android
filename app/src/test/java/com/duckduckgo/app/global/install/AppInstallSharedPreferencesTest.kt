/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.global.install

import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

@RunWith(AndroidJUnit4::class)
class AppInstallSharedPreferencesTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    private lateinit var testee: AppInstallSharedPreferences

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setup() {
        context.getSharedPreferences(AppInstallSharedPreferences.FILENAME, Context.MODE_PRIVATE).edit { clear() }
        testee = AppInstallSharedPreferences(
            context = context,
            appCoroutineScope = coroutineTestRule.testScope,
            dispatcherProvider = coroutineTestRule.testDispatcherProvider,
        )
    }

    @Test
    fun whenInitializedThenInstallTimestampNotYetRecorded() = runTest {
        assertFalse(testee.hasInstallTimestampRecorded())
    }

    @Test
    fun whenInstallTimestampRecordedThenTimestampMarkedAsAvailable() = runTest {
        val timestamp = 1L
        testee.installTimestamp = timestamp
        assertTrue(testee.hasInstallTimestampRecorded())
    }

    @Test
    fun whenTimestampRecordedThenSameTimestampRetrieved() = runTest {
        val timestamp = 1L
        testee.installTimestamp = timestamp
        assertEquals(timestamp, testee.installTimestamp)
    }

    @Test
    fun whenOnCreateCalledAndNoTimestampThenTimestampIsRecorded() = runTest {
        assertFalse(testee.hasInstallTimestampRecorded())

        testee.onCreate(mock())

        assertTrue(testee.hasInstallTimestampRecorded())
        assertTrue(testee.installTimestamp > 0)
    }

    @Test
    fun whenOnCreateCalledAndTimestampExistsThenTimestampIsNotOverwritten() = runTest {
        val existingTimestamp = 12345L
        testee.installTimestamp = existingTimestamp

        testee.onCreate(mock())

        assertEquals(existingTimestamp, testee.installTimestamp)
    }

    @Test
    fun whenInitializedThenDefaultBrowserAndWasEverDefaultBrowserAreFalse() = runTest {
        assertFalse(testee.defaultBrowser)
        assertFalse(testee.wasEverDefaultBrowser)
    }

    @Test
    fun whenDefaultBrowserSetToTrueThenWasEverDefaultBrowserIsTrue() = runTest {
        testee.defaultBrowser = true

        assertTrue(testee.defaultBrowser)
        assertTrue(testee.wasEverDefaultBrowser)
    }

    @Test
    fun whenDefaultBrowserSetToFalseInitiallyThenWasEverDefaultBrowserRemainsFalse() = runTest {
        testee.defaultBrowser = false

        assertFalse(testee.defaultBrowser)
        assertFalse(testee.wasEverDefaultBrowser)
    }

    @Test
    fun whenDefaultBrowserSetToFalseAfterBeingTrueThenWasEverDefaultBrowserRemainsTrue() = runTest {
        testee.defaultBrowser = true
        testee.defaultBrowser = false

        assertFalse(testee.defaultBrowser)
        assertTrue(testee.wasEverDefaultBrowser)
    }
}
