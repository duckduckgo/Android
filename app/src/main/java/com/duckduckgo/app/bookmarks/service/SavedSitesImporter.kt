package com.duckduckgo.app.bookmarks.service

import android.content.ContentResolver
import android.net.Uri
import com.duckduckgo.app.bookmarks.model.SavedSite
import com.duckduckgo.app.bookmarks.model.SavedSitesRepository
import com.duckduckgo.sync.store.Entity
import com.duckduckgo.sync.store.EntityType.BOOKMARK
import com.duckduckgo.sync.store.Relation
import org.jsoup.Jsoup

/*
 * Copyright (c) 2021 DuckDuckGo
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

interface SavedSitesImporter {
    suspend fun import(uri: Uri): ImportSavedSitesResult
}

sealed class ImportSavedSitesResult {
    data class Success(val savedSites: List<SavedSite>) : ImportSavedSitesResult()
    data class Error(val exception: Exception) : ImportSavedSitesResult()
}

class RealSavedSitesImporter(
    private val contentResolver: ContentResolver,
    private val syncEntitiesDao: SyncEntitiesDao,
    private val syncRelationsDao: SyncRelationsDao,
    private val savedSitesRepository: SavedSitesRepository,
    private val savedSitesParser: SavedSitesParser,
) : SavedSitesImporter {

    companion object {
        private const val BASE_URI = "duckduckgo.com"
        private const val IMPORT_BATCH_SIZE = 200
    }

    override suspend fun import(uri: Uri): ImportSavedSitesResult {
        return try {
            val savedSites = contentResolver.openInputStream(uri).use { stream ->
                val document = Jsoup.parse(stream, Charsets.UTF_8.name(), BASE_URI)
                savedSitesParser.parseHtml(document, savedSitesRepository)
            }

            savedSites.filterIsInstance<SavedSite.Bookmark>().map { bookmark ->
                Pair(
                    Relation(relationId = bookmark.parentId, entityId = bookmark.id),
                    Entity(bookmark.id, title = bookmark.title, url = bookmark.url, type = BOOKMARK),
                )
            }.also { pairs ->
                pairs.asSequence().chunked(IMPORT_BATCH_SIZE).forEach { chunk ->
                    syncRelationsDao.insertList(chunk.map { it.first })
                    syncEntitiesDao.insertList(chunk.map { it.second })
                }
            }

            savedSites.filterIsInstance<SavedSite.Favorite>().filter { it.url.isNotEmpty() }.map { favorite ->
                Pair(
                    Relation(relationId = Relation.FAVORITES_ROOT, entityId = favorite.id),
                    Entity(favorite.id, title = favorite.title, url = favorite.url, type = BOOKMARK),
                )
            }.also { pairs ->
                pairs.asSequence().chunked(IMPORT_BATCH_SIZE).forEach { chunk ->
                    syncRelationsDao.insertList(chunk.map { it.first })
                    syncEntitiesDao.insertList(chunk.map { it.second })
                }
            }

            ImportSavedSitesResult.Success(savedSites)
        } catch (exception: Exception) {
            ImportSavedSitesResult.Error(exception)
        }
    }
}
