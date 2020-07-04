/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.tabs.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tabs",
    indices = [
        Index("tabId")
    ]
)
data class TabEntity(
    @PrimaryKey var tabId: String,
    var url: String? = null,
    var title: String? = null,
    var skipHome: Boolean = false,
    var viewed: Boolean = true,
    var position: Int,
    var tabPreviewFile: String? = null,
    var sourceTabId: String? = null
)

val TabEntity.isBlank: Boolean
    get() = title == null && url == null
