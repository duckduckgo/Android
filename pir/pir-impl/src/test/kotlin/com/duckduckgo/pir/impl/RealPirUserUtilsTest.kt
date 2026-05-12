/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.pir.impl

import com.duckduckgo.pir.impl.checker.PirWorkHandler
import com.duckduckgo.pir.impl.models.ProfileQuery
import com.duckduckgo.pir.impl.store.PirRepository
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RealPirUserUtilsTest {

    private lateinit var testee: RealPirUserUtils

    private val mockPirWorkHandler: PirWorkHandler = mock()
    private val mockPirRepository: PirRepository = mock()

    private val testProfileQuery = ProfileQuery(
        id = 1L,
        firstName = "John",
        lastName = "Doe",
        city = "New York",
        state = "NY",
        addresses = emptyList(),
        birthYear = 1990,
        fullName = "John Doe",
        age = 33,
        deprecated = false,
    )

    @Before
    fun setUp() {
        testee = RealPirUserUtils(
            pirWorkHandler = mockPirWorkHandler,
            pirRepository = mockPirRepository,
        )
    }

    @Test
    fun whenCanRunPirAndHasProfileQueriesThenIsActiveUserReturnsTrue() = runTest {
        whenever(mockPirWorkHandler.canRunPir()).thenReturn(flowOf(true))
        whenever(mockPirRepository.getValidUserProfileQueries()).thenReturn(listOf(testProfileQuery))

        val result = testee.isActiveUser()

        assertTrue(result)
    }

    @Test
    fun whenCannotRunPirThenIsActiveUserReturnsFalse() = runTest {
        whenever(mockPirWorkHandler.canRunPir()).thenReturn(flowOf(false))
        whenever(mockPirRepository.getValidUserProfileQueries()).thenReturn(listOf(testProfileQuery))

        val result = testee.isActiveUser()

        assertFalse(result)
    }

    @Test
    fun whenCanRunPirButNoProfileQueriesThenIsActiveUserReturnsFalse() = runTest {
        whenever(mockPirWorkHandler.canRunPir()).thenReturn(flowOf(true))
        whenever(mockPirRepository.getValidUserProfileQueries()).thenReturn(emptyList())

        val result = testee.isActiveUser()

        assertFalse(result)
    }

    @Test
    fun whenCannotRunPirAndNoProfileQueriesThenIsActiveUserReturnsFalse() = runTest {
        whenever(mockPirWorkHandler.canRunPir()).thenReturn(flowOf(false))
        whenever(mockPirRepository.getValidUserProfileQueries()).thenReturn(emptyList())

        val result = testee.isActiveUser()

        assertFalse(result)
    }

    @Test
    fun whenCanRunPirFlowIsEmptyThenIsActiveUserReturnsFalse() = runTest {
        whenever(mockPirWorkHandler.canRunPir()).thenReturn(emptyFlow())
        whenever(mockPirRepository.getValidUserProfileQueries()).thenReturn(listOf(testProfileQuery))

        val result = testee.isActiveUser()

        assertFalse(result)
    }

    @Test
    fun whenHasMultipleProfileQueriesAndCanRunPirThenIsActiveUserReturnsTrue() = runTest {
        val profileQuery2 = ProfileQuery(
            id = 2L,
            firstName = "Jane",
            lastName = "Smith",
            city = "Los Angeles",
            state = "CA",
            addresses = emptyList(),
            birthYear = 1985,
            fullName = "Jane Smith",
            age = 38,
            deprecated = false,
        )
        whenever(mockPirWorkHandler.canRunPir()).thenReturn(flowOf(true))
        whenever(mockPirRepository.getValidUserProfileQueries()).thenReturn(listOf(testProfileQuery, profileQuery2))

        val result = testee.isActiveUser()

        assertTrue(result)
    }
}
