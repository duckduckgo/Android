/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.app.browser.tabs.transition

import androidx.viewpager2.widget.ViewPager2

/**
 * Swiping between tabs is on only when the browser allows it and no new tab transition is running.
 */
class TabPagerSwipeGate(private val tabPager: ViewPager2) {

    var allowedByBrowser = true
        set(value) {
            field = value
            updateUserInput()
        }

    var pausedByTransition = false
        set(value) {
            field = value
            updateUserInput()
        }

    private fun updateUserInput() {
        tabPager.isUserInputEnabled = allowedByBrowser && !pausedByTransition
    }
}
