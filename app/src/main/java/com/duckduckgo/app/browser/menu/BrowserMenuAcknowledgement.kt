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

package com.duckduckgo.app.browser.menu

import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface BrowserMenuAcknowledgement {
    /**
     * Signals that the browser menu has been shown to the user in [mode]. Any highlight that exists only to flag an
     * unopened menu should clear.
     */
    fun onBrowserMenuViewed(mode: BrowserViewMode)
}

@ContributesBinding(AppScope::class)
class RealBrowserMenuAcknowledgement @Inject constructor(
    private val downloadMenuHighlightPlugin: DownloadMenuHighlightPlugin,
) : BrowserMenuAcknowledgement {

    override fun onBrowserMenuViewed(mode: BrowserViewMode) {
        // a custom tab has no Downloads row, so acknowledging there would clear a dot the user never saw
        if (mode == BrowserViewMode.CustomTab) return
        downloadMenuHighlightPlugin.onDownloadMenuHighlightAcknowledged()
    }
}
