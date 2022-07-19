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

package com.duckduckgo.privacy.dashboard.impl.ui

import android.webkit.WebView
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.privacy.dashboard.impl.ui.RendererViewHolder.WebviewRenderer
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.Moshi
import dagger.SingleInstanceIn
import javax.inject.Inject

interface PrivacyDashboardRendererFactory {
    fun createRenderer(renderer: RendererViewHolder): PrivacyDashboardRenderer
}

sealed class RendererViewHolder {
    data class WebviewRenderer(
        val holder: WebView,
        val onPrivacyProtectionSettingChanged: (Boolean) -> Unit
    ) : RendererViewHolder()
}

@ContributesBinding(ActivityScope::class)
@SingleInstanceIn(ActivityScope::class)
class BrowserPrivacyDashboardRendererFactory @Inject constructor(
    val moshi: Moshi
) : PrivacyDashboardRendererFactory {

    override fun createRenderer(renderer: RendererViewHolder): PrivacyDashboardRenderer {
        return when (renderer) {
            is WebviewRenderer -> PrivacyDashboardRenderer(renderer.holder, renderer.onPrivacyProtectionSettingChanged, moshi)
        }
    }
}
