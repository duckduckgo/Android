/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.autofill.store.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "website_login_credentials")
data class WebsiteLoginCredentialsEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val domain: String?,
    val username: String?,
    val password: String?,
    val passwordIv: String?,
    val notes: String?,
    val notesIv: String?,
    val domainTitle: String?,
    val lastUpdatedInMillis: Long?,
    val lastUsedInMillis: Long?,
)
