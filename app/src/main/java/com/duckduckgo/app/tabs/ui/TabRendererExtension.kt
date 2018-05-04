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

package com.duckduckgo.app.tabs.ui

import android.content.Context
import android.net.Uri
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.AppUrl
import com.duckduckgo.app.global.faviconLocation
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.isBlank


fun TabEntity.displayTitle(context: Context): String {
    if (isBlank) {
        return context.getString(R.string.homeTab)
    }

    return title ?: Uri.parse(resolvedUrl()).host ?: ""
}

private fun TabEntity.resolvedUrl(): String {
    return if (isBlank) AppUrl.Url.HOME else url ?: ""
}

fun TabEntity.displayUrl(): String {
    return resolvedUrl()
}

fun TabEntity.favicon(): Uri? {
    return Uri.parse(resolvedUrl())?.faviconLocation()
}