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

package com.duckduckgo.sync.impl.ui

/**
 * The entry point that opened Sync. Launch sources reach [com.duckduckgo.sync.impl.ui.dashboard.SyncActivity] as
 * free-form strings, so we map every entry point that isn't a promotion (Sync settings, deep links, no source)
 * to [Other].
 */
enum class SyncLaunchSource(val value: String) {
    PromotionBookmarks("promotion_bookmarks"),
    PromotionPasswords("promotion_passwords"),
    PromotionBookmarkAddedDialog("promotion_bookmark_added_dialog"),
    PromotionAiChat("promotion_ai_chat"),
    PromotionPasswordsImport("promotion_passwords_import"),
    PromotionBookmarksImport("promotion_bookmarks_import"),
    Other("other"),
    ;

    companion object {
        fun from(value: String?): SyncLaunchSource = entries.firstOrNull { it.value == value } ?: Other
    }
}
