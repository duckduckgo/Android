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
import javax.inject.Inject

class AppLinksHandler @Inject constructor() {

    var appLinkOpenedInBrowser = false

    fun handleAppLink(isRedirect: Boolean, isForMainFrame: Boolean, launchAppLink: () -> Unit): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || isRedirect && appLinkOpenedInBrowser || !isForMainFrame) {
            return false
        }
        launchAppLink()
        return true
    }

    fun handleNonHttpAppLink(isRedirect: Boolean, launchAppLink: () -> Unit): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isRedirect && appLinkOpenedInBrowser) {
            return true
        }
        launchAppLink()
        return true
    }

    fun enterBrowserState() {
        appLinkOpenedInBrowser = true
    }

    fun reset() {
        appLinkOpenedInBrowser = false
    }
}
