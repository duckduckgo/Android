/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.app.privacy.ui

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.privacy.db.UserWhitelistDao
import com.duckduckgo.app.privacy.model.UserWhitelistedDomain
import com.duckduckgo.app.privacy.ui.WhitelistViewModel.Command
import com.duckduckgo.app.privacy.ui.WhitelistViewModel.Command.ShowAdd
import com.duckduckgo.app.privacy.ui.WhitelistViewModel.Command.ShowWhitelistFormatError
import com.duckduckgo.app.runBlocking
import com.nhaarman.mockitokotlin2.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScope
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class WhitelistViewModelTest {

    @ExperimentalCoroutinesApi @get:Rule var coroutineRule = CoroutineTestRule()

    @get:Rule @Suppress("unused") var instantTaskExecutorRule = InstantTaskExecutorRule()

    private val mockDao: UserWhitelistDao = mock()
    private val liveData = MutableLiveData<List<UserWhitelistedDomain>>()

    private val mockCommandObserver: Observer<Command> = mock()
    private var commandCaptor: KArgumentCaptor<Command> = argumentCaptor()

    private val testee by lazy {
        WhitelistViewModel(mockDao, TestCoroutineScope(), coroutineRule.testDispatcherProvider)
    }

    @Before
    fun before() =
        coroutineRule.runBlocking {
            liveData.value = emptyList()
            whenever(mockDao.all()).thenReturn(liveData)
            testee.command.observeForever(mockCommandObserver)
        }

    @After
    fun after() {
        testee.command.removeObserver(mockCommandObserver)
    }

    @Test
    fun whenWhitelistUpdatedWithDataThenViewStateIsUpdatedAndWhitelistDisplayed() {
        val list = listOf(UserWhitelistedDomain(DOMAIN), UserWhitelistedDomain(NEW_DOMAIN))
        liveData.postValue(list)
        val viewState = testee.viewState.value!!
        assertEquals(list, viewState.whitelist)
        assertTrue(viewState.showWhitelist)
    }

    @Test
    fun whenWhitelistUpdatedWithEmptyListThenViewStateIsUpdatedAndWhitelistNotDisplayed() {
        liveData.postValue(emptyList())
        val viewState = testee.viewState.value!!
        assertTrue(viewState.whitelist.isEmpty())
        assertFalse(viewState.showWhitelist)
    }

    @Test
    fun whenAddRequestedThenAddShown() {
        testee.onAddRequested()
        verify(mockCommandObserver).onChanged(ShowAdd)
    }

    @Test
    fun whenValidEntryAddedThenDaoUpdated() {
        val entry = UserWhitelistedDomain(NEW_DOMAIN)
        testee.onEntryAdded(entry)
        verify(mockDao).insert(entry)
    }

    @Test
    fun whenInvalidEntryAddedThenErrorShownAndDaoNotUpdated() {
        val entry = UserWhitelistedDomain(INVALID_DOMAIN)
        testee.onEntryAdded(entry)
        verify(mockCommandObserver).onChanged(ShowWhitelistFormatError)
        verify(mockDao, never()).insert(entry)
    }

    @Test
    fun whenEditRequestedThenEditShown() {
        val entry = UserWhitelistedDomain(DOMAIN)
        testee.onEditRequested(entry)
        verify(mockCommandObserver).onChanged(commandCaptor.capture())
        val lastValue = commandCaptor.lastValue as Command.ShowEdit
        assertEquals(entry, lastValue.entry)
    }

    @Test
    fun whenValidEditSubmittedThenDaoUpdated() {
        val old = UserWhitelistedDomain(DOMAIN)
        val new = UserWhitelistedDomain(NEW_DOMAIN)
        testee.onEntryEdited(old, new)
        verify(mockDao).delete(old)
        verify(mockDao).insert(new)
    }

    @Test
    fun whenValidEditSubmittedThenErrorShownAndDaoNotUpdated() {
        val old = UserWhitelistedDomain(DOMAIN)
        val new = UserWhitelistedDomain(INVALID_DOMAIN)
        testee.onEntryEdited(old, new)
        verify(mockCommandObserver).onChanged(ShowWhitelistFormatError)
        verify(mockDao, never()).delete(old)
        verify(mockDao, never()).insert(new)
    }

    @Test
    fun whenDeleteRequestedThenDeletionConfirmed() {
        val entry = UserWhitelistedDomain(DOMAIN)
        testee.onDeleteRequested(entry)
        verify(mockCommandObserver).onChanged(commandCaptor.capture())
        val lastValue = commandCaptor.lastValue as Command.ConfirmDelete
        assertEquals(entry, lastValue.entry)
    }

    @Test
    fun whenDeletedThenDaoUpdated() {
        val entry = UserWhitelistedDomain(DOMAIN)
        testee.onEntryDeleted(entry)
        verify(mockDao).delete(entry)
    }

    companion object {
        private const val DOMAIN = "example.com"
        private const val NEW_DOMAIN = "new.example.com"
        private const val INVALID_DOMAIN = "_"
    }
}
