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

        Timber.i("PD: requests $newJson")
        Timber.i("PD: protections $protectionsJson")
        onPrivacyProtectionSettingChanged(viewState.userChangedValues)
        webView.evaluateJavascript("javascript:onChangeProtectionStatus($protectionsJson);", null)
        webView.evaluateJavascript("javascript:onChangeParentEntity($parentEntityJson);", null)
        webView.evaluateJavascript("javascript:onChangeCertificateData($json);", null)
        webView.evaluateJavascript("javascript:onChangeUpgradedHttps(${viewState.siteProtectionsViewState.upgradedHttps});", null)
        webView.evaluateJavascript("javascript:onChangeProtectionStatus(${viewState.userSettingsViewState.privacyProtectionEnabled});", null)
        webView.evaluateJavascript("javascript:onChangeRequestData(\"${viewState.siteProtectionsViewState.url}\", $newJson);", null)
    }

    companion object {
        val requestsSample = "{\n" +
            "  \"requests\": [\n" +
            "    {\n" +
            "      \"category\": \"Advertising\",\n" +
            "      \"url\": \"https:\\/\\/www.google.com\\/images\\/branding\\/googlelogo\\/2x\\/googlelogo_color_160x56dp.png\",\n" +
            "      \"pageUrl\": \"https:\\/\\/www.google.com\\/\",\n" +
            "      \"ownerName\": \"Google LLC\",\n" +
            "      \"entityName\": \"Google\",\n" +
            "      \"state\": {\n" +
            "        \"allowed\": {\n" +
            "          \"reason\": \"ownedByFirstParty\"\n" +
            "        }\n" +
            "      },\n" +
            "      \"prevalence\": 80.099999999999994\n" +
            "    },\n" +
            "    {\n" +
            "      \"category\": \"Advertising\",\n" +
            "      \"url\": \"https:\\/\\/apis.google.com\\/_\\/scs\\/abc-static\\/_\\/js\\/k=gapi.gapi.en.t9z7VPsEMFg.O\\/m=gapi_iframes,googleapis_client\\/rt=j\\/sv=1\\/d=1\\/ed=1\\/rs=AHpOoo8oD_5FQW3kT3ksWwmXIWvhhqbKdw\\/cb=gapi.loaded_0\",\n" +
            "      \"pageUrl\": \"https:\\/\\/www.google.com\\/\",\n" +
            "      \"ownerName\": \"Google LLC\",\n" +
            "      \"entityName\": \"Google\",\n" +
            "      \"state\": {\n" +
            "        \"allowed\": {\n" +
            "          \"reason\": \"ownedByFirstParty\"\n" +
            "        }\n" +
            "      },\n" +
            "      \"prevalence\": 80.099999999999994\n" +
            "    },\n" +
            "    {\n" +
            "      \"category\": \"Content Delivery\",\n" +
            "      \"url\": \"https:\\/\\/fonts.gstatic.com\\/s\\/i\\/productlogos\\/googleg\\/v6\\/24px.svg\",\n" +
            "      \"pageUrl\": \"https:\\/\\/www.google.com\\/\",\n" +
            "      \"ownerName\": \"Google LLC\",\n" +
            "      \"entityName\": \"Google\",\n" +
            "      \"state\": {\n" +
            "        \"allowed\": {\n" +
            "          \"reason\": \"ownedByFirstParty\"\n" +
            "        }\n" +
            "      },\n" +
            "      \"prevalence\": 80.099999999999994\n" +
            "    },\n" +
            "    {\n" +
            "      \"category\": \"Content Delivery\",\n" +
            "      \"url\": \"https:\\/\\/www.gstatic.com\\/og\\/_\\/js\\/k=og.qtm.en_US.asUsweLQqwk.O\\/rt=j\\/m=qabr,q_dnp,qcwid,qapid\\/exm=qaaw,qadd,qaid,qein,qhaw,qhbr,qhch,qhga,qhid,qhin,qhpr\\/d=1\\/ed=1\\/rs=AA2YrTvH37iHjvnJ7NPFbMaGY1OZ0tqdnw\",\n" +
            "      \"pageUrl\": \"https:\\/\\/www.google.com\\/\",\n" +
            "      \"ownerName\": \"Google LLC\",\n" +
            "      \"entityName\": \"Google\",\n" +
            "      \"state\": {\n" +
            "        \"allowed\": {\n" +
            "          \"reason\": \"ownedByFirstParty\"\n" +
            "        }\n" +
            "      },\n" +
            "      \"prevalence\": 80.099999999999994\n" +
            "    }\n" +
            "  ]\n" +
            "}"

        val protectionsSample = "{\"allowlisted\":false,\"denylisted\":false,\"enabledFeatures\":[\"contentBlocking\"],\"unprotectedTemporary\":false}"
    }
}
