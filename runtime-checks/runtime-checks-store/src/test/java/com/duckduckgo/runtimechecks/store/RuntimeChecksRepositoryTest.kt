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

package com.duckduckgo.runtimechecks.store

import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RuntimeChecksRepositoryTest {
    @get:Rule var coroutineRule = CoroutineTestRule()

    private lateinit var testee: RealRuntimeChecksRepository

    private val mockDatabase: RuntimeChecksDatabase = mock()
    private val mockRuntimeChecksDao: RuntimeChecksDao = mock()

    @Before
    fun before() {
        whenever(mockRuntimeChecksDao.get()).thenReturn(null)
        whenever(mockDatabase.runtimeChecksDao()).thenReturn(mockRuntimeChecksDao)
    }

    @Test
    fun whenInitializedAndDoesNotHaveStoredValueThenLoadEmptyJsonToMemory() =
        runTest {
            testee =
                RealRuntimeChecksRepository(
                    mockDatabase,
                    TestScope(),
                    coroutineRule.testDispatcherProvider,
                    isMainProcess = true,
                )

            verify(mockRuntimeChecksDao).get()
            assertEquals("{}", testee.getRuntimeChecksEntity().json)
        }

    @Test
    fun whenInitializedAndHasStoredValueThenLoadStoredJsonToMemory() =
        runTest {
            whenever(mockRuntimeChecksDao.get()).thenReturn(runtimeChecksEntity)
            testee =
                RealRuntimeChecksRepository(
                    mockDatabase,
                    TestScope(),
                    coroutineRule.testDispatcherProvider,
                    isMainProcess = true,
                )

            verify(mockRuntimeChecksDao).get()
            assertEquals(runtimeChecksEntity.json, testee.getRuntimeChecksEntity().json)
        }

    @Test
    fun whenUpdateAllThenUpdateAllCalled() =
        runTest {
            testee =
                RealRuntimeChecksRepository(
                    mockDatabase,
                    TestScope(),
                    coroutineRule.testDispatcherProvider,
                    isMainProcess = true,
                )

            testee.updateAll(runtimeChecksEntity)

            verify(mockRuntimeChecksDao).updateAll(runtimeChecksEntity)
        }

    companion object {
        val runtimeChecksEntity = RuntimeChecksEntity(json = "{\"key\":\"value\"}")
    }
}
