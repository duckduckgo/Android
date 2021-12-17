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
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AppInstallSharedPreferencesTest {

    private lateinit var testee: AppInstallSharedPreferences

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setup() {
        context.getSharedPreferences(AppInstallSharedPreferences.FILENAME, Context.MODE_PRIVATE)
            .edit { clear() }
        testee = AppInstallSharedPreferences(context)
    }

    @Test
    fun whenInitializedThenInstallTimestampNotYetRecorded() {
        assertFalse(testee.hasInstallTimestampRecorded())
    }

    @Test
    fun whenInstallTimestampRecordedThenTimestampMarkedAsAvailable() {
        val timestamp = 1L
        testee.installTimestamp = timestamp
        assertTrue(testee.hasInstallTimestampRecorded())
    }

    @Test
    fun whenTimestampRecordedThenSameTimestampRetrieved() {
        val timestamp = 1L
        testee.installTimestamp = timestamp
        assertEquals(timestamp, testee.installTimestamp)
    }
}
