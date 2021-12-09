/*
 * Copyright (c) 2020 DuckDuckGo
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
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.browser.DuckDuckGoUrlDetector
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class EmailJavascriptInterfaceTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val mockEmailManager: EmailManager = mock()
    private val mockWebView: WebView = mock()
    lateinit var testee: EmailJavascriptInterface
    private var counter = 0

    @Before
    fun setup() {
        testee = EmailJavascriptInterface(mockEmailManager, mockWebView, DuckDuckGoUrlDetector(), coroutineRule.testDispatcherProvider) { counter++ }
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
    fun whenShowTooltipThenLambdaCalled() {
        testee.showTooltip()

        assertEquals(1, counter)
    }

    companion object {
        const val DUCKDUCKGO_EMAIL_URL = "https://duckduckgo.com/email"
        const val NON_EMAIL_URL = "https://example.com"
    }
}
