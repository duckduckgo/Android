/*
 * Copyright (c) 2024 DuckDuckGo
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

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.ZonedDateTime
import kotlin.time.Duration.Companion.days
import kotlin.time.toJavaDuration

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class PixelFiredRepositoryTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val timeProvider = FakeTimeProvider()
    private lateinit var database: StatisticsDatabase
    private lateinit var subject: PixelFiredRepository

    @Before
    fun setUp() {
        database = Room
            .inMemoryDatabaseBuilder(
                context = ApplicationProvider.getApplicationContext(),
                StatisticsDatabase::class.java,
            )
            .build()

        subject = PixelFiredRepositoryImpl(
            dailyPixelFiredDao = database.dailyPixelFiredDao(),
            uniquePixelFiredDao = database.uniquePixelFiredDao(),
            timeProvider = timeProvider,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun whenDatabaseIsEmptyThenPixelFiredIsFalse() = runTest {
        assertFalse(subject.hasDailyPixelFiredToday("pixel_d"))
        assertFalse(subject.hasUniquePixelFired("pixel_u"))
    }

    @Test
    fun whenPixelWasFiredThenPixelFiredIsTrue() = runTest {
        subject.storeDailyPixelFiredToday("pixel_d")
        subject.storeUniquePixelFired("pixel_u")

        assertTrue(subject.hasDailyPixelFiredToday("pixel_d"))
        assertTrue(subject.hasUniquePixelFired("pixel_u"))
    }

    @Test
    fun whenADayHasPassedThenDailyPixelFiredIsFalse() = runTest {
        subject.storeDailyPixelFiredToday("pixel_d")
        subject.storeUniquePixelFired("pixel_u")

        timeProvider.time += 1.days.toJavaDuration()

        assertFalse(subject.hasDailyPixelFiredToday("pixel_d"))
        assertTrue(subject.hasUniquePixelFired("pixel_u"))
    }

    @Test
    fun whenDailyPixelWasFiredAgainThenDateIsUpdated() = runTest {
        subject.storeDailyPixelFiredToday("pixel_d")
        assertTrue(subject.hasDailyPixelFiredToday("pixel_d"))
        timeProvider.time += 1.days.toJavaDuration()
        assertFalse(subject.hasDailyPixelFiredToday("pixel_d"))
        subject.storeDailyPixelFiredToday("pixel_d")
        assertTrue(subject.hasDailyPixelFiredToday("pixel_d"))
    }
}

private class FakeTimeProvider : TimeProvider {
    var time: ZonedDateTime = ZonedDateTime.parse("2024-01-16T10:15:30Z")

    override fun getCurrentTime(): Instant = time.toInstant()
}
