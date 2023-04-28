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
import com.duckduckgo.sync.api.engine.SyncChanges
import com.duckduckgo.sync.api.engine.SyncableType.BOOKMARKS
import com.duckduckgo.sync.impl.BookmarksResponse
import com.duckduckgo.sync.impl.DeviceDataResponse
import com.duckduckgo.sync.impl.Result
import com.duckduckgo.sync.impl.SettingsResponse
import com.duckduckgo.sync.impl.SyncApi
import com.duckduckgo.sync.impl.SyncDataResponse
import com.duckduckgo.sync.store.SyncStore
import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal class SyncApiClientTest {

    private val syncStore: SyncStore = mock()
    private val syncApi: SyncApi = mock()
    private lateinit var apiClient: AppSyncApiClient

    private val firstSyncWithBookmarksAndFavorites = "{\"bookmarks\":{\"updates\":[{\"client_last_modified\":\"timestamp\",\"folder\":{\"children\":[\"bookmark1\"]},\"id\":\"favorites_root\",\"title\":\"Favorites\"},{\"client_last_modified\":\"timestamp\",\"id\":\"bookmark3\",\"page\":{\"url\":\"https://bookmark3.com\"},\"title\":\"Bookmark 3\"},{\"client_last_modified\":\"timestamp\",\"id\":\"bookmark4\",\"page\":{\"url\":\"https://bookmark4.com\"},\"title\":\"Bookmark 4\"},{\"client_last_modified\":\"timestamp\",\"folder\":{\"children\":[\"bookmark3\",\"bookmark4\"]},\"id\":\"bookmarks_root\",\"title\":\"Bookmarks\"}]}}"
    val bookmarksResponse = BookmarksResponse("lastModified", emptyList())
    val settingsResponse = SettingsResponse("lastModified", emptyList())
    val devicessResponse = DeviceDataResponse("lastModified", emptyList())
    val syncDataResponse = SyncDataResponse(bookmarksResponse, settingsResponse, devicessResponse)
    val patchAllError = Result.Error(-1, "Patch All Error")
    val getAllError = Result.Error(-1, "Get All Error")

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

    @Test
    fun whenPatchAndBookmarkChangesThenApiIsSuccessful(){
        val bookmarksChanges = SyncChanges(BOOKMARKS, firstSyncWithBookmarksAndFavorites)
        whenever(syncStore.token).thenReturn(TestSyncFixtures.token)
        whenever(syncApi.patch(any(), any())).thenReturn(Result.Success(syncDataResponse))

        val result = apiClient.patch(listOf(bookmarksChanges))
        assertTrue(result is Result.Success)
    }

    @Test
    fun whenPatchAndBookmarkChangesThenApiFails(){
        val bookmarksChanges = SyncChanges(BOOKMARKS, firstSyncWithBookmarksAndFavorites)
        whenever(syncStore.token).thenReturn(TestSyncFixtures.token)
        whenever(syncApi.patch(any(), any())).thenReturn(patchAllError)

        val result = apiClient.patch(listOf(bookmarksChanges))
        assertTrue(result is Result.Error)
    }

    @Test
    fun whenMappingChangesThenGeneratedObjectIsCorrect(){
        val bookmarksChanges = SyncChanges(BOOKMARKS, firstSyncWithBookmarksAndFavorites)
        val changes = apiClient.mapRequest(listOf(bookmarksChanges))
        assertTrue(changes.clientTimestamp.isNotEmpty())
        assertTrue(changes.bookmarks.updates.size == 4)
    }

    @Test
    fun whenGetAndTokenEmptyThenReturnError() {
        whenever(syncStore.token).thenReturn("")

        val result = apiClient.get()

        assertEquals(result, Result.Error(reason = "Token Empty"))
    }

    @Test
    fun whenGetAndApiFailsThenResultIsError(){
        whenever(syncStore.token).thenReturn(TestSyncFixtures.token)
        whenever(syncApi.getAllData(any())).thenReturn(getAllError)

        val result = apiClient.get()
        assertTrue(result is Result.Error)
    }
}
