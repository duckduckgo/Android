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

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabSelectionEntity
import org.junit.After
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
        database = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        testee = database.tabsDao()
    }

    @After
    fun after() {
        database.close()
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
        val first = TabEntity("TAB_ID1", position = 0)
        val second = TabEntity("TAB_ID2", position = 1)
        testee.insertTab(first)
        testee.insertTab(second)
        assertEquals(first, testee.firstTab())
    }

    @Test
    fun whenTabThatExistsRetrievedThenTabReturned() {
        val tab = TabEntity("TAB_ID", position = 0)
        testee.insertTab(tab)
        assertEquals(tab, testee.tab("TAB_ID"))
    }

    @Test
    fun whenUnknownTabRetrievedThenNullReturned() {
        assertNull(testee.tab("UNKNOWN_ID"))
    }

    @Test
    fun whenTabInsertedThenItExistsInTabsList() {
        val entity = TabEntity("TAB_ID", position = 0)
        testee.insertTab(entity)
        assertTrue(testee.tabs().contains(entity))
    }

    @Test
    fun whenTabInsertedTwiceThenSecondRecordOverwritesFirst() {
        val initial = TabEntity("TAB_ID", "http//example.com", position = 0)
        val updated = TabEntity("TAB_ID", "http//updatedexample.com", position = 1)
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
        val tab = TabEntity("TAB_ID", position = 0)
        val tabSelection = TabSelectionEntity(1, "TAB_ID")
        testee.insertTab(tab)
        testee.insertTabSelection(tabSelection)
        assertEquals(tab, testee.selectedTab())
    }

    @Test
    fun whenTabIsUpdatedThenExistingRecordIsUpdated() {
        val initial = TabEntity("TAB_ID", "http//example.com", position = 0)
        val updated = TabEntity("TAB_ID", "http//updatedexample.com", position = 1)
        testee.insertTab(initial)
        testee.updateTab(updated)
        assertEquals("http//updatedexample.com", testee.tab("TAB_ID")?.url)
    }

    @Test
    fun whenUnknownTabIsUpdatedThenNothingHappens() {
        val tab = TabEntity("TAB_ID", "http//updatedexample.com", position = 0)
        testee.updateTab(tab)
        assertNull(testee.tab("TAB_ID"))
    }

    @Test
    fun whenTabThatWasSelectedDeletedThenSelectedTabIsNull() {
        val tab = TabEntity("TAB_ID", position = 0)
        val tabSelection = TabSelectionEntity(1, "TAB_ID")

        testee.insertTab(tab)
        testee.insertTabSelection(tabSelection)
        testee.deleteTab(tab)

        assertNull(testee.selectedTab())
    }

    @Test
    fun whenTabThatExistsIsDeletedThenItIsRemovedFromListAndOtherElementsRemain() {
        val first = TabEntity("TAB_ID1", position = 0)
        val second = TabEntity("TAB_ID2", position = 1)

        testee.insertTab(first)
        testee.insertTab(second)
        testee.deleteTab(first)

        assertFalse(testee.tabs().contains(first))
        assertTrue(testee.tabs().contains(second))
    }

    @Test
    fun whenTabThatDoesNotExistIsDeletedThenListIsUnchanged() {
        val first = TabEntity("TAB_ID1", position = 0)
        val second = TabEntity("TAB_ID2", position = 1)
        testee.insertTab(first)
        testee.deleteTab(second)
        assertTrue(testee.tabs().contains(first))
    }

    @Test
    fun whenDeleteAllCalledThenAllElementsRemoves() {
        val first = TabEntity("TAB_ID1", position = 0)
        val second = TabEntity("TAB_ID2", position = 1)

        testee.insertTab(first)
        testee.insertTab(second)
        testee.deleteAllTabs()

        assertFalse(testee.tabs().contains(first))
        assertFalse(testee.tabs().contains(second))
    }

    @Test
    fun whenDeleteBlankCalledThenBlankElementsRemoved() {
        val first = TabEntity("TAB_ID1", position = 0)
        val second = TabEntity("TAB_ID2", "http://example.com", position = 1)
        val third = TabEntity("TAB_ID3", position = 2)

        testee.insertTab(first)
        testee.insertTab(second)
        testee.insertTab(third)

        testee.deleteBlankTabs()

        assertFalse(testee.tabs().contains(first))
        assertTrue(testee.tabs().contains(second))
        assertFalse(testee.tabs().contains(third))
    }

    @Test
    fun whenTabAddedAndSelectedThenRecordUpdatedAndAnyOldBlankTabsRemoved() {
        val first = TabEntity("TAB_ID1", position = 1)
        val second = TabEntity("TAB_ID2", position = 2)

        testee.addAndSelectTab(first)
        testee.addAndSelectTab(second)

        assertFalse(testee.tabs().contains(first))
        assertTrue(testee.tabs().contains(second))
        assertEquals(second, testee.selectedTab())
    }

    @Test
    fun whenTabInsertedAtPositionThenOtherTabsReordered() {
        testee.insertTab(TabEntity("TAB_ID1", position = 0))
        testee.insertTab(TabEntity("TAB_ID2", position = 1))
        testee.insertTab(TabEntity("TAB_ID3", position = 2))

        testee.insertTabAtPosition(TabEntity("TAB_ID4", position = 0))

        val tabs = testee.tabs()

        assertEquals(0, tabs[0].position)
        assertEquals("TAB_ID4", tabs[0].tabId)

        assertEquals(1, tabs[1].position)
        assertEquals("TAB_ID1", tabs[1].tabId)

        assertEquals(2, tabs[2].position)
        assertEquals("TAB_ID2", tabs[2].tabId)

        assertEquals(3, tabs[3].position)
        assertEquals("TAB_ID3", tabs[3].tabId)
    }

    @Test
    fun whenSourceTabDeletedThenRelatedTabsUpdated() {
        val firstTab = TabEntity("TAB_ID", "http//updatedexample.com", position = 0)
        val secondTab = TabEntity("TAB_ID_1", "http//updatedexample.com", position = 1, sourceTabId = "TAB_ID")
        testee.insertTab(firstTab)
        testee.insertTab(secondTab)

        testee.deleteTab(firstTab)

        assertNotNull(testee.tab("TAB_ID_1"))
        assertNull(testee.tab("TAB_ID_1")?.sourceTabId)
    }
}
