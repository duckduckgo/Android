/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.duckchat.impl.pixel

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.duckchat.impl.nativeinput.footer.FailingPreferencesDataStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SwipeUpNewChatUsageTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val pixel: Pixel = mock()
    private val currentTimeProvider: CurrentTimeProvider = mock()
    private var now = LocalDateTime.of(2026, 11, 10, 9, 0)
    private lateinit var testee: SwipeUpNewChatUsage

    @Before
    fun setUp() {
        whenever(currentTimeProvider.localDateTimeNow()).thenAnswer { now }
        val dataStore = PreferenceDataStoreFactory.create(
            scope = coroutineRule.testScope,
            produceFile = { context.preferencesDataStoreFile("swipe_up_new_chat_${UUID.randomUUID()}") },
        )
        testee = usage(dataStore)
    }

    @Test
    fun whenFirstSwipeThenNotUsedBefore() = runTest {
        assertEquals(false, testee.recordSwipe())
    }

    @Test
    fun whenSwipedBeforeThenUsedBefore() = runTest {
        testee.recordSwipe()

        assertEquals(true, testee.recordSwipe())
    }

    @Test
    fun whenSwipedOnThreeDifferentDaysThenMilestoneFires() = runTest {
        swipeOnDays(3)

        verifyMilestoneFired()
    }

    @Test
    fun whenSwipedOnTwoDaysThenMilestoneDoesNotFire() = runTest {
        swipeOnDays(2)

        verifyMilestoneNotFired()
    }

    @Test
    fun whenSwipedManyTimesOnOneDayThenItCountsOnce() = runTest {
        repeat(5) { testee.recordSwipe() }
        now = now.plusDays(1)
        repeat(5) { testee.recordSwipe() }

        verifyMilestoneNotFired()
    }

    @Test
    fun whenSwipedLaterTheSameDayThenItCountsOnce() = runTest {
        testee.recordSwipe()
        now = now.withHour(23)
        testee.recordSwipe()
        now = now.plusDays(1)
        testee.recordSwipe()

        verifyMilestoneNotFired()
    }

    @Test
    fun whenSwipedAfterTheThirdDayThenMilestoneFiresOnlyOnce() = runTest {
        swipeOnDays(5)

        verifyMilestoneFired()
    }

    @Test
    fun whenStoreFailsThenUsageIsUnknownAndNothingFires() = runTest {
        val failing = usage(FailingPreferencesDataStore())

        assertNull(failing.recordSwipe())
        verify(pixel, never()).enqueueFire(any<Pixel.PixelName>(), any(), any(), any())
    }

    @Test
    fun whenSwipedAfterTheMilestoneThenStillUsedBefore() = runTest {
        swipeOnDays(4)

        assertEquals(true, testee.recordSwipe())
    }

    private suspend fun swipeOnDays(days: Int) {
        repeat(days) {
            testee.recordSwipe()
            now = now.plusDays(1)
        }
    }

    private fun verifyMilestoneFired() {
        verify(pixel).enqueueFire(DuckChatPixelName.DUCK_CHAT_SWIPE_NEW_CHAT_USED_3_DAYS, type = Pixel.PixelType.Unique())
    }

    private fun verifyMilestoneNotFired() {
        verify(pixel, never()).enqueueFire(DuckChatPixelName.DUCK_CHAT_SWIPE_NEW_CHAT_USED_3_DAYS, type = Pixel.PixelType.Unique())
    }

    private fun usage(store: DataStore<Preferences>) = SwipeUpNewChatUsage(store, currentTimeProvider, pixel)
}
