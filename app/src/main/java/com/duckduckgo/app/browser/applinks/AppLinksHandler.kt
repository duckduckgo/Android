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
    fun handleAppLink(isRedirect: Boolean, isForMainFrame: Boolean, urlString: String, launchAppLink: () -> Unit): Boolean
    fun handleNonHttpAppLink(isRedirect: Boolean, launchNonHttpAppLink: () -> Unit): Boolean
    fun enterBrowserState(urlString: String?)
    fun userEnteredBrowserState(url: String?)
    fun updateDisabledUrl(urlString: String?)
    fun reset()
}

class DuckDuckGoAppLinksHandler @Inject constructor() : AppLinksHandler {

    var disabledUrl: String? = null
    var appLinkOpenedInBrowser = false
    var userEnteredLink = false

    override fun handleAppLink(isRedirect: Boolean, isForMainFrame: Boolean, urlString: String, launchAppLink: () -> Unit): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N ||
            isRedirect && appLinkOpenedInBrowser ||
            !isForMainFrame ||
            UriString.sameOrSubdomain(disabledUrl ?: "", urlString)
        ) {
            return false
        }
        launchAppLink()
        return true
    }

    override fun handleNonHttpAppLink(isRedirect: Boolean, launchNonHttpAppLink: () -> Unit): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isRedirect && appLinkOpenedInBrowser && !userEnteredLink) {
            return true
        }
        launchNonHttpAppLink()
        return true
    }

    override fun enterBrowserState(urlString: String?) {
        appLinkOpenedInBrowser = true
        disabledUrl = urlString
    }

    override fun userEnteredBrowserState(url: String?) {
        userEnteredLink = true
        enterBrowserState(url)
    }

    override fun updateDisabledUrl(urlString: String?) {
        disabledUrl = urlString
    }

    override fun reset() {
        appLinkOpenedInBrowser = false
        userEnteredLink = false
    }
}
