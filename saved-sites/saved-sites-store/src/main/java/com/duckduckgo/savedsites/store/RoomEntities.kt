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

package com.duckduckgo.savedsites.store

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.duckduckgo.common.utils.formatters.time.DatabaseDateFormatter
import java.util.UUID

@Entity(tableName = "entities", primaryKeys = ["entityId"])
data class Entity(
    var entityId: String = UUID.randomUUID().toString(),
    var title: String,
    var url: String?,
    var type: EntityType,
    var lastModified: String? = DatabaseDateFormatter.iso8601(),
    var deleted: Boolean = false,
)

enum class EntityType {
    BOOKMARK,
    FOLDER,
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

@Entity(tableName = "relations")
data class Relation(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    var folderId: String = UUID.randomUUID().toString(),
    var entityId: String,
)
