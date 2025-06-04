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

package com.duckduckgo.tabs.db

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.tabs.db.TabsDao
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabSelectionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
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
    fun whenOnlyDeletableTabsExistTabsReturnsEmpty() {
        val deletableTab = TabEntity(
            tabId = "ID",
            position = 0,
            deletable = true,
        )
        testee.insertTab(deletableTab)

        assertTrue(testee.tabs().isEmpty())
    }

    @Test
    fun whenOnlyDeletableTabsExistTabReturnsMatch() {
        val deletableTab = TabEntity(
            tabId = "ID",
            position = 0,
            deletable = true,
        )
        testee.insertTab(deletableTab)

        assertEquals(deletableTab, testee.tab("ID"))
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

    @Test
    fun whenGetDeletableTabIdsCalledThenReturnListWithDeletableTabIds() {
        val firstTabNonDeletable = TabEntity("TAB_ID_1", "http//www.example.com", position = 0, deletable = false)
        val secondTabDeletable = TabEntity("TAB_ID_2", "http//www.example.com", position = 1, deletable = true)
        testee.insertTab(firstTabNonDeletable)
        testee.insertTab(secondTabDeletable)

        val deletableTabIds = testee.getDeletableTabIds()

        assertEquals(listOf("TAB_ID_2"), deletableTabIds)
    }

    @Test
    fun deleteTabsMarkedAsDeletableDeletesOnlyDeletableTabs() {
        testee.insertTab(TabEntity("TAB_ID1", position = 0, deletable = true))
        val tab = TabEntity("TAB_ID2", position = 1)
        testee.insertTab(tab)

        testee.deleteTabsMarkedAsDeletable()

        assertNull(testee.tab("TAB_ID1"))
        assertEquals(tab, testee.tab("TAB_ID2"))
    }

    @Test
    fun whenSelectedTabMarkedAsDeletableAndPurgedThenUpdateSelection() {
        val tab = TabEntity(
            tabId = "TAB_ID",
            url = "www.duckduckgo.com",
            position = 0,
        )
        val deletableTab = TabEntity(
            tabId = "TAB_ID_1",
            url = "www.duckduckgo.com",
            position = 1,
            deletable = true,
        )

        testee.insertTab(tab)
        testee.addAndSelectTab(deletableTab)
        testee.purgeDeletableTabsAndUpdateSelection()

        assertEquals(tab, testee.selectedTab())
    }

    @Test
    fun whenMarkTabAsDeletableThenModifyOnlyDeletableColumn() {
        val tab = TabEntity(
            tabId = "TAB_ID",
            url = "www.duckduckgo.com",
            position = 0,
        )

        testee.insertTab(tab)
        testee.markTabAsDeletable(tab.copy(url = "www.other.com"))

        assertEquals(tab.copy(deletable = true), testee.tab(tab.tabId))
    }

    @Test
    fun whenUndoDeletableTabThenModifyOnlyDeletableColumn() {
        val tab = TabEntity(
            tabId = "TAB_ID",
            url = "www.duckduckgo.com",
            position = 0,
            deletable = true,
        )

        testee.insertTab(tab)
        testee.undoDeletableTab(tab.copy(url = "www.other.com"))

        assertEquals(tab.copy(deletable = false), testee.tab(tab.tabId))
    }

    @Test
    fun whenSelectTabByUrlAndTabExistsThenTabIdReturned() = runTest {
        val tab = TabEntity(
            tabId = "TAB_ID",
            url = "https://www.duckduckgo.com/",
            position = 0,
            deletable = true,
        )

        testee.insertTab(tab)
        val tabId = testee.selectTabByUrl("https://www.duckduckgo.com/")

        assertEquals(tabId, tab.tabId)
    }

    @Test
    fun whenSelectTabByUrlAndTabDoesNotExistThenNullReturned() = runTest {
        val tab = TabEntity(
            tabId = "TAB_ID",
            url = "https://www.duckduckgo.com/",
            position = 0,
        )

        testee.insertTab(tab)
        val tabId = testee.selectTabByUrl("https://www.quackquackno.com/")

        assertNull(tabId)
    }

    @Test
    fun whenNoTabsThenFlowSelectedTabIsNull() = runTest {
        val selectedTab = testee.flowSelectedTab().first()
        assertNull(selectedTab)
    }

    @Test
    fun whenTabSelectionInsertedWithForeignKeyThatExistsThenFlowSelectedTabIsUpdated() = runTest {
        val tab = TabEntity("TAB_ID", position = 0)
        val tabSelection = TabSelectionEntity(1, "TAB_ID")
        testee.insertTab(tab)
        testee.insertTabSelection(tabSelection)
        val selectedTab = testee.flowSelectedTab().first()
        assertEquals(tab, selectedTab)
    }

    @Test
    fun whenTabThatWasSelectedDeletedThenFlowSelectedTabIsNull() = runTest {
        val tab = TabEntity("TAB_ID", position = 0)
        val tabSelection = TabSelectionEntity(1, "TAB_ID")

        testee.insertTab(tab)
        testee.insertTabSelection(tabSelection)
        testee.deleteTab(tab)

        val selectedTab = testee.flowSelectedTab().first()
        assertNull(selectedTab)
    }

    @Test
    fun whenMarkTabsAsDeletableThenModifyOnlyDeletableColumn() {
        val tab1 = TabEntity(tabId = "TAB_ID1", url = "www.duckduckgo.com", position = 0)
        val tab2 = TabEntity(tabId = "TAB_ID2", url = "www.duckduckgo.com", position = 1)

        testee.insertTab(tab1)
        testee.insertTab(tab2)
        testee.markTabsAsDeletable(listOf(tab1.tabId, tab2.tabId))

        assertEquals(tab1.copy(deletable = true), testee.tab(tab1.tabId))
        assertEquals(tab2.copy(deletable = true), testee.tab(tab2.tabId))
    }

    @Test
    fun whenUndoDeletableTabsThenModifyOnlyDeletableColumn() {
        val tab1 = TabEntity(tabId = "TAB_ID1", url = "www.duckduckgo.com", position = 0, deletable = true)
        val tab2 = TabEntity(tabId = "TAB_ID2", url = "www.duckduckgo.com", position = 1, deletable = true)

        testee.insertTab(tab1)
        testee.insertTab(tab2)
        testee.undoDeletableTabs(listOf(tab1.tabId, tab2.tabId), false)

        assertEquals(tab1.copy(deletable = false), testee.tab(tab1.tabId))
        assertEquals(tab2.copy(deletable = false), testee.tab(tab2.tabId))
    }

    @Test
    fun whenUndoDeletableTabsAndMoveActiveToEndThenModifyDeletableColumnAndMoveActiveToEnd() {
        val tab1 = TabEntity(tabId = "TAB_ID1", url = "www.duckduckgo.com", position = 0, deletable = false)
        val tab2 = TabEntity(tabId = "TAB_ID2", url = "www.duckduckgo.com", position = 0, deletable = true)
        val tab3 = TabEntity(tabId = "TAB_ID3", url = "www.duckduckgo.com", position = 1, deletable = true)

        testee.insertTab(tab1)
        testee.insertTab(tab2)
        testee.insertTab(tab3)
        testee.insertTabSelection(TabSelectionEntity(tabId = tab1.tabId))
        testee.undoDeletableTabs(listOf(tab2.tabId, tab3.tabId), true)

        val tabs = testee.tabs()
        assertEquals(tab2.copy(deletable = false), testee.tab(tab2.tabId))
        assertEquals(tab3.copy(deletable = false), testee.tab(tab3.tabId))
        assertEquals(tab1.copy(position = 2), testee.tab(tab1.tabId))
        assertEquals(tab1.tabId, tabs.last().tabId)
    }

    @Test
    fun whenDeleteTabsAndUpdateSelectionThenTabsDeletedAndSelectionUpdated() {
        val tab1 = TabEntity(tabId = "TAB_ID1", url = "www.duckduckgo.com", position = 0)
        val tab2 = TabEntity(tabId = "TAB_ID2", url = "www.duckduckgo.com", position = 1)
        val tab3 = TabEntity(tabId = "TAB_ID3", url = "www.duckduckgo.com", position = 2)

        testee.insertTab(tab1)
        testee.insertTab(tab2)
        testee.insertTab(tab3)
        testee.insertTabSelection(TabSelectionEntity(tabId = tab1.tabId))

        testee.deleteTabsAndUpdateSelection(listOf(tab1.tabId, tab2.tabId))

        assertNull(testee.tab(tab1.tabId))
        assertNull(testee.tab(tab2.tabId))
        assertNotNull(testee.tab(tab3.tabId))
        assertEquals(tab3, testee.selectedTab())
    }

    @Test
    fun whenAddAndSelectWithBlankParentAndUpdateIfBlankParentTrueThenUpdateTabBeforeInserting() {
        val zero = TabEntity(
            "TAB_ID0",
            position = 0,
            sourceTabId = null,
            url = "http://example.com",
        )
        val first = TabEntity(
            "TAB_ID1",
            position = 1,
            sourceTabId = "TAB_ID0",
            url = null,
        )
        val second = TabEntity(
            "TAB_ID2",
            position = 2,
            sourceTabId = "TAB_ID1",
            url = "http://example.com",
        )

        testee.insertTab(zero)
        testee.insertTab(first)
        testee.addAndSelectTab(second, updateIfBlankParent = true)

        assertFalse(testee.tabs().contains(first))
        val storedTab = testee.tab("TAB_ID2")
        assertEquals("TAB_ID0", storedTab?.sourceTabId)
        assertEquals(1, storedTab?.position)
        assertEquals(storedTab, testee.selectedTab())
    }
}
