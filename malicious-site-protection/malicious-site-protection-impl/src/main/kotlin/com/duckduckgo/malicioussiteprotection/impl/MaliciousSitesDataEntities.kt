/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.malicioussiteprotection.impl

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "revisions")
data class RevisionEntity(
    @PrimaryKey
    val phishingHashPrefixesRevision: Int,
    val malwareHashPrefixesRevision: Int,
    val phishingFiltersRevision: Int,
    val malwareFiltersRevision: Int,
)

@Entity(
    tableName = "hash_prefixes",
)
data class HashPrefixEntity(
    @PrimaryKey
    val hashPrefix: String,
    val type: String,
)

@Entity(
    tableName = "filters",
)
data class FilterEntity(
    @PrimaryKey
    val hash: String,
    val regex: String,
    val type: String,
)

data class DataWithFilters(
    val revision: RevisionEntity,
    val phishingFilters: List<FilterEntity>,
    val malwareFilters: List<FilterEntity>,
    val phishingHashPrefixes: List<HashPrefixEntity>,
    val malwareHashPrefixes: List<HashPrefixEntity>,
)
