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

package com.duckduckgo.app.fire

import androidx.core.content.edit
import com.duckduckgo.app.fire.UnsentForgetAllPixelStoreSharedPreferences.Companion.FILENAME
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UnsentForgetAllPixelStoreSharedPreferencesTest {

    private lateinit var testee: UnsentForgetAllPixelStoreSharedPreferences

    @Before
    fun setup() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences(FILENAME, 0).edit { clear() }
        testee = UnsentForgetAllPixelStoreSharedPreferences(context)
    }

    @Test
    fun whenFirstInitialisedThenPendingCountIs0() {
        assertEquals(0, testee.pendingPixelCountClearData)
    }

    @Test
    fun whenIncrementedOneThenValueIncrementsTo1() {
        testee.incrementCount()
        assertEquals(1, testee.pendingPixelCountClearData)
    }

    @Test
    fun whenIncrementedManyTimesThenIncrementsAsExpected() {
        testee.incrementCount()
        testee.incrementCount()
        testee.incrementCount()
        assertEquals(3, testee.pendingPixelCountClearData)
    }

    @Test
    fun whenFirstInitialisedThenLastClearTimestampIs0() {
        assertEquals(0, testee.lastClearTimestamp)
    }

    @Test
    fun whenIncrementedThenTimestampUpdated() {
        testee.incrementCount()
        assertTrue(testee.lastClearTimestamp > 0)
    }

    @Test
    fun whenResetWhenAlready0ThenCountIs0() {
        testee.resetCount()
        assertEquals(0, testee.pendingPixelCountClearData)
    }

    @Test
    fun whenResetFromAbove0ThenCountIs0() {
        testee.incrementCount()
        testee.resetCount()
        assertEquals(0, testee.pendingPixelCountClearData)
    }
}