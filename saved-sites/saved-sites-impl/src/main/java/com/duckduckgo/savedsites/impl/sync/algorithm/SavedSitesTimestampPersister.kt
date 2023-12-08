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
import com.duckduckgo.savedsites.api.models.SavedSite
import com.duckduckgo.savedsites.api.models.SavedSite.Bookmark
import com.duckduckgo.savedsites.api.models.SavedSite.Favorite
import com.duckduckgo.savedsites.impl.sync.SyncSavedSitesRepository
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import javax.inject.Named
import org.threeten.bp.OffsetDateTime
import timber.log.Timber

@ContributesBinding(AppScope::class)
@Named("timestampStrategy")
class SavedSitesTimestampPersister @Inject constructor(
    private val savedSitesRepository: SavedSitesRepository,
    private val syncSavedSitesRepository: SyncSavedSitesRepository,
) : SavedSitesSyncPersisterStrategy {
    override fun processBookmarkFolder(
        folder: BookmarkFolder,
        children: List<String>,
    ) {
        // if there's a folder with the same id locally we check the conflict resolution
        // if TIMESTAMP -> new timestamp wins
        val localFolder = savedSitesRepository.getFolder(folder.id)
        if (localFolder != null) {
            if (folder.isDeleted()) {
                Timber.d("Sync-Bookmarks-Persister: remote folder ${folder.id} exists locally but was deleted, deleting locally")
                savedSitesRepository.delete(localFolder)
            } else {
                if (localFolder.modifiedAfter(folder.lastModified)) {
                    Timber.d("Sync-Bookmarks-Persister: folder ${folder.id} modified before local folder, nothing to do")
                } else {
                    Timber.d("Sync-Bookmarks-Persister: folder ${folder.id} modified after local folder, replacing content")
                    syncSavedSitesRepository.replaceFolder(folder, children)
                }
            }
        } else {
            if (folder.isDeleted()) {
                Timber.d("Sync-Bookmarks-Persister: folder ${folder.id} not present locally but was deleted, nothing to do")
            } else {
                Timber.d("Sync-Bookmarks-Persister: folder ${folder.id} not present locally, inserting")
                savedSitesRepository.insert(folder)
            }
        }
    }

    override fun processBookmark(
        bookmark: Bookmark,
        folderId: String,
    ) {
        // if there's a bookmark with the same id locally we check the conflict resolution
        // if TIMESTAMP -> new timestamp wins
        val storedBookmark = savedSitesRepository.getBookmarkById(bookmark.id)
        if (storedBookmark != null) {
            if (bookmark.isDeleted()) {
                Timber.d("Sync-Bookmarks-Persister: remote bookmark ${bookmark.id} deleted, deleting local bookmark")
                savedSitesRepository.delete(storedBookmark)
            } else {
                if (storedBookmark.modifiedAfter(bookmark.lastModified)) {
                    Timber.d("Sync-Bookmarks-Persister: bookmark ${bookmark.id} modified before local bookmark, nothing to do")
                } else {
                    Timber.d("Sync-Bookmarks-Persister: bookmark ${bookmark.id} modified after local bookmark, replacing content")
                    savedSitesRepository.replaceBookmark(bookmark, bookmark.id)
                }
            }
        } else {
            if (bookmark.isDeleted()) {
                Timber.d("Sync-Bookmarks-Persister: bookmark ${bookmark.id} not present locally but was deleted, nothing to do")
            } else {
                Timber.d("Sync-Bookmarks-Persister: bookmark ${bookmark.id} not present locally, inserting")
                savedSitesRepository.insert(bookmark)
            }
        }
    }

    override fun processFavourite(
        favourite: Favorite,
        favoriteFolder: String,
    ) {
        val storedFavourite = syncSavedSitesRepository.getFavoriteById(favourite.id, favoriteFolder)
        if (storedFavourite != null) {
            if (favourite.isDeleted()) {
                Timber.d("Sync-Bookmarks-Persister: remote favourite ${favourite.id} deleted, deleting local favourite")
                syncSavedSitesRepository.delete(favourite, favoriteFolder)
            } else {
                if (storedFavourite.modifiedAfter(favourite.lastModified)) {
                    Timber.d("Sync-Bookmarks-Persister: favourite ${favourite.id} modified before local favourite, nothing to do")
                } else {
                    Timber.d("Sync-Bookmarks-Persister: favourite ${favourite.id} modified after local favourite, replacing content")
                    syncSavedSitesRepository.replaceFavourite(favourite, favourite.id, favoriteFolder)
                }
            }
        } else {
            if (favourite.isDeleted()) {
                Timber.d("Sync-Bookmarks-Persister: favourite ${favourite.id} not present locally but was deleted, nothing to do")
            } else {
                Timber.d("Sync-Bookmarks-Persister: favourite ${favourite.id} not present locally, inserting")
                syncSavedSitesRepository.insert(favourite, favoriteFolder)
            }
        }
    }
}

fun BookmarkFolder.modifiedAfter(after: String?): Boolean {
    return if (this.lastModified == null) {
        true
    } else {
        if (after == null) {
            false
        } else {
            val entityModified = OffsetDateTime.parse(this.lastModified)
            val sinceModified = OffsetDateTime.parse(after)
            entityModified.isAfter(sinceModified)
        }
    }
}

fun BookmarkFolder.isDeleted(): Boolean {
    return this.deleted != null
}

fun SavedSite.modifiedAfter(after: String?): Boolean {
    return if (this.lastModified == null) {
        true
    } else {
        if (after == null) {
            false
        } else {
            val entityModified = OffsetDateTime.parse(this.lastModified)
            val sinceModified = OffsetDateTime.parse(after)
            entityModified.isAfter(sinceModified)
        }
    }
}

fun SavedSite.isDeleted(): Boolean {
    return this.deleted != null
}
