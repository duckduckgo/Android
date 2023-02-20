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

package com.duckduckgo.app.bookmarks.model

import com.duckduckgo.sync.store.Entity
import com.duckduckgo.sync.store.Relation

data class BookmarkFolderBranch(
    val bookmarkEntities: List<Entity>,
    val bookmarkFolderEntities: List<Entity>,
)

data class EntityFolderBranch(
    val bookmarks: List<Entity>,
    val folder: List<Entity>,
    val relations: List<Relation>,
)
