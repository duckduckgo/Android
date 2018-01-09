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

package com.duckduckgo.app.privacymonitor.model

import com.duckduckgo.app.privacymonitor.db.NetworkLeaderboardDao
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import org.junit.Assert.*
import org.junit.Test

class DatabaseNetworkLeaderboardTest {

    private val dao: NetworkLeaderboardDao = mock()

    private val testee: NetworkLeaderboard by lazy {
        DatabaseNetworkLeaderboard(dao)
    }

    @Test
    fun delegatesToDaoWhenNetworkDetected() {
        testee.onNetworkDetected("Network1", "www.example.com")
        verify(dao).insert(any())
    }

    @Test
    fun delegatesToDaoForTotalDomains() {
        whenever(dao.totalDomainsVisited()).thenReturn(666)
        assertEquals(666, testee.totalDomainsVisited())
    }

    @Test
    fun delegatesToDaoForNetworkPercents() {
        whenever(dao.networkPercents(0)).thenReturn(arrayOf(NetworkPercent("Network1", 66.6f)))
        assertEquals(1, testee.networkPercents().size)
        assertEquals("Network1", testee.networkPercents()[0].networkName)
        assertEquals(66.6f, testee.networkPercents()[0].percent)
    }

    @Test
    fun delegatesToDaoForShouldShow() {
        whenever(dao.shouldShow()).thenReturn(true)
        assertTrue(testee.shouldShow())
    }

}