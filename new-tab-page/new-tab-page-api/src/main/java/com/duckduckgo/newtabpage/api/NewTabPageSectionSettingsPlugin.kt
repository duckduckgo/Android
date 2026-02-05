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

/**
 * This class is used to provide each of the Sections that build the New Tab Page
 * Implementation of https://app.asana.com/1/137249556945/project/72649045549333/task/1207111396655548?focus=true
 */

interface NewTabPageSectionSettingsPlugin {

    /** Name of the focused view version */
    val name: String

    /**
     * This method returns a [View] that will be used as the NewTabPage content
     * @return [View]
     */
    fun getView(context: Context): View?

    /**
     * This method returns a [Boolean] that represents if the plugin should be visible in New Tab Settings
     * Specially built for AppTP Setting. We don't want to show the Setting unless AppTP has ever been enabled
     * @return [View]
     */
    suspend fun isActive(): Boolean

    // Every time you want to add a setting add the priority (order) to the list below and use it in the plugin
    companion object {
        const val APP_TRACKING_PROTECTION = 100
        const val FAVOURITES = 200
        const val SHORTCUTS = 300
    }
}
