/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.app.browser.applinks

import android.os.Build
import com.duckduckgo.app.global.UriString
import javax.inject.Inject

interface AppLinksHandler {
    fun handleAppLink(isForMainFrame: Boolean, urlString: String, appLinksEnabled: Boolean, shouldOverride: Boolean, launchAppLink: () -> Unit): Boolean
    fun updatePreviousUrl(urlString: String?)
    fun setUserQueryState(state: Boolean)
    fun isUserQuery(): Boolean
}

class DuckDuckGoAppLinksHandler @Inject constructor() : AppLinksHandler {

    var previousUrl: String? = null
    var userQuery = false

    override fun handleAppLink(isForMainFrame: Boolean, urlString: String, appLinksEnabled: Boolean, shouldOverride: Boolean, launchAppLink: () -> Unit): Boolean {

        if (!appLinksEnabled) {
            return false
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N ||
            !isForMainFrame ||
            previousUrl != null && UriString.sameOrSubdomain(previousUrl!!, urlString) ||
            previousUrl != null && UriString.sameOrSubdomain(urlString, previousUrl!!)
        ) {
            if (userQuery) {
                previousUrl = urlString
                launchAppLink()
            }
            return false
        }
        previousUrl = urlString
        launchAppLink()
        return shouldOverride
    }

    override fun updatePreviousUrl(urlString: String?) {
        previousUrl = urlString
    }

    override fun setUserQueryState(state: Boolean) {
        userQuery = state
    }

    override fun isUserQuery(): Boolean {
        return userQuery
    }
}
