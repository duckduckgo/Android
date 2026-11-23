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

package com.duckduckgo.pagetranslation.api

import android.content.Context
import android.view.View
import android.webkit.WebView
import androidx.annotation.MainThread
import kotlinx.coroutines.flow.StateFlow

/**
 * Page translation for one tab. One instance per tab, injected in the tab's fragment scope.
 *
 * The module owns the offer, the translation, the menu row, the snackbars and the settings.
 * The host only places the banner, draws [progress] and reports the WebView and the tab state.
 */
interface PageTranslation {

    /**
     * The translation progress to draw in the address bar.
     */
    val progress: StateFlow<TranslationProgress>

    /**
     * Returns a new banner view on each call. The view decides its own visibility, the host only places it.
     * It shows nothing until [attach] is called.
     *
     * @param context the context of the tab's fragment.
     */
    @MainThread
    fun getBannerView(context: Context): View

    /**
     * Call before [webView] loads its first page, because it registers the JavaScript interface.
     * Attaching another WebView ends the translation session of the previous one.
     *
     * @param webView the tab's WebView.
     */
    @MainThread
    fun attach(webView: WebView)

    /**
     * Call when [webView] is dropped. Ends its translation session and sets [progress] back to [TranslationProgress.Idle].
     * Does nothing for a WebView that isn't attached, so it is safe to call more than once.
     *
     * @param webView the WebView being dropped.
     */
    @MainThread
    fun detach(webView: WebView)

    /**
     * Call whenever the tab is shown or hidden, resumed or paused, selected or not, and when what it shows changes.
     * State reported before [attach] is remembered.
     *
     * @param isVisible true only when this tab is the one on screen.
     * @param content what the tab actually shows, not the address bar layout.
     */
    @MainThread
    fun onTabStateChanged(isVisible: Boolean, content: TabContent)
}
