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

package com.duckduckgo.clicktoload.store

import com.duckduckgo.app.CoroutineTestRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
class ClickToLoadRepositoryTest {
    @get:Rule var coroutineRule = CoroutineTestRule()

    private lateinit var testee: RealClickToLoadRepository

    private val mockDatabase: ClickToLoadDatabase = mock()
    private val mockClickToLoadDao: ClickToLoadDao = mock()

    @Before
    fun before() {
        whenever(mockClickToLoadDao.get()).thenReturn(null)
        whenever(mockDatabase.clickToLoadDao()).thenReturn(mockClickToLoadDao)
    }

    @Test
    fun whenInitializedAndDoesNotHaveStoredValueThenLoadEmptyJsonToMemory() =
        runTest {
            testee =
                RealClickToLoadRepository(
                    mockDatabase,
                    TestScope(),
                    coroutineRule.testDispatcherProvider,
                )

            verify(mockClickToLoadDao).get()
            assertEquals("{}", testee.clickToLoadEntity.json)
        }

    @Test
    fun whenInitializedAndHasStoredValueThenLoadStoredJsonToMemory() =
        runTest {
            whenever(mockClickToLoadDao.get()).thenReturn(clickToLoadEntity)
            testee =
                RealClickToLoadRepository(
                    mockDatabase,
                    TestScope(),
                    coroutineRule.testDispatcherProvider,
                )

            verify(mockClickToLoadDao).get()
            assertEquals(clickToLoadEntity.json, testee.clickToLoadEntity.json)
        }

    @Test
    fun whenUpdateAllThenUpdateAllCalled() =
        runTest {
            testee =
                RealClickToLoadRepository(
                    mockDatabase,
                    TestScope(),
                    coroutineRule.testDispatcherProvider,
                )

            testee.updateAll(clickToLoadEntity)

            verify(mockClickToLoadDao).updateAll(clickToLoadEntity)
        }

    companion object {
        val clickToLoadEntity = ClickToLoadEntity(json = "{\"key\":\"value\"}")
    }
}
