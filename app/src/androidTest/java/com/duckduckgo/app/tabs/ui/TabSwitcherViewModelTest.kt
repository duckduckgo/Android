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

package com.duckduckgo.app.tabs.ui

import android.arch.core.executor.testing.InstantTaskExecutorRule
import android.arch.lifecycle.Observer
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.Command
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.lastValue
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import org.junit.Assert.*
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

    @Mock
    private lateinit var mockCommandObserver: Observer<Command>

    @Captor
    private lateinit var commandCaptor: ArgumentCaptor<Command>

    @Mock
    private lateinit var mockTabRepository: TabRepository

    private lateinit var testee: TabSwitcherViewModel

    @Before
    fun before() {
        MockitoAnnotations.initMocks(this)
        whenever(mockTabRepository.add()).thenReturn("TAB_ID")
        testee = TabSwitcherViewModel(mockTabRepository)
        testee.command.observeForever(mockCommandObserver)
    }

    @Test
    fun whenNewTabRequestedThenRepositoryNotifiedAndSwitcherClosed() {
        testee.onNewTabRequested()
        verify(mockTabRepository).add()
        verify(mockCommandObserver).onChanged(commandCaptor.capture())
        assertEquals(commandCaptor.lastValue, Command.Close)
    }

    @Test
    fun whenTabSelectedThenRepositoryNotifiedAndSwitcherClosed() {
        testee.onTabSelected(TabEntity("abc", "", ""))
        verify(mockTabRepository).select(eq("abc"))
        verify(mockCommandObserver).onChanged(commandCaptor.capture())
        assertEquals(commandCaptor.lastValue, Command.Close)
    }

    @Test
    fun whenTabDeletedThenRepositoryNotified() {
        val entity = TabEntity("abc", "", "")
        testee.onTabDeleted(entity)
        verify(mockTabRepository).delete(entity)
    }

    @Test
    fun whenClearRequestedThenDeleteAllCalledOnRepositoryAndSwitcherClosed() {
        testee.onClearRequested()
        verify(mockTabRepository).deleteAll()
        verify(mockCommandObserver).onChanged(commandCaptor.capture())
        assertEquals(commandCaptor.lastValue, Command.Close)
    }

    @Test
    fun whenClearCompleteThenMessageDisplayed() {
        testee.onClearComplete()
        verify(mockCommandObserver).onChanged(commandCaptor.capture())
        assertEquals(commandCaptor.lastValue, Command.DisplayMessage(R.string.fireDataCleared))
    }

}