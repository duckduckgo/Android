/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.app.statistics.store

import android.content.Context
import androidx.core.content.edit
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class OfflinePixelSharedPreferencesTest {

    private lateinit var testee: OfflinePixelSharedPreferences

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setup() {
        context.getSharedPreferences(OfflinePixelSharedPreferences.FILENAME, Context.MODE_PRIVATE).edit { clear() }
        testee = OfflinePixelSharedPreferences(context)
    }

    @Test
    fun whenInitializedThenWebRendererGoneCrashCountIsZero() {
        assertEquals(0, testee.webRendererGoneCrashCount)
    }

    @Test
    fun whenWebRendererGoneCrashCountIsSetThenUpdated() {
        testee.webRendererGoneCrashCount = 2
        assertEquals(2, testee.webRendererGoneCrashCount)
    }

    @Test
    fun whenInitializedThenWebRendererGoneOtherCountIsZero() {
        assertEquals(0, testee.webRendererGoneOtherCount)
    }

    @Test
    fun whenWebRendererOtherCrashCountIsSetThenUpdated() {
        testee.webRendererGoneOtherCount = 2
        assertEquals(2, testee.webRendererGoneOtherCount)
    }
}