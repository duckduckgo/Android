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

import com.duckduckgo.app.FileUtilities
import com.duckduckgo.sync.TestSyncFixtures
import com.duckduckgo.sync.api.engine.ModifiedSince.FirstSync
import com.duckduckgo.sync.api.engine.SyncChangesRequest
import com.duckduckgo.sync.api.engine.SyncableType.BOOKMARKS
import com.duckduckgo.sync.impl.Result
import com.duckduckgo.sync.impl.SyncApi
import com.duckduckgo.sync.store.SyncStore
import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertTrue
import org.json.JSONObject
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal class SyncApiClientTest {

    private val syncStore: SyncStore = mock()
    private val syncApi: SyncApi = mock()
    private lateinit var apiClient: AppSyncApiClient

    val patchAllError = Result.Error(-1, "Patch All Error")
    val getAllError = Result.Error(-1, "Get All Error")

    @Before
    fun before() {
        apiClient = AppSyncApiClient(syncStore, syncApi)
    }

    @Test
    fun whenPatchAndTokenEmptyThenReturnError() {
        whenever(syncStore.token).thenReturn("")

        val result = apiClient.patch(SyncChangesRequest.empty())

        assertEquals(result, Result.Error(reason = "Token Empty"))
    }

    @Test
    fun whenPatchAndChangesEmptyThenReturnError() {
        whenever(syncStore.token).thenReturn(TestSyncFixtures.token)

        val result = apiClient.patch(SyncChangesRequest.empty())

        assertEquals(result, Result.Error(reason = "Changes Empty"))
    }

    @Test
    fun whenPatchAndBookmarkChangesThenApiIsSuccessful() {
        val updatesJSON = FileUtilities.loadText(javaClass.classLoader!!, "data_sync_sent_bookmarks.json")
        val bookmarksChanges = SyncChangesRequest(BOOKMARKS, updatesJSON, FirstSync)
        whenever(syncStore.token).thenReturn(TestSyncFixtures.token)
        whenever(syncApi.patch(any(), any())).thenReturn(Result.Success(JSONObject()))

        val result = apiClient.patch(bookmarksChanges)
        assertTrue(result is Result.Success)
    }

    @Test
    fun whenPatchAndBookmarkChangesThenApiFails() {
        val updatesJSON = FileUtilities.loadText(javaClass.classLoader!!, "data_sync_sent_bookmarks.json")
        val bookmarksChanges = SyncChangesRequest(BOOKMARKS, updatesJSON, FirstSync)
        whenever(syncStore.token).thenReturn(TestSyncFixtures.token)
        whenever(syncApi.patch(any(), any())).thenReturn(patchAllError)

        val result = apiClient.patch(bookmarksChanges)
        assertTrue(result is Result.Error)
    }

    @Test
    fun whenMappingChangesThenGeneratedObjectIsCorrect() {
        val updatesJSON = FileUtilities.loadText(javaClass.classLoader!!, "data_sync_sent_bookmarks.json")
        val bookmarksChanges = SyncChangesRequest(BOOKMARKS, updatesJSON, FirstSync)
        val changes = apiClient.mapRequest(listOf(bookmarksChanges))
        assertTrue(changes.get("client_timestamp") != null)
        assertTrue(changes.get("bookmarks") != null)
    }

    @Test
    fun whenGetAndTokenEmptyThenReturnError() {
        whenever(syncStore.token).thenReturn("")

        val result = apiClient.get(BOOKMARKS, "")

        assertEquals(result, Result.Error(reason = "Token Empty"))
    }

    @Test
    fun whenGetAndApiFailsThenResultIsError() {
        whenever(syncStore.token).thenReturn(TestSyncFixtures.token)
        whenever(syncApi.getBookmarks(any(), any())).thenReturn(getAllError)

        val result = apiClient.get(BOOKMARKS, "")
        assertTrue(result is Result.Error)
    }
}
