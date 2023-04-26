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

package com.duckduckgo.sync.impl.engine

import com.duckduckgo.sync.TestSyncFixtures
import com.duckduckgo.sync.impl.Result
import com.duckduckgo.sync.impl.SyncApi
import com.duckduckgo.sync.store.SyncStore
import junit.framework.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal class SyncApiClientTest {

    private val syncStore: SyncStore = mock()
    private val syncApi: SyncApi = mock()
    private lateinit var apiClient: SyncApiClient

    @Before
    fun before() {
        apiClient = AppSyncApiClient(syncStore, syncApi)
    }

    @Test
    fun whenPatchAndTokenEmptyThenReturnError() {
        whenever(syncStore.token).thenReturn("")

        val result = apiClient.patch(emptyList())

        assertEquals(result, Result.Error(reason = "Token Empty"))
    }

    @Test
    fun whenPatchAndChangesEmptyThenReturnError() {
        whenever(syncStore.token).thenReturn(TestSyncFixtures.token)

        val result = apiClient.patch(emptyList())

        assertEquals(result, Result.Error(reason = "Changes Empty"))
    }
}
