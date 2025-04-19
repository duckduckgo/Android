/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.request.filterer.store

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.request.filterer.store.RealRequestFiltererRepository.Companion.DEFAULT_WINDOW_IN_MS
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RealRequestFiltererRepositoryTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    lateinit var testee: RealRequestFiltererRepository

    private val mockDatabase: RequestFiltererDatabase = mock()
    private val mockRequestFiltererDao: RequestFiltererDao = mock()

    @Before
    fun before() {
        whenever(mockDatabase.requestFiltererDao()).thenReturn(mockRequestFiltererDao)
    }

    @Test
    fun whenRepositoryIsCreatedThenValuesLoadedIntoMemory() {
        givenRequestFiltererDaoHasContent()

        testee = RealRequestFiltererRepository(
            mockDatabase,
            TestScope(),
            coroutineRule.testDispatcherProvider,
            true,
        )

        assertEquals(requestFiltererExceptionEntity.toFeatureException(), testee.exceptions.first())
        assertEquals(WINDOW_IN_MS, testee.settings.windowInMs)
    }

    @Test
    fun whenLoadToMemoryAndNoSettingsThenSetDefaultValues() {
        whenever(mockRequestFiltererDao.getSettings()).thenReturn(null)

        testee = RealRequestFiltererRepository(
            mockDatabase,
            TestScope(),
            coroutineRule.testDispatcherProvider,
            true,
        )

        assertEquals(DEFAULT_WINDOW_IN_MS, testee.settings.windowInMs)
    }

    @Test
    fun whenUpdateAllThenUpdateAllCalled() {
        val policy = SettingsEntity(2, 600)

        testee = RealRequestFiltererRepository(
            mockDatabase,
            TestScope(),
            coroutineRule.testDispatcherProvider,
            true,
        )

        testee.updateAll(listOf(), policy)

        verify(mockRequestFiltererDao).updateAll(emptyList(), policy)
    }

    @Test
    fun whenUpdateAllThenPreviousValuesAreCleared() {
        givenRequestFiltererDaoHasContent()

        testee = RealRequestFiltererRepository(
            mockDatabase,
            TestScope(),
            coroutineRule.testDispatcherProvider,
            true,
        )
        assertEquals(1, testee.exceptions.size)
        assertEquals(WINDOW_IN_MS, testee.settings.windowInMs)

        reset(mockRequestFiltererDao)

        testee.updateAll(listOf(), SettingsEntity(2, 600))

        assertEquals(0, testee.exceptions.size)
    }

    private fun givenRequestFiltererDaoHasContent() {
        whenever(mockRequestFiltererDao.getAllRequestFiltererExceptions()).thenReturn(listOf(requestFiltererExceptionEntity))
        whenever(mockRequestFiltererDao.getSettings()).thenReturn(SettingsEntity(1, WINDOW_IN_MS))
    }

    companion object {
        val requestFiltererExceptionEntity = RequestFiltererExceptionEntity(
            domain = "https://www.example.com",
            reason = "reason",
        )

        const val WINDOW_IN_MS = 100
    }
}
