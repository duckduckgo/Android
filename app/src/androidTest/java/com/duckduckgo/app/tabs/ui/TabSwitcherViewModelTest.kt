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

@file:Suppress("RemoveExplicitTypeArguments")

package com.duckduckgo.app.tabs.ui

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.browser.session.WebViewSessionInMemoryStorage
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.Command
import com.nhaarman.mockitokotlin2.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.MockitoAnnotations

class TabSwitcherViewModelTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @ExperimentalCoroutinesApi
    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    @Mock
    private lateinit var mockCommandObserver: Observer<Command>

    @Captor
    private lateinit var commandCaptor: ArgumentCaptor<Command>

    @Mock
    private lateinit var mockTabRepository: TabRepository

    private lateinit var testee: TabSwitcherViewModel

    private val repoDeletableTabs = Channel<List<TabEntity>>()

    @ExperimentalCoroutinesApi
    @Before
    fun before() {
        MockitoAnnotations.initMocks(this)
        runBlocking {
            whenever(mockTabRepository.flowDeletableTabs)
                .thenReturn(repoDeletableTabs.consumeAsFlow())
            whenever(mockTabRepository.add()).thenReturn("TAB_ID")
            testee = TabSwitcherViewModel(mockTabRepository, WebViewSessionInMemoryStorage())
            testee.command.observeForever(mockCommandObserver)
        }
    }

    @After
    fun after() {
        repoDeletableTabs.close()
    }

    @Test
    fun whenNewTabRequestedThenRepositoryNotifiedAndSwitcherClosed() = runBlocking<Unit> {
        testee.onNewTabRequested()
        verify(mockTabRepository).add()
        verify(mockCommandObserver).onChanged(commandCaptor.capture())
        assertEquals(Command.Close, commandCaptor.lastValue)
    }

    @Test
    fun whenTabSelectedThenRepositoryNotifiedAndSwitcherClosed() = runBlocking<Unit> {
        testee.onTabSelected(TabEntity("abc", "", "", position = 0))
        verify(mockTabRepository).select(eq("abc"))
        verify(mockCommandObserver).onChanged(commandCaptor.capture())
        assertEquals(Command.Close, commandCaptor.lastValue)
    }

    @Test
    fun whenTabDeletedThenRepositoryNotified() = runBlocking<Unit> {
        val entity = TabEntity("abc", "", "", position = 0)
        testee.onTabDeleted(entity)
        verify(mockTabRepository).delete(entity)
    }

    @Test
    fun whenOnMarkTabAsDeletableThenCallMarkDeletable() = runBlocking {
        val entity = TabEntity("abc", "", "", position = 0)
        testee.onMarkTabAsDeletable(entity)

        verify(mockTabRepository).markDeletable(entity)
    }

    @Test
    fun whenUndoDeletableTabThenUndoDeletable() = runBlocking {
        val entity = TabEntity("abc", "", "", position = 0)
        testee.undoDeletableTab(entity)

        verify(mockTabRepository).undoDeletable(entity)
    }

    @Test
    fun whenPurgeDeletableTabsThenCallRepositoryPurgeDeletableTabs() = runBlocking {
        testee.purgeDeletableTabs()

        verify(mockTabRepository).purgeDeletableTabs()
    }

    @Test
    fun whenRepositoryDeletableTabsUpdatesThenDeletableTabsEmits() = runBlocking {
        val tab = TabEntity("ID", position = 0)

        val expectedTabs = listOf(listOf(), listOf(tab))
        var index = 0
        testee.deletableTabs.observeForever {
            assertEquals(expectedTabs[index++], it)
        }

        repoDeletableTabs.send(listOf())
        repoDeletableTabs.send(listOf(tab))
    }

    @Test
    fun whenRepositoryDeletableTabsEmitsSameValueThenDeletableTabsEmitsAll() = runBlocking {
        val tab = TabEntity("ID", position = 0)

        testee.deletableTabs.observeForever {
            assertEquals(listOf(tab), it)
        }

        repoDeletableTabs.send(listOf(tab))
        repoDeletableTabs.send(listOf(tab))
    }
}
