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

import com.duckduckgo.common.test.FileUtilities
import com.duckduckgo.sync.TestSyncFixtures
import com.duckduckgo.sync.api.engine.DeletableType.DUCK_AI_CHATS
import com.duckduckgo.sync.api.engine.ModifiedSince.FirstSync
import com.duckduckgo.sync.api.engine.SyncChangesRequest
import com.duckduckgo.sync.api.engine.SyncDeletionRequest
import com.duckduckgo.sync.api.engine.SyncableType.BOOKMARKS
import com.duckduckgo.sync.api.engine.SyncableType.CREDENTIALS
import com.duckduckgo.sync.impl.API_CODE
import com.duckduckgo.sync.impl.Result
import com.duckduckgo.sync.impl.SyncApi
import com.duckduckgo.sync.impl.error.SyncApiErrorRecorder
import com.duckduckgo.sync.store.SyncStore
import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertTrue
import org.json.JSONObject
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

internal class SyncApiClientTest {

    private val syncStore: SyncStore = mock()
    private val syncApi: SyncApi = mock()
    private val syncApiErrorRecorder: SyncApiErrorRecorder = mock()
    private lateinit var apiClient: AppSyncApiClient

    private val patchAllError = Result.Error(-1, "Patch All Error")
    private val getAllError = Result.Error(-1, "Get All Error")
    private val getCountLimitError = Result.Error(API_CODE.COUNT_LIMIT.code, "Get Count Limit Error")

    @Before
    fun before() {
        apiClient = AppSyncApiClient(syncStore, syncApi, syncApiErrorRecorder)
    }

    @Test
    fun whenPatchAndTokenEmptyThenReturnError() {
        whenever(syncStore.token).thenReturn("")

        val result = apiClient.patch(SyncChangesRequest.empty())

        assertEquals(result, Result.Error(reason = "Token Empty"))
        verifyNoInteractions(syncApiErrorRecorder)
    }

    @Test
    fun whenPatchAndChangesEmptyThenReturnError() {
        whenever(syncStore.token).thenReturn(TestSyncFixtures.token)

        val result = apiClient.patch(SyncChangesRequest.empty())

        assertEquals(result, Result.Error(reason = "Changes Empty"))
        verifyNoInteractions(syncApiErrorRecorder)
    }

    @Test
    fun whenPatchAndBookmarkChangesThenApiIsSuccessful() {
        val updatesJSON = FileUtilities.loadText(javaClass.classLoader!!, "data_sync_sent_bookmarks.json")
        val bookmarksChanges = SyncChangesRequest(BOOKMARKS, updatesJSON, FirstSync)
        whenever(syncStore.token).thenReturn(TestSyncFixtures.token)
        whenever(syncApi.patch(any(), any())).thenReturn(Result.Success(JSONObject()))

        val result = apiClient.patch(bookmarksChanges)
        assertTrue(result is Result.Success)
        verifyNoInteractions(syncApiErrorRecorder)
    }

