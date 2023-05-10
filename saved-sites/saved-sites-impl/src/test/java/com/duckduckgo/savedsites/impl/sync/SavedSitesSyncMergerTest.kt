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

package com.duckduckgo.savedsites.impl.sync

import com.duckduckgo.app.FileUtilities
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.sync.api.SyncCrypto
import com.duckduckgo.sync.api.engine.FeatureSyncStore
import com.duckduckgo.sync.api.engine.SyncChanges
import com.duckduckgo.sync.api.engine.SyncMergeResult
import com.duckduckgo.sync.api.engine.SyncableType.BOOKMARKS
import junit.framework.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SavedSitesSyncMergerTest {

    private val repository: SavedSitesRepository = mock()
    private val syncCrypto: SyncCrypto = mock()
    private val savedSitesSyncStore: FeatureSyncStore = mock()
    private val duplicateFinder: SavedSitesDuplicateFinder = mock()
    private lateinit var parser: SavedSitesSyncMerger

    @Before
    fun before() {
        parser = SavedSitesSyncMerger(repository, savedSitesSyncStore, duplicateFinder, syncCrypto)

        whenever(syncCrypto.decrypt(ArgumentMatchers.anyString()))
            .thenAnswer { invocation -> invocation.getArgument(0) }
    }

    @Test
    fun whenMergingCorruptListOfChangesThenResultIsError() {
        val updatesJSON = FileUtilities.loadText(javaClass.classLoader!!, "json/corrupted_data.json")
        val corruptedChanges = SyncChanges(BOOKMARKS, updatesJSON)
        val result = parser.merge(corruptedChanges)

        assertTrue(result is SyncMergeResult.Error)
    }

    @Test
    fun whenMergingChangesInEmptyDBDataIsStoredSuccessfully() {
        whenever(repository.hasBookmarks()).thenReturn(false)
        whenever(duplicateFinder.findFolderDuplicate(any())).thenReturn(SavedSitesDuplicateResult.NotDuplicate)
        whenever(duplicateFinder.findBookmarkDuplicate(any())).thenReturn(SavedSitesDuplicateResult.NotDuplicate)
        whenever(duplicateFinder.findFavouriteDuplicate(any())).thenReturn(SavedSitesDuplicateResult.NotDuplicate)
        val updatesJSON = FileUtilities.loadText(javaClass.classLoader!!, "json/first_sync_get_data.json")
        val remoteChanges = SyncChanges(BOOKMARKS, updatesJSON)

        val result = parser.syncChanges(listOf(remoteChanges), "")

        assertTrue(result is SyncMergeResult.Success)
    }
}
