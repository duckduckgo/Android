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
import androidx.core.net.toUri
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.isBlank
import com.duckduckgo.common.utils.AppUrl

fun TabEntity.displayTitle(context: Context): String {
    if (isBlank) {
        return context.getString(R.string.newTabMenuItem)
    }

    return title ?: resolvedUrl().toUri().host?.take(TabSwitcherAdapter.TabSwitcherViewHolder.MAX_TITLE_LENGTH) ?: ""
}

private fun TabEntity.resolvedUrl(): String {
    return if (isBlank) AppUrl.Url.HOME else url ?: ""
}

fun TabEntity.displayUrl(): String {
    return resolvedUrl()
}
