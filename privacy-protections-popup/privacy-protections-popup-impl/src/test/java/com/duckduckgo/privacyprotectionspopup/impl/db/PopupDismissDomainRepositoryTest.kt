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
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class PopupDismissDomainRepositoryTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private lateinit var database: PrivacyProtectionsPopupDatabase
    private lateinit var subject: PopupDismissDomainRepository

    @Before
    fun setUp() {
        database = Room
            .inMemoryDatabaseBuilder(
                context = ApplicationProvider.getApplicationContext(),
                PrivacyProtectionsPopupDatabase::class.java,
            )
            .build()

        subject = PopupDismissDomainRepositoryImpl(database.popupDismissDomainDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun whenDatabaseIsEmptyThenReturnsNullDismissTimestamp() = runTest {
        val dismissedAt = subject.getPopupDismissTime("www.example.com").first()
        assertNull(dismissedAt)
    }

    @Test
    fun whenDismissTimeIsStoredThenQueryReturnsCorrectValue() = runTest {
        val domain = "www.example.com"
        val dismissAt = Instant.parse("2023-11-29T10:15:30.000Z")

        subject.setPopupDismissTime(domain, dismissAt)
        val storedDismissAt = subject.getPopupDismissTime(domain).first()
        assertEquals(dismissAt, storedDismissAt)
    }

    @Test
    fun whenDismissTimeIsSetMultipleTimesThenReturnsMostRecentlyStoredValue() = runTest {
        val domain = "www.example.com"
        subject.setPopupDismissTime(domain, Instant.parse("2023-11-28T10:15:30.000Z"))
        subject.setPopupDismissTime(domain, Instant.parse("2023-11-29T10:15:30.000Z"))
        subject.setPopupDismissTime(domain, Instant.parse("2023-11-10T10:15:30.000Z"))

        val storedDismissAt = subject.getPopupDismissTime(domain).first()
        assertEquals(Instant.parse("2023-11-10T10:15:30.000Z"), storedDismissAt)
    }

    @Test
    fun whenDismissTimeIsSetForDifferentDomainsThenCorrectValueIsReturned() = runTest {
        val domain = "www.example.com"
        subject.setPopupDismissTime(domain, Instant.parse("2023-11-28T10:15:30.000Z"))
        subject.setPopupDismissTime("www.example2.com", Instant.parse("2023-11-29T10:15:30.000Z"))
        subject.setPopupDismissTime("www.example1.com", Instant.parse("2023-11-30T10:15:30.000Z"))

        val storedDismissAt = subject.getPopupDismissTime(domain).first()
        assertEquals(Instant.parse("2023-11-28T10:15:30.000Z"), storedDismissAt)
    }

    @Test
    fun whenRemoveEntriesInvokedThenCorrectDataIsDeleted() = runTest {
        subject.setPopupDismissTime("www.example1.com", Instant.parse("2023-11-28T10:15:30.000Z"))
        subject.setPopupDismissTime("www.example2.com", Instant.parse("2023-11-29T10:15:30.000Z"))
        subject.setPopupDismissTime("www.example3.com", Instant.parse("2023-12-01T10:00:00.000Z"))
        subject.setPopupDismissTime("www.example4.com", Instant.parse("2023-12-01T10:30:00.000Z"))

        subject.removeEntriesOlderThan(Instant.parse("2023-12-01T10:15:00.000Z"))

        assertNull(subject.getPopupDismissTime("www.example1.com").first())
        assertNull(subject.getPopupDismissTime("www.example2.com").first())
        assertNull(subject.getPopupDismissTime("www.example3.com").first())
        assertEquals(Instant.parse("2023-12-01T10:30:00.000Z"), subject.getPopupDismissTime("www.example4.com").first())
    }

    @Test
    fun whenRemoreAllEntriesIsInvokedThenAllDataIsDeleted() = runTest {
        subject.setPopupDismissTime("www.example1.com", Instant.parse("2023-11-28T10:15:30.000Z"))
        subject.setPopupDismissTime("www.example2.com", Instant.parse("2023-11-29T10:15:30.000Z"))
        subject.setPopupDismissTime("www.example3.com", Instant.parse("2023-12-01T10:00:00.000Z"))
        subject.setPopupDismissTime("www.example4.com", Instant.parse("2023-12-01T10:30:00.000Z"))

        subject.removeAllEntries()

        assertNull(subject.getPopupDismissTime("www.example1.com").first())
        assertNull(subject.getPopupDismissTime("www.example2.com").first())
        assertNull(subject.getPopupDismissTime("www.example3.com").first())
        assertNull(subject.getPopupDismissTime("www.example4.com").first())
    }
}
