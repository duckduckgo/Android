/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.app.privacy.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "privacy_protection_count")
data class PrivacyProtectionCountsEntity(
    @PrimaryKey val key: String = SINGLETON_KEY,

    @ColumnInfo(name = "blocked_tracker_count")
    val blockedTrackerCount: Long,

    @ColumnInfo(name = "upgrade_count")
    val upgradeCount: Long

) {
    companion object {
        const val SINGLETON_KEY = "SINGLETON_KEY"
    }
}