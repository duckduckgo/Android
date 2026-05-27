/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.app.browser.session

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.duckduckgo.app.tabs.model.TabEntity

@Entity(
    tableName = "webview_sessions",
    foreignKeys = [
        ForeignKey(
            entity = TabEntity::class,
            parentColumns = ["tabId"],
            childColumns = ["tabId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class WebViewSessionEntity(
    @PrimaryKey val tabId: String,
    val sessionBundle: ByteArray,
    val savedAt: Long,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WebViewSessionEntity) return false
        return tabId == other.tabId &&
            sessionBundle.contentEquals(other.sessionBundle) &&
            savedAt == other.savedAt
    }

    override fun hashCode(): Int {
        var result = tabId.hashCode()
        result = 31 * result + sessionBundle.contentHashCode()
        result = 31 * result + savedAt.hashCode()
        return result
    }
}
