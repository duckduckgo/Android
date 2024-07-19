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

package com.duckduckgo.performancemetrics.store

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

class PerformanceMetricsRepositoryTest {
    @get:Rule var coroutineRule = CoroutineTestRule()

    private lateinit var testee: RealPerformanceMetricsRepository

    private val mockDatabase: PerformanceMetricsDatabase = mock()
    private val mockPerformanceMetricsDao: PerformanceMetricsDao = mock()

    @Before
    fun before() {
        whenever(mockPerformanceMetricsDao.get()).thenReturn(null)
        whenever(mockDatabase.performanceMetricsDao()).thenReturn(mockPerformanceMetricsDao)
    }

    @Test
    fun whenInitializedAndDoesNotHaveStoredValueThenLoadEmptyJsonToMemory() =
        runTest {
            testee =
                RealPerformanceMetricsRepository(
                    mockDatabase,
                    TestScope(),
                    coroutineRule.testDispatcherProvider,
                    isMainProcess = true,
                )

            verify(mockPerformanceMetricsDao).get()
            assertEquals("{}", testee.getPerformanceMetricsEntity().json)
        }

    @Test
    fun whenInitializedAndHasStoredValueThenLoadStoredJsonToMemory() =
        runTest {
            whenever(mockPerformanceMetricsDao.get()).thenReturn(performanceMetricsEntity)
            testee =
                RealPerformanceMetricsRepository(
                    mockDatabase,
                    TestScope(),
                    coroutineRule.testDispatcherProvider,
                    isMainProcess = true,
                )

            verify(mockPerformanceMetricsDao).get()
            assertEquals(performanceMetricsEntity.json, testee.getPerformanceMetricsEntity().json)
        }

    @Test
    fun whenUpdateAllThenUpdateAllCalled() =
        runTest {
            testee =
                RealPerformanceMetricsRepository(
                    mockDatabase,
                    TestScope(),
                    coroutineRule.testDispatcherProvider,
                    isMainProcess = true,
                )

            testee.updateAll(performanceMetricsEntity)

            verify(mockPerformanceMetricsDao).updateAll(performanceMetricsEntity)
        }

    companion object {
        val performanceMetricsEntity = PerformanceMetricsEntity(json = "{\"key\":\"value\"}")
    }
}
