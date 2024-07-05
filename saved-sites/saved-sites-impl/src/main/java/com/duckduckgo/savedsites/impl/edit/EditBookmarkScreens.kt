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

package com.duckduckgo.savedsites.impl.edit

import com.duckduckgo.navigation.api.GlobalActivityStarter.ActivityParams

/**
 * Launch the Bookmark editing activity, which will allow the user to edit, delete and update location of a bookmark
 */
sealed interface EditBookmarkScreens {

    /**
     * Launch the Autofill management activity, which will show suggestions for the current url and the full list of available credentials
     * @param currentUrl The current URL the user is viewing. This is used to show suggestions for the current site if available.
     * @param source is used to indicate from where in the app Autofill management activity was launched
     */
    data class EditBookmarkScreen(
        val bookmarkId: String,
    ) : ActivityParams
}
