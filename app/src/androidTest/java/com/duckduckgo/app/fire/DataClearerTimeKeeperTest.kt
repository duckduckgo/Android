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

import android.os.SystemClock
import androidx.test.filters.FlakyTest
import com.duckduckgo.app.settings.clear.ClearWhenOption
import com.duckduckgo.app.settings.clear.ClearWhenOption.*
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import java.util.*
import java.util.concurrent.TimeUnit.MINUTES
import java.util.concurrent.TimeUnit.SECONDS

@RunWith(Parameterized::class)
class DataClearerTimeKeeperTest(private val testCase: TestCase) {

    private val testee: DataClearerTimeKeeper = DataClearerTimeKeeper()

    companion object {

        @JvmStatic
        @Parameters(name = "Test case: {index} - {0}")
        fun testData(): Array<TestCase> {

            fun timeNow(): () -> Long = { SystemClock.elapsedRealtime() }

            return arrayOf(
                // APP_EXIT_ONLY shouldn't be passed to this method - always expected to return false regardless of other configuration/inputs
                TestCase(false, APP_EXIT_ONLY, MINUTES.toMillis(5), timeNow()),
                TestCase(false, APP_EXIT_ONLY, MINUTES.toMillis(0), timeNow()),
                TestCase(false, APP_EXIT_ONLY, MINUTES.toMillis(-5), timeNow()),

                // will return true when duration is >= 5 secs
                TestCase(false, APP_EXIT_OR_5_SECONDS, SECONDS.toMillis(4), timeNow()),
                TestCase(true, APP_EXIT_OR_5_SECONDS, SECONDS.toMillis(5), timeNow()),
                TestCase(true, APP_EXIT_OR_5_SECONDS, SECONDS.toMillis(6), timeNow()),

                // will return true when duration is >= 5 mins
                TestCase(false, APP_EXIT_OR_5_MINS, MINUTES.toMillis(4), timeNow()),
                TestCase(true, APP_EXIT_OR_5_MINS, MINUTES.toMillis(5), timeNow()),
                TestCase(true, APP_EXIT_OR_5_MINS, MINUTES.toMillis(6), timeNow()),

                // will return true when duration is >= 15 mins
                TestCase(false, APP_EXIT_OR_15_MINS, MINUTES.toMillis(14), timeNow()),
                TestCase(true, APP_EXIT_OR_15_MINS, MINUTES.toMillis(15), timeNow()),
                TestCase(true, APP_EXIT_OR_15_MINS, MINUTES.toMillis(16), timeNow()),

                // will return true when duration is >= 30 mins
                TestCase(false, APP_EXIT_OR_30_MINS, MINUTES.toMillis(29), timeNow()),
                TestCase(true, APP_EXIT_OR_30_MINS, MINUTES.toMillis(30), timeNow()),
                TestCase(true, APP_EXIT_OR_30_MINS, MINUTES.toMillis(31), timeNow()),

                // will return true when duration is >= 60 mins
                TestCase(false, APP_EXIT_OR_60_MINS, MINUTES.toMillis(59), timeNow()),
                TestCase(true, APP_EXIT_OR_60_MINS, MINUTES.toMillis(60), timeNow()),
                TestCase(true, APP_EXIT_OR_60_MINS, MINUTES.toMillis(61), timeNow())
            )
        }
    }

    @Test
    @FlakyTest
    fun enoughTimePassed() {
        val timestamp = getPastTimestamp(testCase.durationBackgrounded, testCase.timeNow.invoke())
        assertEquals(testCase.expected, testee.hasEnoughTimeElapsed(backgroundedTimestamp = timestamp, clearWhenOption = testCase.clearWhenOption))
    }

    private fun getPastTimestamp(millisPreviously: Long, timeNow: Long): Long {
        return Calendar.getInstance().also {
            it.timeInMillis = timeNow
            it.add(Calendar.MILLISECOND, (-millisPreviously).toInt())
        }.timeInMillis
    }

    data class TestCase(
        val expected: Boolean,
        val clearWhenOption: ClearWhenOption,
        val durationBackgrounded: Long,
        val timeNow: () -> Long
    )
}
