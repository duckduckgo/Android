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

package com.duckduckgo.newtabpage.api

import android.content.Context
import android.view.View
import com.duckduckgo.common.utils.plugins.ActivePlugin

/**
 * This class is used to provide each of the Sections that build the New Tab Page
 * Implementation of https://app.asana.com/1/137249556945/project/72649045549333/task/1207111396655548?focus=true
 */
interface NewTabPageSectionPlugin : ActivePlugin {

    /** Name of the focused view version */
    val name: String

    /**
     * This method returns a [View] that will be used as the NewTabPage content
     * @return [View]
     */
    fun getView(context: Context): View?

    /**
     * This method returns a [Boolean] that shows if the plugin is enabled manually by the user
     * @return [Boolean]
     */
    suspend fun isUserEnabled(): Boolean

    companion object {
        const val PRIORITY_INDONESIA_MESSAGE = 5
        const val PRIORITY_REMOTE_MESSAGE = 10
        const val PRIORITY_APP_TP = 20
        const val PRIORITY_FAVOURITES = 33
        const val PRIORITY_SHORTCUTS = 40
    }
}

enum class NewTabPageSection {
    INDONESIA_MESSAGE,
    APP_TRACKING_PROTECTION,
    REMOTE_MESSAGING_FRAMEWORK,
    FAVOURITES,
    SHORTCUTS,
}
