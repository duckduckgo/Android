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

package com.duckduckgo.app.launch.seeder

import com.duckduckgo.app.privacy.db.UserAllowListDao
import com.duckduckgo.app.privacy.db.UserAllowListRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class UserAllowListSeederPluginTest {

    private val dao: UserAllowListDao = mock()
    private val repository: UserAllowListRepository = mock()
    private val plugin = UserAllowListSeederPlugin(repository, dao)

    @Test
    fun whenDbAlreadyContainsDomainAndMirrorIsStaleEmptyThenClearingEmptyRequestClearsDb() = runTest {
        // First read (pre-clear) sees the stale row; second read (post-clear) confirms it's gone.
        whenever(dao.allDomainsFlow()).thenReturn(flowOf(listOf("127.0.0.1")), flowOf(emptyList()))
        whenever(repository.domainsInUserAllowListFlow()).thenReturn(flowOf(emptyList()))

        plugin.apply(key = "userAllowList", value = "")

        verify(repository).removeDomainFromUserAllowList("127.0.0.1")
        verify(repository, never()).addDomainToUserAllowList(any())
    }

    @Test
    fun whenDbIsEmptyThenRequestedDomainIsAddedAndApplyReturns() = runTest {
        whenever(dao.allDomainsFlow()).thenReturn(flowOf(emptyList()), flowOf(listOf("127.0.0.1")))
        whenever(repository.domainsInUserAllowListFlow()).thenReturn(flowOf(listOf("127.0.0.1")))

        plugin.apply(key = "userAllowList", value = "127.0.0.1")

        verify(repository).addDomainToUserAllowList("127.0.0.1")
        verify(repository, never()).removeDomainFromUserAllowList(any())
    }

    @Test
    fun whenRepositoryWritesDoNotReachRoomThenApplyThrows() = runTest {
        // The DAO never reflects the write, modelling a repository whose writes don't persist.
        whenever(dao.allDomainsFlow()).thenReturn(flowOf(emptyList()))

        var thrown: IllegalStateException? = null
        try {
            plugin.apply(key = "userAllowList", value = "127.0.0.1")
        } catch (e: IllegalStateException) {
            thrown = e
        }
        assertNotNull(thrown)
    }
}
