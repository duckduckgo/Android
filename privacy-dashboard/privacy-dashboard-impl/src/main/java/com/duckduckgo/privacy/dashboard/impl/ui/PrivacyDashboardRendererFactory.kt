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
import javax.inject.Inject
import javax.inject.Named

interface PrivacyDashboardRendererFactory {
    fun createRenderer(renderer: RendererViewHolder): PrivacyDashboardRenderer
}

sealed class RendererViewHolder {
    data class WebviewRenderer(
        val holder: WebView,
        val onPrivacyProtectionSettingChanged: (Boolean) -> Unit,
        val onPrivacyProtectionsClicked: (String) -> Unit,
        val onUrlClicked: (String) -> Unit,
        val onOpenSettings: (String) -> Unit,
        val onClose: () -> Unit,
        val onSubmitBrokenSiteReport: (String) -> Unit,
        val onGetToggleReportOptions: () -> Unit,
        val onSendToggleReport: () -> Unit,
        val onRejectToggleReport: () -> Unit,
        val onSeeWhatIsSent: () -> Unit,
        val onShowNativeFeedback: () -> Unit,
        val onReportBrokenSiteShown: () -> Unit,
    ) : RendererViewHolder()
}

@ContributesBinding(ActivityScope::class)
class BrowserPrivacyDashboardRendererFactory @Inject constructor(
    @Named("privacyDashboard") val moshi: Moshi,
) : PrivacyDashboardRendererFactory {

    override fun createRenderer(renderer: RendererViewHolder): PrivacyDashboardRenderer {
        return when (renderer) {
            is WebviewRenderer -> PrivacyDashboardRenderer(
                renderer.holder,
                renderer.onPrivacyProtectionSettingChanged,
                moshi,
                renderer.onPrivacyProtectionsClicked,
                renderer.onUrlClicked,
                renderer.onOpenSettings,
                renderer.onClose,
                renderer.onSubmitBrokenSiteReport,
                renderer.onGetToggleReportOptions,
                renderer.onSendToggleReport,
                renderer.onRejectToggleReport,
                renderer.onSeeWhatIsSent,
                renderer.onShowNativeFeedback,
                renderer.onReportBrokenSiteShown,
            )
        }
    }
}
