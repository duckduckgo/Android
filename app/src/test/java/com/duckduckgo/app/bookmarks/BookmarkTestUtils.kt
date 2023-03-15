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

package com.duckduckgo.app.bookmarks

import com.duckduckgo.app.bookmarks.migration.AppDatabaseBookmarksMigrationCallback
import com.duckduckgo.savedsites.store.Entity
import com.duckduckgo.savedsites.store.EntityType.BOOKMARK
import com.duckduckgo.savedsites.store.EntityType.FOLDER
import com.duckduckgo.savedsites.store.Relation
import java.util.UUID

object BookmarkTestUtils {

    fun givenSomeBookmarks(
        total: Int,
    ): List<Entity> {
        val entities = mutableListOf<Entity>()
        for (index in 1..total) {
            entities.add(
                Entity(UUID.randomUUID().toString(), "entity$index", "https://testUrl$index", BOOKMARK),
            )
        }
        return entities
    }

    fun givenSomeFolders(
        total: Int,
    ): List<Entity> {
        val entities = mutableListOf<Entity>()
        for (index in 1..total) {
            entities.add(
                Entity(UUID.randomUUID().toString(), "entity$index", "https://testUrl$index", FOLDER),
            )
        }
        return entities
    }
    fun givenFolderWithContent(
        folderId: String,
        entities: List<Entity>,
    ): List<Relation> {
        val relations = mutableListOf<Relation>()
        entities.forEach {
            relations.add(Relation(folderId = folderId, entityId = it.entityId))
        }
        return relations
    }
}