    @Test
    fun whenPatchAndBookmarkChangesThenApiFailsAndApiErrorIsRecorded() {
        val updatesJSON = FileUtilities.loadText(javaClass.classLoader!!, "data_sync_sent_bookmarks.json")
        val bookmarksChanges = SyncChangesRequest(BOOKMARKS, updatesJSON, FirstSync)
        whenever(syncStore.token).thenReturn(TestSyncFixtures.token)
        whenever(syncApi.patch(any(), any())).thenReturn(patchAllError)

        val result = apiClient.patch(bookmarksChanges)
        assertTrue(result is Result.Error)
        verify(syncApiErrorRecorder).record(BOOKMARKS, patchAllError)
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
    fun whenGetBookmarksAndTokenEmptyThenReturnError() {
        whenever(syncStore.token).thenReturn("")

        val result = apiClient.get(BOOKMARKS, "")

        assertEquals(result, Result.Error(reason = "Token Empty"))
        verifyNoInteractions(syncApiErrorRecorder)
    }

    @Test
    fun whenGetCredentialsBookmarksAndTokenEmptyThenReturnError() {
        whenever(syncStore.token).thenReturn("")

        val result = apiClient.get(CREDENTIALS, "")

        assertEquals(result, Result.Error(reason = "Token Empty"))
        verifyNoInteractions(syncApiErrorRecorder)
    }

    @Test
    fun whenGetBookmarksAndApiFailsThenResultIsErrorAndErrorIsRecorded() {
        whenever(syncStore.token).thenReturn(TestSyncFixtures.token)
        whenever(syncApi.getBookmarks(any(), any())).thenReturn(getAllError)

        val result = apiClient.get(BOOKMARKS, "")
        assertTrue(result is Result.Error)
        verify(syncApiErrorRecorder).record(BOOKMARKS, getAllError)
    }

    @Test
    fun whenGetCredentialsAndApiFailsThenResultIsErrorAndErrorIsRecorded() {
        whenever(syncStore.token).thenReturn(TestSyncFixtures.token)
        whenever(syncApi.getCredentials(any(), any())).thenReturn(getAllError)

        val result = apiClient.get(CREDENTIALS, "")
        assertTrue(result is Result.Error)
        verify(syncApiErrorRecorder).record(CREDENTIALS, getAllError)
    }

    @Test
    fun whenGetBookmarksAndApiCountLimitFailsThenResultIsErrorAndErrorIsRecorded() {
        whenever(syncStore.token).thenReturn(TestSyncFixtures.token)
        whenever(syncApi.getBookmarks(any(), any())).thenReturn(getCountLimitError)

        val result = apiClient.get(BOOKMARKS, "")
        assertTrue(result is Result.Error)
        verify(syncApiErrorRecorder).record(BOOKMARKS, getCountLimitError)
    }

    @Test
    fun whenGetCredentialsAndApiCountLimitFailsThenResultIsErrorAndErrorIsRecorded() {
        whenever(syncStore.token).thenReturn(TestSyncFixtures.token)
        whenever(syncApi.getCredentials(any(), any())).thenReturn(getCountLimitError)

        val result = apiClient.get(CREDENTIALS, "")
        assertTrue(result is Result.Error)
        verify(syncApiErrorRecorder).record(CREDENTIALS, getCountLimitError)
    }

    @Test
    fun whenDeleteAndTokenEmptyThenReturnError() {
        whenever(syncStore.token).thenReturn("")

        val result = apiClient.delete(SyncDeletionRequest(DUCK_AI_CHATS, "timestamp"))

        assertEquals(result, Result.Error(reason = "Token Empty"))
        verifyNoInteractions(syncApiErrorRecorder)
    }

    @Test
    fun whenDeleteDuckAiChatsAndApiSucceedsThenReturnSuccess() {
        val timestamp = "2025-01-01T00:00:00Z"
        whenever(syncStore.token).thenReturn(TestSyncFixtures.token)
        whenever(syncApi.deleteAiChats(any(), any())).thenReturn(Result.Success(Unit))

        val result = apiClient.delete(SyncDeletionRequest(DUCK_AI_CHATS, timestamp))

        assertTrue(result is Result.Success)
        verifyNoInteractions(syncApiErrorRecorder)
    }

    @Test
    fun whenDeleteDuckAiChatsAndApiFailsThenReturnErrorAndRecordError() {
        val timestamp = "2025-01-01T00:00:00Z"
        val deleteError = Result.Error(-1, "Delete Error")
        whenever(syncStore.token).thenReturn(TestSyncFixtures.token)
        whenever(syncApi.deleteAiChats(any(), any())).thenReturn(deleteError)

        val result = apiClient.delete(SyncDeletionRequest(DUCK_AI_CHATS, timestamp))

        assertTrue(result is Result.Error)
        verify(syncApiErrorRecorder).record(DUCK_AI_CHATS, deleteError)
    }
}
