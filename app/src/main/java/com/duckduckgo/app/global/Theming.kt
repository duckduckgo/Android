/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.them

import android.app.Activity
import com.duckduckgo.app.browser.R


fun Activity.applyTheme() {
    val theme = themeId
    setTheme(theme)
}

private val Activity.manifestThemeId: Int
    get() {
        return packageManager.getActivityInfo(componentName, 0).themeResource
    }

private val Activity.themeId: Int
    get() {
        val defaultTheme = manifestThemeId
        if (defaultTheme == R.style.AppTheme) {
            return R.style.AppTheme_Light
        }
        return defaultTheme
    }

