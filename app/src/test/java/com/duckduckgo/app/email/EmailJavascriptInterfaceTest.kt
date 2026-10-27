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

import android.annotation.SuppressLint
import android.webkit.WebBackForwardList
import android.webkit.WebHistoryItem
import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.browser.DuckDuckGoUrlDetectorImpl
import com.duckduckgo.autofill.api.Autofill
import com.duckduckgo.autofill.api.AutofillFeature
import com.duckduckgo.autofill.api.email.EmailManager
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class EmailJavascriptInterfaceTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val mockEmailManager: EmailManager = mock()
    private val mockWebView: WebView = mock()
    private lateinit var autofillFeature: AutofillFeature
    private val mockAutofill: Autofill = mock()
    private val originCheckFeature = FakeFeatureToggleFactory.create(EmailBridgeOriginCheckFeature::class.java)
    lateinit var testee: EmailJavascriptInterface
    private var counter = 0

    @SuppressLint("DenyListedApi")
    @Before
    fun setup() {
        autofillFeature = com.duckduckgo.autofill.api.FakeAutofillFeature.create()

        testee = EmailJavascriptInterface(
            mockEmailManager,
            mockWebView,
            DuckDuckGoUrlDetectorImpl(),
            coroutineRule.testDispatcherProvider,
            autofillFeature,
            mockAutofill,
            originCheckFeature,
        ) { counter++ }

        autofillFeature.self().setRawStoredState(Toggle.State(enable = true))
        whenever(mockAutofill.isAnException(any())).thenReturn(false)
    }

    @Test
    fun whenIsSignedInAndUrlIsDuckDuckGoEmailThenIsSignedInCalled() {
        givenUrl(DUCKDUCKGO_EMAIL_URL)

        testee.isSignedIn()

        verify(mockEmailManager).isSignedIn()
    }

    @Test
    fun whenIsSignedInAndUrlIsNotDuckDuckGoEmailThenIsSignedInNotCalled() {
        givenUrl(NON_EMAIL_URL)

        testee.isSignedIn()

        verify(mockEmailManager, never()).isSignedIn()
    }

    @Test
    fun whenStoreCredentialsAndUrlIsDuckDuckGoEmailThenStoreCredentialsCalledWithCorrectParameters() {
        givenUrl(DUCKDUCKGO_EMAIL_URL)

        testee.storeCredentials("token", "username", "cohort")

        verify(mockEmailManager).storeCredentials("token", "username", "cohort")
    }

    @Test
    fun whenStoreCredentialsAndUrlIsNotDuckDuckGoEmailThenStoreCredentialsNotCalled() {
        givenUrl(NON_EMAIL_URL)

        testee.storeCredentials("token", "username", "cohort")

        verify(mockEmailManager, never()).storeCredentials("token", "username", "cohort")
    }

    @Test
    fun whenGetUserDataAndUrlIsDuckDuckGoEmailThenGetUserDataCalled() {
        givenUrl(DUCKDUCKGO_EMAIL_URL)

        testee.getUserData()

        verify(mockEmailManager).getUserData()
    }

    @Test
    fun whenGetUserDataAndUrlIsNotDuckDuckGoEmailThenGetUserDataIsNotCalled() {
        givenUrl(NON_EMAIL_URL)

        testee.getUserData()

        verify(mockEmailManager, never()).getUserData()
    }

    @Test
    fun whenShowTooltipThenLambdaCalled() {
        givenUrl(NON_EMAIL_URL)

        testee.showTooltip()

        assertEquals(1, counter)
    }

    @SuppressLint("DenyListedApi")
    @Test
    fun whenShowTooltipAndFeatureDisabledThenLambdaNotCalled() {
        givenUrl(NON_EMAIL_URL)
        autofillFeature.self().setRawStoredState(Toggle.State(enable = false))

        testee.showTooltip()

        assertEquals(0, counter)
    }

    @Test
    fun whenShowTooltipAndUrlIsAnExceptionThenLambdaNotCalled() {
        givenUrl(NON_EMAIL_URL)
        whenever(mockAutofill.isAnException(any())).thenReturn(true)

        testee.showTooltip()

        assertEquals(0, counter)
    }

    @Test
    fun whenGetDeviceCapabilitiesAndUrlIsDuckDuckGoEmailThenReturnNonEmptyString() {
        givenUrl(DUCKDUCKGO_EMAIL_URL)

        assert(testee.getDeviceCapabilities().isNotBlank())
    }

    @Test
    fun whenGetDeviceCapabilitiesAndUrlIsNotDuckDuckGoEmailThenReturnEmptyString() {
        givenUrl(NON_EMAIL_URL)

        assert(testee.getDeviceCapabilities().isBlank())
    }

    @Test
    fun whenGetUserDataAndCommittedPageIsNotDuckDuckGoEmailThenGetUserDataNotCalled() {
        givenUrl(DUCKDUCKGO_EMAIL_URL, committedUrl = NON_EMAIL_URL)

        testee.getUserData()

        verify(mockEmailManager, never()).getUserData()
    }

    @Test
    fun whenStoreCredentialsAndCommittedPageIsNotDuckDuckGoEmailThenStoreCredentialsNotCalled() {
        givenUrl(DUCKDUCKGO_EMAIL_URL, committedUrl = NON_EMAIL_URL)

        testee.storeCredentials("token", "username", "cohort")

        verify(mockEmailManager, never()).storeCredentials("token", "username", "cohort")
    }

    @Test
    fun whenRemoveCredentialsAndCommittedPageIsNotDuckDuckGoEmailThenSignOutNotCalled() {
        givenUrl(DUCKDUCKGO_EMAIL_URL, committedUrl = NON_EMAIL_URL)

        testee.removeCredentials()

        verify(mockEmailManager, never()).signOut()
    }

    @Test
    fun whenHistoryReadFailsThenSensitiveReadsReturnEmpty() {
        whenever(mockWebView.url).thenReturn(DUCKDUCKGO_EMAIL_URL)
        whenever(mockWebView.copyBackForwardList()).thenThrow(NullPointerException())

        assertEquals("", testee.isSignedIn())
        assertEquals("", testee.getUserData())
        assertEquals("", testee.getDeviceCapabilities())
        verifyNoInteractions(mockEmailManager)
    }

    @Test
    fun whenHistoryReadFailsThenCredentialWritesAreDenied() {
        whenever(mockWebView.url).thenReturn(DUCKDUCKGO_EMAIL_URL)
        whenever(mockWebView.copyBackForwardList()).thenThrow(NullPointerException())

        testee.storeCredentials("token", "username", "cohort")
        testee.removeCredentials()

        verifyNoInteractions(mockEmailManager)
    }

    @SuppressLint("DenyListedApi")
    @Test
    fun whenOriginCheckDisabledAndCommittedPageIsNotDuckDuckGoEmailThenGetUserDataCalled() {
        originCheckFeature.self().setRawStoredState(Toggle.State(enable = false))
        givenUrl(DUCKDUCKGO_EMAIL_URL, committedUrl = NON_EMAIL_URL)

        testee.getUserData()

        verify(mockEmailManager).getUserData()
    }

    private fun givenUrl(url: String?, committedUrl: String? = url) {
        val currentItem: WebHistoryItem = mock()
        whenever(currentItem.url).thenReturn(committedUrl)
        val backForwardList: WebBackForwardList = mock()
        whenever(backForwardList.currentItem).thenReturn(currentItem)
        whenever(mockWebView.url).thenReturn(url)
        whenever(mockWebView.copyBackForwardList()).thenReturn(backForwardList)
    }

    companion object {
        const val DUCKDUCKGO_EMAIL_URL = "https://duckduckgo.com/email"
        const val NON_EMAIL_URL = "https://example.com"
    }
}
