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

import android.content.Context
import android.webkit.WebView
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.utils.domain
import com.duckduckgo.privacy.dashboard.api.ui.DashboardOpener
import com.duckduckgo.privacy.dashboard.impl.di.JsonModule
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.CookiePromptManagementState
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.DetectedRequest
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.EntityViewState
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.LayoutType
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.PrimaryScreenSettings
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.ProtectionStatusViewState
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.RemoteFeatureSettingsViewState
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.RequestDataViewState
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.RequestState.Blocked
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.SiteViewState
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.ViewState
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.WebBrokenSiteFormSettings
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.WebBrokenSiteFormState
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardJavascriptInterface.Companion.JAVASCRIPT_INTERFACE_NAME
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardRenderer.InitialScreen.BREAKAGE_FORM
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardRenderer.InitialScreen.PRIMARY
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardRenderer.InitialScreen.TOGGLE_REPORT
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.verify
import com.squareup.moshi.Moshi
import java.util.*
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.times

@RunWith(AndroidJUnit4::class)
class PrivacyDashboardRendererTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    val spyWebView = spy(WebView(context))
    val moshi = getMoshiPD()
    val testee = PrivacyDashboardRenderer(
        spyWebView,
        {},
        moshi,
        {},
        {},
        {},
        {},
        {},
        {},
        {},
        {},
        {},
        {},
        {},
    )

    @Test
    fun whenLoadDashboardThenJSInterfaceInjected() {
        testee.loadDashboard(spyWebView, initialScreen = PRIMARY, toggleOpener = DashboardOpener.DASHBOARD)

        verify(spyWebView).addJavascriptInterface(
            any<PrivacyDashboardJavascriptInterface>(),
            eq(JAVASCRIPT_INTERFACE_NAME),
        )
    }

    @Test
    fun whenLoadDashboardWithInitialScreenPrimaryThenLoadLocalHtml() {
        testee.loadDashboard(spyWebView, initialScreen = PRIMARY, toggleOpener = DashboardOpener.DASHBOARD)

        verify(spyWebView).loadUrl("file:///android_asset/html/android.html?screen=primaryScreen&opener=dashboard")
    }

    @Test
    fun whenLoadDashboardWithInitialScreenBreakageFormThenLoadLocalHtml() {
        testee.loadDashboard(spyWebView, initialScreen = BREAKAGE_FORM, toggleOpener = DashboardOpener.DASHBOARD)

        verify(spyWebView).loadUrl("file:///android_asset/html/android.html?screen=breakageForm&opener=dashboard")
    }

    @Test
    fun whenLoadDashboardWithInitialScreenToggleReportThenLoadLocalHtml() {
        testee.loadDashboard(spyWebView, initialScreen = TOGGLE_REPORT, toggleOpener = DashboardOpener.MENU)

        verify(spyWebView).loadUrl("file:///android_asset/html/android.html?screen=toggleReport&opener=menu")
    }

    @Test
    fun whenRenderStateThenJSInterface() {
        val captor = argumentCaptor<String>()

        testee.render(aViewState())

        verify(spyWebView, times(8)).evaluateJavascript(captor.capture(), eq(null))

        assertNotNull(captor.allValues.find { it.startsWith("javascript:onChangeLocale") })
        assertNotNull(captor.allValues.find { it.startsWith("javascript:onChangeFeatureSettings") })
        assertNotNull(captor.allValues.find { it.startsWith("javascript:onChangeProtectionStatus") })
        assertNotNull(captor.allValues.find { it.startsWith("javascript:onChangeParentEntity") })
        assertNotNull(captor.allValues.find { it.startsWith("javascript:onChangeCertificateData") })
        assertNotNull(captor.allValues.find { it.startsWith("javascript:onChangeUpgradedHttps") })
        assertNotNull(captor.allValues.find { it.startsWith("javascript:onChangeRequestData") })
        assertNotNull(captor.allValues.find { it.startsWith("javascript:onChangeConsentManaged") })
    }

    fun aViewState() = ViewState(
        siteViewState = SiteViewState(
            url = "http://example.com",
            domain = "http://example.com".toUri().domain()!!,
            upgradedHttps = true,
            parentEntity = EntityViewState(
                displayName = "displayName",
                prevalence = 12.0,
            ),
            secCertificateViewModels = emptyList(),
            locale = Locale.getDefault().language,
        ),
        userChangedValues = false,
        requestData = RequestDataViewState(
            emptyList(),
            listOf(
                DetectedRequest(
                    category = "Analytics",
                    eTLDplus1 = "example.com",
                    entityName = "Entity Name",
                    ownerName = "Owner name",
                    pageUrl = "test.com",
                    prevalence = 10.0,
                    state = Blocked(),
                    url = "tracker.com",
                ),
            ),
        ),
        protectionStatus = ProtectionStatusViewState(true, true, emptyList(), true),
        cookiePromptManagementStatus = CookiePromptManagementState(),
        remoteFeatureSettings = RemoteFeatureSettingsViewState(
            primaryScreen = PrimaryScreenSettings(layout = LayoutType.DEFAULT.value),
            webBreakageForm = WebBrokenSiteFormSettings(state = WebBrokenSiteFormState.DISABLED.value),
        ),
    )

    private fun getMoshiPD(): Moshi = JsonModule.moshi(Moshi.Builder().build())
}
