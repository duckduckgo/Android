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
import com.duckduckgo.savedsites.impl.sync.SyncBookmarkEntry
import com.squareup.anvil.annotations.ContributesBinding
import org.threeten.bp.OffsetDateTime
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named

@ContributesBinding(AppScope::class)
@Named("timestampStrategy")
class SavedSitesTimestampPersister @Inject constructor(
    private val savedSitesRepository: SavedSitesRepository
) : SavedSitesSyncPersisterStrategy {
    override fun processBookmarkFolder(
        folder: BookmarkFolder,
        parentId: String,
        lastModified: String,
    ) {
        // if there's a folder with the same id locally we check the conflict resolution
        // if TIMESTAMP -> new timestamp wins
        val localFolder = savedSitesRepository.getFolder(folder.id)
        if (localFolder != null) {
            if (folder.modifiedAfter(localFolder.lastModified)) {
                if (folder.deleted != null) {
                    Timber.d("Sync-Feature: folder ${folder.id} deleted after local folder")
                    savedSitesRepository.delete(localFolder)
                } else {
                    Timber.d("Sync-Feature: folder ${folder.id} modified after local folder, replacing content")
                    savedSitesRepository.replaceFolderContent(folder, folder.id)
                }
            } else {
                Timber.d("Sync-Feature: folder ${folder.id} modified before local folder, nothing to do")
            }
        } else {
            if (folder.deleted != null) {
                Timber.d("Sync-Feature: folder ${folder.id} not present locally but was deleted, nothing to do")
            } else {
                Timber.d("Sync-Feature: folder ${folder.id} not present locally, inserting")
                savedSitesRepository.insert(folder)
            }
        }
    }

    override fun processBookmark(
        bookmark: Bookmark,
        bookmarkId: String,
        folderId: String,
        lastModified: String
    ) {
        // if there's a bookmark with the same id locally we check the conflict resolution
        // if TIMESTAMP -> new timestamp wins
        val storedBookmark = savedSitesRepository.getBookmarkById(bookmarkId)
        if (storedBookmark != null) {
            if (bookmark.modifiedAfter(storedBookmark.lastModified)) {
                Timber.d("Sync-Feature: bookmark ${bookmark.id} modified after local bookmark, replacing content")
                savedSitesRepository.replaceBookmark(bookmark, bookmarkId)
            } else {
                Timber.d("Sync-Feature: bookmark ${bookmark.id} modified before local bookmark, nothing to do")
            }
        } else {
            Timber.d("Sync-Feature: child $bookmarkId not present locally, inserting")
            savedSitesRepository.insert(bookmark)
        }

    }

    override fun processFavourite(
        favourite: Favorite,
        lastModified: String
    ) {
        Timber.d("Sync-Feature: adding ${favourite.id} to Favourites")
        savedSitesRepository.insert(favourite)
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
