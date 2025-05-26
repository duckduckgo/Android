/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.autofill.store.targets

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "autofill_domain_target_apps_mapping")
data class DomainTargetAppEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val domain: String,
    @Embedded val targetApp: TargetApp,
    val dataExpiryInMillis: Long, // If from our dataset this will be 0L, else if part of user's cache, then a value greater than 0L.
)

data class TargetApp(
    @ColumnInfo(name = "app_package") val packageName: String,
    @ColumnInfo(name = "app_fingerprint") val sha256CertFingerprints: String,
)
