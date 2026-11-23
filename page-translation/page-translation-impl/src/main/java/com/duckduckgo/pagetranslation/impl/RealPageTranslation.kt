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

package com.duckduckgo.pagetranslation.impl

import android.content.Context
import android.view.View
import android.webkit.WebView
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.pagetranslation.api.PageTranslation
import com.duckduckgo.pagetranslation.api.TabContent
import com.duckduckgo.pagetranslation.api.TranslationProgress
import dev.zacsweers.metro.ContributesBinding
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@ContributesBinding(FragmentScope::class)
class RealPageTranslation @Inject constructor() : PageTranslation {

    override val progress: StateFlow<TranslationProgress> = MutableStateFlow(TranslationProgress.Idle)

    override fun getBannerView(context: Context): View = View(context).apply { visibility = View.GONE }

    override fun attach(webView: WebView) = Unit

    override fun detach(webView: WebView) = Unit

    override fun onTabStateChanged(isVisible: Boolean, content: TabContent) = Unit
}
