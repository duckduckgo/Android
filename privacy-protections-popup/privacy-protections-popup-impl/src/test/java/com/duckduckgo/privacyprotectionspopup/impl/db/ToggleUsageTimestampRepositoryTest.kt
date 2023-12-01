/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.privacyprotectionspopup.impl.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.threeten.bp.Instant

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class ToggleUsageTimestampRepositoryTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private lateinit var database: PrivacyProtectionsPopupDatabase
    private lateinit var subject: ToggleUsageTimestampRepository

    @Before
    fun setUp() {
        database = Room
            .inMemoryDatabaseBuilder(
                context = ApplicationProvider.getApplicationContext(),
                PrivacyProtectionsPopupDatabase::class.java,
            )
            .build()

        subject = ToggleUsageTimestampRepositoryImpl(database.toggleUsageTimestampDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun whenDatabaseIsEmptyThenReturnsNullDismissTimestamp() = runTest {
        assertNull(subject.getToggleUsageTimestamp().first())
    }

    @Test
    fun whenDismissTimeIsStoredThenQueryReturnsCorrectValue() = runTest {
        val timestamp = Instant.parse("2023-11-29T10:15:30.000Z")

        subject.setToggleUsageTimestamp(timestamp)
        val storedTimestamp = subject.getToggleUsageTimestamp().first()
        assertEquals(timestamp, storedTimestamp)
    }

    @Test
    fun whenDismissTimeIsSetMultipleTimesThenReturnsMostRecentlyStoredValue() = runTest {
        subject.setToggleUsageTimestamp(Instant.parse("2023-11-28T10:15:30.000Z"))
        subject.setToggleUsageTimestamp(Instant.parse("2023-11-29T10:15:30.000Z"))
        subject.setToggleUsageTimestamp(Instant.parse("2023-11-10T10:15:30.000Z"))

        val storedTimestamp = subject.getToggleUsageTimestamp().first()
        assertEquals(Instant.parse("2023-11-10T10:15:30.000Z"), storedTimestamp)
    }
}
