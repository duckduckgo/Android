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
import com.duckduckgo.app.global.domain
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.ProtectionStatusViewState
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.RequestDataViewState
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.SiteViewState
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.UserSettingsViewState
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.ViewState
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardJavascriptInterface.Companion.JAVASCRIPT_INTERFACE_NAME
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.verify
import com.squareup.moshi.Moshi
import org.junit.Assert.assertNotNull
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.times
import java.util.*

@RunWith(AndroidJUnit4::class)
@Ignore
class PrivacyDashboardRendererTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    val spyWebView = spy(WebView(context))
    val moshi = Moshi.Builder().build()
    val testee = PrivacyDashboardRenderer(
        spyWebView,
        {},
        moshi,
        {},
        {},
        {},
        {}
    )

    @Test
    fun whenLoadDashboardThenJSInterfaceInjected() {
        testee.loadDashboard(spyWebView)

        verify(spyWebView).addJavascriptInterface(
            any<PrivacyDashboardJavascriptInterface>(),
            eq(JAVASCRIPT_INTERFACE_NAME)
        )
    }

    @Test
    fun whenLoadDashboardThenLoadLocalHtml() {
        testee.loadDashboard(spyWebView)

        verify(spyWebView).loadUrl("file:///android_asset/html/popup.html")
    }

    @Test
    fun whenRenderStateThenJSInterface() {
        val captor = argumentCaptor<String>()

        testee.render(aViewState())

        verify(spyWebView, times(5)).evaluateJavascript(captor.capture(), eq(null))

        assertNotNull(captor.allValues.find { it.startsWith("javascript:onChangeParentEntity") })
        assertNotNull(captor.allValues.find { it.startsWith("javascript:onChangeCertificateData") })
        assertNotNull(captor.allValues.find { it.startsWith("javascript:onChangeTrackerBlockingData") })
        assertNotNull(captor.allValues.find { it.startsWith("javascript:onChangeUpgradedHttps") })
        assertNotNull(captor.allValues.find { it.startsWith("javascript:onChangeProtectionStatus") })
    }

    fun aViewState() = ViewState(
        userSettingsViewState = UserSettingsViewState(privacyProtectionEnabled = true),
        siteViewState = SiteViewState(
            url = "http://example.com",
            upgradedHttps = true,
            site = SiteViewState(
                url = "http://example.com",
                domain = "http://example.com".toUri().domain()!!,
                emptySet(),
                false
            ),
            parentEntity = null,
            trackers = emptyMap(),
            trackerBlocked = emptyMap(),
            secCertificateViewModels = emptyList(),
            locale = Locale.getDefault().language
        ),
        userChangedValues = false,
        requestData = RequestDataViewState(emptyList(), emptyList()), // TODO
        protectionStatus = ProtectionStatusViewState(true, true, emptyList(), true)
    )
}
