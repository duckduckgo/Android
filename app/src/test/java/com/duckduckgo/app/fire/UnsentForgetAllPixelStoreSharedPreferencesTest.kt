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

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.fire.UnsentForgetAllPixelStoreSharedPreferences.Companion.FILENAME
import com.duckduckgo.app.fire.UnsentForgetAllPixelStoreSharedPreferences.Companion.KEY_UNSENT_CLEAR_PIXELS
import com.duckduckgo.browsermode.api.BrowserMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UnsentForgetAllPixelStoreSharedPreferencesTest {

    private lateinit var testee: UnsentForgetAllPixelStoreSharedPreferences

    @SuppressLint("DenyListedApi")
    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences(FILENAME, 0).edit { clear() }
        testee = UnsentForgetAllPixelStoreSharedPreferences(context)
    }

    @Test
    fun whenFirstInitialisedThenNoPendingCounts() {
        assertEquals(emptyMap<BrowserMode, Int>(), testee.pendingPixelCountsClearData)
    }

    @Test
    fun whenRegularIncrementedThenRegularCountIncrements() {
        testee.incrementCount(BrowserMode.REGULAR)
        assertEquals(mapOf(BrowserMode.REGULAR to 1), testee.pendingPixelCountsClearData)
    }

    @Test
    fun whenBothModesIncrementedThenCountsRemainSeparate() {
        testee.incrementCount(BrowserMode.REGULAR)
        testee.incrementCount(BrowserMode.FIRE)
        testee.incrementCount(BrowserMode.FIRE)
        assertEquals(mapOf(BrowserMode.REGULAR to 1, BrowserMode.FIRE to 2), testee.pendingPixelCountsClearData)
    }

    @Test
    fun whenFirstInitialisedThenLastClearTimestampIs0() {
        assertEquals(0, testee.lastClearTimestamp)
    }

    @Test
    fun whenIncrementedThenTimestampUpdated() {
        testee.incrementCount(BrowserMode.REGULAR)
        assertTrue(testee.lastClearTimestamp > 0)
    }

    @Test
    fun whenResetWhenAlready0ThenNoCountsArePending() {
        testee.resetCount(BrowserMode.REGULAR)
        assertEquals(emptyMap<BrowserMode, Int>(), testee.pendingPixelCountsClearData)
    }

    @Test
    fun whenResetOneModeThenOtherModeRemainsPending() {
        testee.incrementCount(BrowserMode.REGULAR)
        testee.incrementCount(BrowserMode.FIRE)
        testee.resetCount(BrowserMode.REGULAR)
        assertEquals(mapOf(BrowserMode.FIRE to 1), testee.pendingPixelCountsClearData)
    }

    @Test
    fun whenLegacyCountExistsThenItIsReadAsRegular() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences(FILENAME, 0).edit { putInt(KEY_UNSENT_CLEAR_PIXELS, 3) }

        assertEquals(mapOf(BrowserMode.REGULAR to 3), testee.pendingPixelCountsClearData)
    }
}
