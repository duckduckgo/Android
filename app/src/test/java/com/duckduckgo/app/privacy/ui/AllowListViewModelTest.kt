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
import com.duckduckgo.app.privacy.db.UserAllowListDao
import com.duckduckgo.app.privacy.model.UserAllowListedDomain
import com.duckduckgo.app.privacy.ui.AllowListViewModel.Command
import com.duckduckgo.app.privacy.ui.AllowListViewModel.Command.ShowAdd
import com.duckduckgo.app.privacy.ui.AllowListViewModel.Command.ShowAllowListFormatError
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.*

class AllowListViewModelTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private val mockDao: UserAllowListDao = mock()
    private val liveData = MutableLiveData<List<UserAllowListedDomain>>()

    private val mockCommandObserver: Observer<Command> = mock()
    private var commandCaptor: KArgumentCaptor<Command> = argumentCaptor()

    private val testee by lazy { AllowListViewModel(mockDao, TestScope(), coroutineRule.testDispatcherProvider) }

    @Before
    fun before() = runTest {
        liveData.value = emptyList()
        whenever(mockDao.all()).thenReturn(liveData)
        testee.command.observeForever(mockCommandObserver)
    }

    @After
    fun after() {
        testee.command.removeObserver(mockCommandObserver)
    }

    @Test
    fun whenAllowListUpdatedWithDataThenViewStateIsUpdatedAndAllowListDisplayed() {
        val list = listOf(UserAllowListedDomain(DOMAIN), UserAllowListedDomain(NEW_DOMAIN))
        liveData.postValue(list)
        val viewState = testee.viewState.value!!
        assertEquals(list, viewState.allowList)
        assertTrue(viewState.showAllowList)
    }

    @Test
    fun whenAllowListUpdatedWithEmptyListThenViewStateIsUpdatedAndAllowListNotDisplayed() {
        liveData.postValue(emptyList())
        val viewState = testee.viewState.value!!
        assertTrue(viewState.allowList.isEmpty())
        assertFalse(viewState.showAllowList)
    }

    @Test
    fun whenAddRequestedThenAddShown() {
        testee.onAddRequested()
        verify(mockCommandObserver).onChanged(ShowAdd)
    }

    @Test
    fun whenValidEntryAddedThenDaoUpdated() {
        val entry = UserAllowListedDomain(NEW_DOMAIN)
        testee.onEntryAdded(entry)
        verify(mockDao).insert(entry)
    }

    @Test
    fun whenInvalidEntryAddedThenErrorShownAndDaoNotUpdated() {
        val entry = UserAllowListedDomain(INVALID_DOMAIN)
        testee.onEntryAdded(entry)
        verify(mockCommandObserver).onChanged(ShowAllowListFormatError)
        verify(mockDao, never()).insert(entry)
    }

    @Test
    fun whenEditRequestedThenEditShown() {
        val entry = UserAllowListedDomain(DOMAIN)
        testee.onEditRequested(entry)
        verify(mockCommandObserver).onChanged(commandCaptor.capture())
        val lastValue = commandCaptor.lastValue as Command.ShowEdit
        assertEquals(entry, lastValue.entry)
    }

    @Test
    fun whenValidEditSubmittedThenDaoUpdated() {
        val old = UserAllowListedDomain(DOMAIN)
        val new = UserAllowListedDomain(NEW_DOMAIN)
        testee.onEntryEdited(old, new)
        verify(mockDao).delete(old)
        verify(mockDao).insert(new)
    }

    @Test
    fun whenValidEditSubmittedThenErrorShownAndDaoNotUpdated() {
        val old = UserAllowListedDomain(DOMAIN)
        val new = UserAllowListedDomain(INVALID_DOMAIN)
        testee.onEntryEdited(old, new)
        verify(mockCommandObserver).onChanged(ShowAllowListFormatError)
        verify(mockDao, never()).delete(old)
        verify(mockDao, never()).insert(new)
    }

    @Test
    fun whenDeleteRequestedThenDeletionConfirmed() {
        val entry = UserAllowListedDomain(DOMAIN)
        testee.onDeleteRequested(entry)
        verify(mockCommandObserver).onChanged(commandCaptor.capture())
        val lastValue = commandCaptor.lastValue as Command.ConfirmDelete
        assertEquals(entry, lastValue.entry)
    }

    @Test
    fun whenDeletedThenDaoUpdated() {
        val entry = UserAllowListedDomain(DOMAIN)
        testee.onEntryDeleted(entry)
        verify(mockDao).delete(entry)
    }

    companion object {
        private const val DOMAIN = "example.com"
        private const val NEW_DOMAIN = "new.example.com"
        private const val INVALID_DOMAIN = "_"
    }
}
