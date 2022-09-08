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
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.EntityViewState
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.ProtectionStatusViewState
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.RequestDataViewState
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.SiteProtectionsViewState
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.ViewState
import com.squareup.moshi.Moshi
import timber.log.Timber

class PrivacyDashboardRenderer(
    private val webView: WebView,
    private val onPrivacyProtectionSettingChanged: (Boolean) -> Unit,
    private val moshi: Moshi,
    private val onBrokenSiteClicked: () -> Unit,
    private val onPrivacyProtectionsClicked: (Boolean) -> Unit,
    private val onClose: () -> Unit
) {

    fun loadDashboard(webView: WebView) {
        webView.addJavascriptInterface(
            PrivacyDashboardJavascriptInterface(
                onBrokenSiteClicked = { onBrokenSiteClicked() },
                onPrivacyProtectionsClicked = { newValue ->
                    onPrivacyProtectionsClicked(newValue)
                },
                onClose = { onClose() }
            ),
            PrivacyDashboardJavascriptInterface.JAVASCRIPT_INTERFACE_NAME
        )
        webView.loadUrl("file:///android_asset/html/popup.html")
    }

    fun render(viewState: ViewState) {
        Timber.i("PrivacyDashboard viewState $viewState")
        val adapter = moshi.adapter(SiteProtectionsViewState::class.java)

        val json = adapter.toJson(viewState.siteProtectionsViewState)

        val newAdapter = moshi.adapter(RequestDataViewState::class.java)
        val newJson = newAdapter.toJson(viewState.requestData)

        val protectionsAdapter = moshi.adapter(ProtectionStatusViewState::class.java)
        val protectionsJson = protectionsAdapter.toJson(viewState.protectionStatus)

        val adapterParententity = moshi.adapter(EntityViewState::class.java)
        val parentEntityJson = adapterParententity.toJson(viewState.siteProtectionsViewState.parentEntity)

        Timber.i("PD: $newJson")
        onPrivacyProtectionSettingChanged(viewState.userChangedValues)
        webView.evaluateJavascript("javascript:onChangeProtectionStatus($protectionsJson);", null)
        webView.evaluateJavascript("javascript:onChangeRequestData(\"${viewState.siteProtectionsViewState.url}\", $newJson);", null)

        // old calls
        /*
        webView.evaluateJavascript("javascript:onChangeParentEntity($parentEntityJson);", null)
        webView.evaluateJavascript("javascript:onChangeCertificateData($json);", null)
        webView.evaluateJavascript("javascript:onChangeUpgradedHttps(${viewState.siteProtectionsViewState.upgradedHttps});", null)
        webView.evaluateJavascript("javascript:onChangeProtectionStatus(${viewState.userSettingsViewState.privacyProtectionEnabled});", null)
        */
    }
}
