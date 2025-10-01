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
import com.squareup.anvil.annotations.ContributesBinding
import logcat.logcat
import javax.inject.Inject
import javax.inject.Named

@ContributesBinding(AppScope::class)
@Named("remoteWinsStrategy")
class SavedSitesRemoteWinsPersister @Inject constructor(
    private val savedSitesRepository: SavedSitesRepository,
    private val syncSavedSitesRepository: SyncSavedSitesRepository,
) : SavedSitesSyncPersisterStrategy {
    override fun processBookmarkFolder(
        folder: BookmarkFolder,
        children: List<String>,
    ) {
        val localFolder = savedSitesRepository.getFolder(folder.id)
        if (localFolder != null) {
            if (folder.isDeleted()) {
                logcat { "Sync-Bookmarks-Persister: folder ${localFolder.id} exists locally but was deleted remotely, deleting locally too" }
                savedSitesRepository.delete(localFolder)
            } else {
                logcat { "Sync-Bookmarks-Persister: folder ${localFolder.id} exists locally, replacing content" }
                syncSavedSitesRepository.replaceBookmarkFolder(folder, children)
            }
        } else {
            if (folder.isDeleted()) {
                logcat { "Sync-Bookmarks-Persister: folder ${folder.id} not present locally but was deleted, nothing to do" }
            } else {
                logcat { "Sync-Bookmarks-Persister: folder ${folder.id} not present locally, inserting" }
                savedSitesRepository.insert(folder)
            }
        }
    }

    override fun processFavouritesFolder(
        favouriteFolder: String,
        children: List<String>,
    ) {
        // process favourites means replacing the current children with the remote
        syncSavedSitesRepository.replaceFavouriteFolder(favouriteFolder, children)
    }

    override fun processBookmark(
        bookmark: Bookmark,
        folderId: String,
    ) {
        val storedBookmark = savedSitesRepository.getBookmarkById(bookmark.id)
        if (storedBookmark != null) {
            if (bookmark.isDeleted()) {
                logcat { "Sync-Bookmarks-Persister: remote bookmark ${bookmark.id} exists locally but was deleted remotely, deleting locally too" }
                savedSitesRepository.delete(bookmark)
            } else {
                logcat { "Sync-Bookmarks-Persister: remote bookmark ${bookmark.id} exists locally, replacing" }
                syncSavedSitesRepository.replaceBookmark(bookmark, bookmark.id)
            }
        } else {
            if (bookmark.isDeleted()) {
                logcat { "Sync-Bookmarks-Persister: bookmark ${bookmark.id} not present locally but was deleted, nothing to do" }
            } else {
                logcat { "Sync-Bookmarks-Persister: child ${bookmark.id} not present locally, inserting" }
                savedSitesRepository.insert(bookmark)
            }
        }
    }
}
