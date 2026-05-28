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

package com.duckduckgo.app.tabs.ui

import android.content.Context
import androidx.core.net.toUri
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.isAboutBlank
import com.duckduckgo.app.tabs.model.isBlank
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.common.utils.AppUrl
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface TabTitleResolver {
    fun resolveTitle(entity: TabEntity, browserMode: BrowserMode): String
}

@ContributesBinding(AppScope::class)
class RealTabTitleResolver @Inject constructor(
    private val context: Context,
) : TabTitleResolver {

    override fun resolveTitle(entity: TabEntity, browserMode: BrowserMode): String {
        if (entity.isAboutBlank) {
            return "about:blank"
        }
        if (entity.isBlank) {
            val res = when (browserMode) {
                BrowserMode.REGULAR -> R.string.newTabMenuItem
                BrowserMode.FIRE -> R.string.fireTabMenuItem
            }
            return context.getString(res)
        }
        val raw = entity.title ?: entity.resolvedUrl().toUri().host?.take(MAX_TITLE_LENGTH) ?: ""
        return raw.removeSuffix(DUCKDUCKGO_TITLE_SUFFIX)
    }

    private fun TabEntity.resolvedUrl(): String {
        return if (isBlank) AppUrl.Url.HOME else url ?: ""
    }

    private companion object {
        const val MAX_TITLE_LENGTH = 50
        const val DUCKDUCKGO_TITLE_SUFFIX = "at DuckDuckGo"
    }
}
