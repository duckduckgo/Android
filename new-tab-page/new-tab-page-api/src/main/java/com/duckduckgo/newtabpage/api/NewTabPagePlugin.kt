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
 * This class is used to provide one of the two different version of NewTabPage
 * Legacy -> What existed before https://app.asana.com/0/1174433894299346/1207064372575037
 * New -> Implementation of https://app.asana.com/0/1174433894299346/1207064372575037
 */
interface NewTabPagePlugin : ActivePlugin {

    /**
     * This method returns a [View] that will be used as the NewTabPage content
     * @param context The context to create the view with
     * @param showLogo Whether to show the logo in the new tab page
     * @param onHasContent Optional callback to notify when the view has content
     * @return [View]
     */
    fun getView(
        context: Context,
        showLogo: Boolean = true,
        onHasContent: ((Boolean) -> Unit)? = null,
    ): View

    companion object {
        const val PRIORITY_LEGACY_NTP = 0
        const val PRIORITY_NTP = 100
    }
}
