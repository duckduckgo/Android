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

package com.duckduckgo.app.bookmarks

import android.view.View

interface BookmarkAddedDialogPlugin {

    /**
     * Returns a view to be displayed in the bookmark added confirmation dialog, or null if the promotion should not be shown.
     * @return Some promotions may require criteria to be met before they are shown. If the criteria is not met, this method should return null.
     */
    suspend fun getView(): View?

    companion object {
        const val PRIORITY_KEY_SETUP_SYNC = 100
    }
}
