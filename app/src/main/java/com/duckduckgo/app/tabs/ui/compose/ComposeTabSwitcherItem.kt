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

package com.duckduckgo.app.tabs.ui.compose

import android.graphics.Bitmap
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.common.ui.compose.component.tabs.SelectionStatus
import java.io.File

sealed class ComposeTabSwitcherItem(
    open val id: String,
    open val isCurrentTab: Boolean,
    open val selectionStatus: SelectionStatus?,
) {

    data class NewTab(
        override val isCurrentTab: Boolean,
        override val selectionStatus: SelectionStatus?,
    ) :
        ComposeTabSwitcherItem("NewTab", isCurrentTab, selectionStatus)

    data class WebTab(
        val tabEntity: TabEntity,
        val faviconBitmap: Bitmap?,
        val isUnreadIndicatorVisible: Boolean,
        val tabPreviewFilePath: File?,
        val title: String,
        override val isCurrentTab: Boolean,
        override val selectionStatus: SelectionStatus?,
    ) : ComposeTabSwitcherItem(tabEntity.tabId, isCurrentTab, selectionStatus)
}
