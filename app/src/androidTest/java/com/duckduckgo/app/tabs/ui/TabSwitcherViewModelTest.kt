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
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.session.WebViewSessionInMemoryStorage
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.Command
import com.jakewharton.rxrelay2.PublishRelay
import com.nhaarman.mockitokotlin2.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
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

    private val repoDeletableTabs = PublishRelay.create<List<TabEntity>>()

    @Before
    fun before() {
        MockitoAnnotations.initMocks(this)
        runBlocking {
            whenever(mockTabRepository.deletableLiveTabs).thenReturn(repoDeletableTabs)
            whenever(mockTabRepository.add()).thenReturn("TAB_ID")
            testee = TabSwitcherViewModel(mockTabRepository, WebViewSessionInMemoryStorage())
            testee.command.observeForever(mockCommandObserver)
        }
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
    fun whenClearCompleteThenMessageDisplayedAndSwitcherClosed() {
        testee.onClearComplete()
        verify(mockCommandObserver, times(2)).onChanged(commandCaptor.capture())
        assertEquals(Command.DisplayMessage(R.string.fireDataCleared), commandCaptor.allValues[0])
        assertEquals(Command.Close, commandCaptor.allValues[1])
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
    fun whenRepositoryDeletableTabsUpdatesThenDeletableTabsEmits() {
        val observer = testee.deletableTabs.test()

        val tab = TabEntity("ID", position = 0)
        repoDeletableTabs.accept(listOf())
        repoDeletableTabs.accept(listOf(tab))
        observer.assertValues(listOf(), listOf(tab))
    }

    @Test
    fun whenRepositoryDeletableTabsEmitsSameValueThenDeletableTabsEmitsAll() {
        val observer = testee.deletableTabs.test()

        val tab = TabEntity("ID", position = 0)
        repoDeletableTabs.accept(listOf(tab))
        repoDeletableTabs.accept(listOf(tab))
        observer.assertValues(listOf(tab), listOf(tab))
    }

    @Test
    fun whenObserverSubscribesToDeletableTabsThenLastValueIsNotEmitted() {
        repoDeletableTabs.accept(listOf(TabEntity("ID", position = 0)))

        val observer = testee.deletableTabs.test()

        observer.assertNoValues()
    }
}
