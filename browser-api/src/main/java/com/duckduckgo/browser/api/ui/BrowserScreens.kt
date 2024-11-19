/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.browser.api.ui

import com.duckduckgo.navigation.api.GlobalActivityStarter

/**
 * Model that represents the Browser Screens hosted inside :app module that can be launched from other modules.
 */
sealed class BrowserScreens {
    object FeedbackActivityWithEmptyParams : GlobalActivityStarter.ActivityParams
    data class WebViewActivityWithParams(
        val url: String,
        val screenTitle: String,
    ) : GlobalActivityStarter.ActivityParams

    /**
     * Use this model to launch the Bookmarks screen
     */
    object BookmarksScreenNoParams : GlobalActivityStarter.ActivityParams

    /**
     * Use this model to launch the Settings screen
     */
    object SettingsScreenNoParams : GlobalActivityStarter.ActivityParams

    /**
     * Use this model to launch the New Settings screen
     */
    object NewSettingsScreenNoParams : GlobalActivityStarter.ActivityParams

    /**
     * Use this model to launch the New Tab Settings screen
     */
    object NewTabSettingsScreenNoParams : GlobalActivityStarter.ActivityParams
}
