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

package com.duckduckgo.app.email

import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.browser.DuckDuckGoUrlDetector
import com.duckduckgo.feature.toggles.api.FeatureToggle
import com.duckduckgo.privacy.config.api.Autofill
import com.duckduckgo.privacy.config.api.PrivacyFeatureName.AutofillFeatureName
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class EmailJavascriptInterfaceTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val mockEmailManager: EmailManager = mock()
    private val mockWebView: WebView = mock()
    private val mockFeatureToggle: FeatureToggle = mock()
    private val mockAutofill: Autofill = mock()
    lateinit var testee: EmailJavascriptInterface
    private var counter = 0

    @Before
    fun setup() {
        testee = EmailJavascriptInterface(
            mockEmailManager,
            mockWebView,
            DuckDuckGoUrlDetector(),
            coroutineRule.testDispatcherProvider,
            mockFeatureToggle,
            mockAutofill
        ) { counter++ }

        whenever(mockFeatureToggle.isFeatureEnabled(AutofillFeatureName)).thenReturn(true)
        whenever(mockAutofill.isAnException(any())).thenReturn(false)
    }

    @Test
    fun whenIsSignedInAndUrlIsDuckDuckGoEmailThenIsSignedInCalled() {
        whenever(mockWebView.url).thenReturn(DUCKDUCKGO_EMAIL_URL)

        testee.isSignedIn()

        verify(mockEmailManager).isSignedIn()
    }

    @Test
    fun whenIsSignedInAndUrlIsNotDuckDuckGoEmailThenIsSignedInNotCalled() {
        whenever(mockWebView.url).thenReturn(NON_EMAIL_URL)

        testee.isSignedIn()

        verify(mockEmailManager, never()).isSignedIn()
    }

    @Test
    fun whenStoreCredentialsAndUrlIsDuckDuckGoEmailThenStoreCredentialsCalledWithCorrectParameters() {
        whenever(mockWebView.url).thenReturn(DUCKDUCKGO_EMAIL_URL)

        testee.storeCredentials("token", "username", "cohort")

        verify(mockEmailManager).storeCredentials("token", "username", "cohort")
    }

    @Test
    fun whenStoreCredentialsAndUrlIsNotDuckDuckGoEmailThenStoreCredentialsNotCalled() {
        whenever(mockWebView.url).thenReturn(NON_EMAIL_URL)

        testee.storeCredentials("token", "username", "cohort")

        verify(mockEmailManager, never()).storeCredentials("token", "username", "cohort")
    }

    @Test
    fun whenGetUserDataAndUrlIsDuckDuckGoEmailThenGetUserDataCalled() {
        whenever(mockWebView.url).thenReturn(DUCKDUCKGO_EMAIL_URL)

        testee.getUserData()

        verify(mockEmailManager).getUserData()
    }

    @Test
    fun whenGetUserDataAndUrlIsNotDuckDuckGoEmailThenGetUserDataIsNotCalled() {
        whenever(mockWebView.url).thenReturn(NON_EMAIL_URL)

        testee.getUserData()

        verify(mockEmailManager, never()).getUserData()
    }

    @Test
    fun whenShowTooltipThenLambdaCalled() {
        whenever(mockWebView.url).thenReturn(NON_EMAIL_URL)

        testee.showTooltip()

        assertEquals(1, counter)
    }

    @Test
    fun whenShowTooltipAndFeatureDisabledThenLambdaNotCalled() {
        whenever(mockWebView.url).thenReturn(NON_EMAIL_URL)
        whenever(mockFeatureToggle.isFeatureEnabled(AutofillFeatureName)).thenReturn(false)

        testee.showTooltip()

        assertEquals(0, counter)
    }

    @Test
    fun whenShowTooltipAndUrlIsAnExceptionThenLambdaNotCalled() {
        whenever(mockWebView.url).thenReturn(NON_EMAIL_URL)
        whenever(mockAutofill.isAnException(any())).thenReturn(true)

        testee.showTooltip()

        assertEquals(0, counter)
    }

    companion object {
        const val DUCKDUCKGO_EMAIL_URL = "https://duckduckgo.com/email"
        const val NON_EMAIL_URL = "https://example.com"
    }
}
