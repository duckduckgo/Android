/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.robots

import com.duckduckgo.actions.enterTextFunction
import com.duckduckgo.actions.tapOnButton
import com.duckduckgo.app.browser.R

inline fun bookmarkScreenRobot(block: BookmarkScreenRobot.() -> Unit) = BookmarkScreenRobot.block()

object BookmarkScreenRobot {

    fun openWebsite (website: String) {
        enterTextFunction(R.id.omnibarTextInputMockup, website)
    }

    fun tapMenuButton () {
        tapOnButton(R.id.browserMenuMockup)
    }

    fun tapAddBookmarkButton () {
        tapOnButton(R.id.addBookmarksMenuItem)
    }
}
