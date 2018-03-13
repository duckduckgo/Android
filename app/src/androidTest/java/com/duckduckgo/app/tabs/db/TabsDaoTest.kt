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

package com.duckduckgo.app.tabs.db

import android.arch.core.executor.testing.InstantTaskExecutorRule
import android.arch.persistence.room.Room
import android.database.sqlite.SQLiteConstraintException
import android.support.test.InstrumentationRegistry
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabSelectionEntity
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class TabsDaoTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var database: AppDatabase
    private lateinit var testee: TabsDao

    @Before
    fun before() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getContext(),
            AppDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()
        testee = database.tabsDao()
    }

    @Test
    fun whenNoTabsThenTabsIsEmpty() {
        assertTrue(testee.tabs().isEmpty())
    }

    @Test
    fun whenNoTabsThenFirstReturnsNull() {
        assertNull(testee.firstTab())
    }

    @Test
    fun whenMultipleTabsThenFirstReturnsFirst() {
        val first = TabEntity("TAB_ID1")
        val second = TabEntity("TAB_ID2")
        testee.insertTab(first)
        testee.insertTab(second)
        assertEquals(first, testee.firstTab())
    }

    @Test
    fun whenTabThatExistsRetrievedThenTabReturned() {
        val tab = TabEntity("TAB_ID")
        testee.insertTab(tab)
        assertEquals(tab, testee.tab("TAB_ID"))
    }

    @Test
    fun whenUnknownTabRetrievedThenNullReturned() {
        assertNull(testee.tab("UNKNOWN_ID"))
    }

    @Test
    fun whenTabInsertedThenItExistsInTabsList() {
        val entity = TabEntity("TAB_ID")
        testee.insertTab(entity)
        assertTrue(testee.tabs().contains(entity))
    }

    @Test
    fun whenTabInsertedTwiceThenSecondRecordOverwritesFirst() {
        val initial = TabEntity("TAB_ID", "http//example.com")
        val updated = TabEntity("TAB_ID", "http//updatedexample.com")
        testee.insertTab(initial)
        testee.insertTab(updated)
        assertEquals("http//updatedexample.com", testee.tab("TAB_ID")?.url)
    }

    @Test
    fun whenNoTabsThenSelectionIsNull() {
        assertNull(testee.selectedTab())
    }

    @Test
    fun whenTabSelectionInsertedWithForeignKeyThatExistsThenRecordIsUpdated() {
        val tab = TabEntity("TAB_ID")
        val tabSelection = TabSelectionEntity(1, "TAB_ID")
        testee.insertTab(tab)
        testee.insertTabSelection(tabSelection)
        assertEquals(tab, testee.selectedTab())
    }

    @Test(expected = SQLiteConstraintException::class)
    fun whenTabSelectionInsertedWithUnknownForeignKeyThenExceptionThrown() {
        val tabSelection = TabSelectionEntity(1, "TAB_ID")
        testee.insertTabSelection(tabSelection)
    }

    @Test
    fun whenTabIsUpdatedThenExistingRecordIsUpdated() {
        val initial = TabEntity("TAB_ID", "http//example.com")
        val updated = TabEntity("TAB_ID", "http//updatedexample.com")
        testee.insertTab(initial)
        testee.updateTab(updated)
        assertEquals("http//updatedexample.com", testee.tab("TAB_ID")?.url)
    }

    @Test
    fun whenUnknownTabIsUpdatedThenNothingHappens() {
        val tab = TabEntity("TAB_ID", "http//updatedexample.com")
        testee.updateTab(tab)
        assertNull(testee.tab("TAB_ID"))
    }

    @Test
    fun whenTabThatWasSelectedDeletedThenSelectedTabIsNull() {
        val tab = TabEntity("TAB_ID")
        val tabSelection = TabSelectionEntity(1, "TAB_ID")

        testee.insertTab(tab)
        testee.insertTabSelection(tabSelection)
        testee.deleteTab(tab)

        assertNull(testee.selectedTab())
    }

    @Test
    fun whenTabThatExistsIsDeletedThenItIsRemovedFromListAndOtherElementsRemain() {
        val first = TabEntity("TAB_ID1")
        val second = TabEntity("TAB_ID2")

        testee.insertTab(first)
        testee.insertTab(second)
        testee.deleteTab(first)

        assertFalse(testee.tabs().contains(first))
        assertTrue(testee.tabs().contains(second))
    }

    @Test
    fun whenTabThatDoesNotExistIsDeletedThenListIsUnchanged() {
        val first = TabEntity("TAB_ID1")
        val second = TabEntity("TAB_ID2")
        testee.insertTab(first)
        testee.deleteTab(second)
        assertTrue(testee.tabs().contains(first))
    }

    @Test
    fun whenDeleteAllCalledThenAllElementsRemoves() {
        val first = TabEntity("TAB_ID1")
        val second = TabEntity("TAB_ID2")

        testee.insertTab(first)
        testee.insertTab(second)
        testee.deleteAllTabs()

        assertFalse(testee.tabs().contains(first))
        assertFalse(testee.tabs().contains(second))
    }

    @Test
    fun whenTabAddedAndSelectedThenRecordUpdated() {
        val tab = TabEntity("TAB_ID")
        testee.addAndSelectTab(tab)
        assertEquals(tab, testee.selectedTab())
    }
}