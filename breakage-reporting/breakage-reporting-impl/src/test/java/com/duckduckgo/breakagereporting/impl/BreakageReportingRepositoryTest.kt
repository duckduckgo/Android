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

package com.duckduckgo.breakagereporting.impl

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

class BreakageReportingRepositoryTest {
    @get:Rule var coroutineRule = CoroutineTestRule()

    private lateinit var testee: BreakageReportingRepository

    private val mockDatabase: BreakageReportingDatabase = mock()
    private val mockBreakageReportingDao: BreakageReportingDao = mock()

    @Before
    fun before() {
        whenever(mockBreakageReportingDao.get()).thenReturn(null)
        whenever(mockDatabase.breakageReportingDao()).thenReturn(mockBreakageReportingDao)
    }

    @Test
    fun whenInitializedAndDoesNotHaveStoredValueThenLoadEmptyJsonToMemory() =
        runTest {
            testee =
                RealBreakageReportingRepository(
                    mockDatabase,
                    TestScope(),
                    coroutineRule.testDispatcherProvider,
                    isMainProcess = true,
                )

            verify(mockBreakageReportingDao).get()
            assertEquals("{}", testee.getBreakageReportingEntity().json)
        }

    @Test
    fun whenInitializedAndHasStoredValueThenLoadStoredJsonToMemory() =
        runTest {
            whenever(mockBreakageReportingDao.get()).thenReturn(breakageReportingEntity)
            testee =
                RealBreakageReportingRepository(
                    mockDatabase,
                    TestScope(),
                    coroutineRule.testDispatcherProvider,
                    isMainProcess = true,
                )

            verify(mockBreakageReportingDao).get()
            assertEquals(breakageReportingEntity.json, testee.getBreakageReportingEntity().json)
        }

    @Test
    fun whenUpdateAllThenUpdateAllCalled() =
        runTest {
            testee =
                RealBreakageReportingRepository(
                    mockDatabase,
                    TestScope(),
                    coroutineRule.testDispatcherProvider,
                    isMainProcess = true,
                )

            testee.updateAll(breakageReportingEntity)

            verify(mockBreakageReportingDao).updateAll(breakageReportingEntity)
        }

    companion object {
        val breakageReportingEntity =
            BreakageReportingEntity(json = "{\"key\":\"value\"}")
    }
}
