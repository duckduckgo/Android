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

package com.duckduckgo.savedsites.impl.sync.algorithm

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.savedsites.api.models.BookmarkFolder
import com.duckduckgo.savedsites.api.models.SavedSite.Bookmark
import com.duckduckgo.savedsites.impl.sync.SyncSavedSitesRepository
import com.duckduckgo.savedsites.impl.sync.algorithm.SavedSitesDuplicateResult.Duplicate
import com.duckduckgo.savedsites.impl.sync.algorithm.SavedSitesDuplicateResult.NotDuplicate
import com.squareup.anvil.annotations.ContributesBinding
import logcat.logcat
import javax.inject.Inject
import javax.inject.Named

@ContributesBinding(AppScope::class)
@Named("deduplicationStrategy")
class SavedSitesDeduplicationPersister @Inject constructor(
    private val savedSitesRepository: SavedSitesRepository,
    private val syncSavedSitesRepository: SyncSavedSitesRepository,
    private val duplicateFinder: SavedSitesDuplicateFinder,
) : SavedSitesSyncPersisterStrategy {
    override fun processBookmarkFolder(
        folder: BookmarkFolder,
        children: List<String>,
    ) {
        // in deduplication we replace local folder with remote folder (id, name, parentId, add children to existent ones)
        // we don't replace the children here, because they might be children already present before the deduplication
        when (val result = duplicateFinder.findFolderDuplicate(folder)) {
            is Duplicate -> {
                if (folder.isDeleted()) {
                    logcat { "Sync-Bookmarks-Persister: folder ${folder.id} has a local duplicate in ${result.id} and needs to be deleted" }
                    savedSitesRepository.delete(folder)
                } else {
                    logcat { "Sync-Bookmarks-Persister: folder ${folder.id} has a local duplicate in ${result.id}, replacing content" }
                    savedSitesRepository.replaceFolderContent(folder, result.id)
                }
            }

            is NotDuplicate -> {
                if (folder.isDeleted()) {
                    logcat { "Sync-Bookmarks-Persister: folder ${folder.id} not present locally but was deleted, nothing to do" }
                } else {
                    logcat { "Sync-Bookmarks-Persister: folder ${folder.id} not present locally, inserting" }
                    savedSitesRepository.insert(folder)
                }
            }
        }
    }

    override fun processFavouritesFolder(
        favouriteFolder: String,
        children: List<String>,
    ) {
        // deduplicating favourites means adding remote favourites to local folder
        // and ensuring the folder will be sent in the next sync operation if there were local favourites
        syncSavedSitesRepository.addToFavouriteFolder(favouriteFolder, children)
    }

    override fun processBookmark(
        bookmark: Bookmark,
        folderId: String,
    ) {
        if (bookmark.isDeleted()) {
            logcat { "Sync-Bookmarks-Persister: child ${bookmark.id} is removed and not present locally, nothing to do" }
        } else {
            // if there's a bookmark duplicate locally (url and name) then we replace it
            when (val result = duplicateFinder.findBookmarkDuplicate(bookmark)) {
                is Duplicate -> {
                    logcat { "Sync-Bookmarks-Persister: child ${bookmark.id} has a local duplicate in ${result.id}, replacing" }
                    syncSavedSitesRepository.replaceBookmark(bookmark, result.id)
                }

                is NotDuplicate -> {
                    logcat { "Sync-Bookmarks-Persister: child ${bookmark.id} not present locally, inserting" }
                    savedSitesRepository.insert(bookmark)
                }
            }
        }
    }
}
