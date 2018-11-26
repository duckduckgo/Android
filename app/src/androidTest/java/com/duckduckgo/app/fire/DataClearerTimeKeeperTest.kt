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

import com.duckduckgo.app.settings.SettingsAutomaticallyClearWhenFragment.ClearWhenOption
import com.duckduckgo.app.settings.SettingsAutomaticallyClearWhenFragment.ClearWhenOption.*
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import java.util.*
import java.util.concurrent.TimeUnit

@RunWith(Parameterized::class)
class DataClearerTimeKeeperTest(private val testCase: TestCase) {

    private val mockSettingsDataStore: SettingsDataStore = mock()
    private val testee: DataClearerTimeKeeper = DataClearerTimeKeeper(mockSettingsDataStore)

    companion object {

        @JvmStatic
        @Parameters(name = "Test case: {index} - {0}")
        fun testData(): Array<TestCase> {

            val timeNow = System.currentTimeMillis()

            return arrayOf(
                // no background time recorded - always expected to indicate not enough time passed
                TestCase(false, APP_EXIT_ONLY, TimeUnit.MINUTES.toMillis(5), timeNow, false),
                TestCase(false, APP_EXIT_ONLY, TimeUnit.MINUTES.toMillis(0), timeNow, false),
                TestCase(false, APP_EXIT_ONLY, TimeUnit.MINUTES.toMillis(-5), timeNow, false),
                TestCase(false, APP_EXIT_OR_5_MINS, TimeUnit.MINUTES.toMillis(-5), timeNow, false),
                TestCase(false, APP_EXIT_OR_5_MINS, TimeUnit.MINUTES.toMillis(4), timeNow, false),
                TestCase(false, APP_EXIT_OR_5_MINS, TimeUnit.MINUTES.toMillis(0), timeNow, false),
                TestCase(false, APP_EXIT_OR_5_MINS, TimeUnit.MINUTES.toMillis(5), timeNow, false),

                // background time recorded - will always return true since this is APP_EXIT_ONLY
                TestCase(true, APP_EXIT_ONLY, TimeUnit.MINUTES.toMillis(5), timeNow),
                TestCase(true, APP_EXIT_ONLY, TimeUnit.MINUTES.toMillis(0), timeNow),
                TestCase(true, APP_EXIT_ONLY, TimeUnit.MINUTES.toMillis(-5), timeNow),

                // background time recorded - will return true when duration is >= 5 mins
                TestCase(false, APP_EXIT_OR_5_MINS, TimeUnit.MINUTES.toMillis(4), timeNow),
                TestCase(true, APP_EXIT_OR_5_MINS, TimeUnit.MINUTES.toMillis(5), timeNow),
                TestCase(true, APP_EXIT_OR_5_MINS, TimeUnit.MINUTES.toMillis(6), timeNow),

                // background time recorded - will return true when duration is >= 15 mins
                TestCase(false, APP_EXIT_OR_15_MINS, TimeUnit.MINUTES.toMillis(14), timeNow),
                TestCase(true, APP_EXIT_OR_15_MINS, TimeUnit.MINUTES.toMillis(15), timeNow),
                TestCase(true, APP_EXIT_OR_15_MINS, TimeUnit.MINUTES.toMillis(16), timeNow),

                // background time recorded - will return true when duration is >= 30 mins
                TestCase(false, APP_EXIT_OR_30_MINS, TimeUnit.MINUTES.toMillis(29), timeNow),
                TestCase(true, APP_EXIT_OR_30_MINS, TimeUnit.MINUTES.toMillis(30), timeNow),
                TestCase(true, APP_EXIT_OR_30_MINS, TimeUnit.MINUTES.toMillis(31), timeNow),

                // background time recorded - will return true when duration is >= 60 mins
                TestCase(false, APP_EXIT_OR_60_MINS, TimeUnit.MINUTES.toMillis(59), timeNow),
                TestCase(true, APP_EXIT_OR_60_MINS, TimeUnit.MINUTES.toMillis(60), timeNow),
                TestCase(true, APP_EXIT_OR_60_MINS, TimeUnit.MINUTES.toMillis(61), timeNow)
            )
        }
    }

    @Before
    fun setup() {

        whenever(mockSettingsDataStore.hasBackgroundTimestampRecorded()).thenReturn(testCase.hasBackgroundTimeRecorded)

        // configure amount of time backgrounded
        val timestamp = getPastTimestamp(testCase.durationBackgrounded, testCase.timeNow)
        configureBackgroundedTime(timestamp)

        // configure which clear-when setting is configured
        whenever(mockSettingsDataStore.automaticallyClearWhenOption).thenReturn(testCase.clearWhenOption)
    }

    @Test
    fun enoughTimePassed() {
        assertEquals(testCase.expected, testee.hasEnoughTimeElapsed())
    }

    private fun configureBackgroundedTime(timestamp: Long) {
        whenever(mockSettingsDataStore.appBackgroundedTimestamp).thenReturn(timestamp)
    }

    private fun getPastTimestamp(millisPreviously: Long, timeNow: Long = System.currentTimeMillis()): Long {
        return Calendar.getInstance().also {
            it.timeInMillis = timeNow
            it.add(Calendar.MILLISECOND, (-millisPreviously).toInt())
        }.timeInMillis
    }

    data class TestCase(
        val expected: Boolean,
        val clearWhenOption: ClearWhenOption,
        val durationBackgrounded: Long,
        val timeNow: Long,
        val hasBackgroundTimeRecorded: Boolean = true
    )
}