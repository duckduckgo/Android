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

import com.duckduckgo.savedsites.api.models.*
import com.duckduckgo.savedsites.store.*
import java.time.*

fun Entity.mapToBookmark(relationId: String): SavedSite.Bookmark =
    SavedSite.Bookmark(this.entityId, this.title, this.url.orEmpty(), relationId, this.lastModified, deleted = this.deletedFlag())

fun Entity.mapToSavedSite(): SavedSite =
    SavedSite.Bookmark(
        id = this.entityId,
        title = this.title,
        url = this.url.orEmpty(),
        lastModified = this.lastModified,
        deleted = this.deletedFlag(),
    )

fun Entity.mapToFavorite(index: Int = 0): SavedSite.Favorite =
    SavedSite.Favorite(
        id = this.entityId,
        title = this.title,
        url = this.url.orEmpty(),
        lastModified = this.lastModified,
        position = index,
        deleted = this.deletedFlag(),
    )

fun Entity.modifiedSince(since: String): Boolean {
    return if (this.lastModified == null) {
        false
    } else {
        val entityModified = OffsetDateTime.parse(this.lastModified)
        val sinceModified = OffsetDateTime.parse(since)
        entityModified.isAfter(sinceModified)
    }
}

fun Entity.deletedFlag(): String? {
    return if (this.deleted) {
        this.lastModified
    } else {
        null
    }
}

fun List<Entity>.mapToFavorites(): List<SavedSite.Favorite> = this.mapIndexed { index, relation -> relation.mapToFavorite(index) }

fun SavedSite.titleOrFallback(): String = this.title.takeIf { it.isNotEmpty() } ?: this.url
