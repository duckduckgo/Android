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

package com.duckduckgo.sync.store

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.TypeConverter
import java.util.UUID

@Entity(tableName = "entities", primaryKeys = ["entityId"])
data class Entity(
    var entityId: String = UUID.randomUUID().toString(),
    var title: String,
    var url: String?,
    var type: EntityType,
) {
    companion object {
        fun generateFolderId(index: Long): String {
            return "folder$index"
        }

        fun generateFavoriteId(index: Long): String {
            return "favorite$index"
        }

        fun generateBookmarkId(index: Long): String {
            return "bookmark$index"
        }
    }
}

enum class EntityType {
    BOOKMARK,
    FOLDER,
    ;
}

class EntityTypeConverter {

    @TypeConverter
    fun toEntityType(entityType: String): EntityType {
        return try {
            EntityType.valueOf(entityType)
        } catch (ex: IllegalArgumentException) {
            EntityType.BOOKMARK
        }
    }

    @TypeConverter
    fun fromEntityType(entityType: EntityType): String {
        return entityType.name
    }
}

@Entity(tableName = "relations", primaryKeys = ["relationId", "entityId"])
data class Relation(
    var relationId: String = UUID.randomUUID().toString(),
    @Embedded var entity: com.duckduckgo.sync.store.Entity,
) {
    companion object {
        const val FAVORITES_ROOT = "favorites_root"
        const val BOOMARKS_ROOT = "bookmarks_root"
        const val BOOMARKS_ROOT_ID = 0L

        fun migrateId(folderId: Long?): String {
            return when (folderId) {
                null -> BOOMARKS_ROOT
                0L -> BOOMARKS_ROOT
                else -> "folder$folderId"
            }
        }
    }
}
