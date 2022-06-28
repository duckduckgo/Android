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

package com.duckduckgo.autofill

import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.email.EmailManager
import com.duckduckgo.autofill.AutofillJavascriptInterface.UrlProvider
import com.duckduckgo.autofill.domain.app.LoginCredentials
import com.duckduckgo.autofill.jsbridge.AutofillMessagePoster
import com.duckduckgo.autofill.jsbridge.request.AutofillDataRequest
import com.duckduckgo.autofill.jsbridge.request.AutofillRequestParser
import com.duckduckgo.autofill.jsbridge.request.SupportedAutofillInputMainType.CREDENTIALS
import com.duckduckgo.autofill.jsbridge.request.SupportedAutofillInputSubType.USERNAME
import com.duckduckgo.autofill.jsbridge.response.AutofillResponseWriter
import com.duckduckgo.autofill.store.AutofillStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class AutofillJavascriptInterfaceTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val requestParser: AutofillRequestParser = mock()
    private val autofillStore: AutofillStore = mock()
    private val autofillMessagePoster: AutofillMessagePoster = mock()
    private val autofillResponseWriter: AutofillResponseWriter = mock()
    private val emailManager: EmailManager = mock()
    private val currentUrlProvider: UrlProvider = mock()
    private val coroutineScope: CoroutineScope = TestScope()

    private val testWebView = WebView(getApplicationContext())

    private lateinit var testee: AutofillJavascriptInterface

    @Before
    fun setup() = runTest {
        testee = AutofillJavascriptInterface(
            requestParser = requestParser,
            autofillStore = autofillStore,
            autofillMessagePoster = autofillMessagePoster,
            autofillResponseWriter = autofillResponseWriter,
            emailManager = emailManager,
            coroutineScope = coroutineScope,
            currentUrlProvider = currentUrlProvider,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            callback = TestCallback
        )
        testee.webView = testWebView

        whenever(currentUrlProvider.currentUrl(testWebView)).thenReturn("https://example.com")
        whenever(requestParser.parseAutofillDataRequest(any())).thenReturn(AutofillDataRequest(CREDENTIALS, USERNAME))
    }

    @Test
    fun whenInjectingNoCredentialResponseThenCorrectJsonWriterInvoked() = runTest {
        testee.injectNoCredentials()
        verify(autofillResponseWriter).generateEmptyResponseGetAutofillData()
        verifyMessageSent()
    }

    @Test
    fun whenInjectingCredentialResponseThenCorrectJsonWriterInvoked() = runTest {
        val loginCredentials = LoginCredentials(0, "example.com", "username", "password")
        testee.injectCredentials(loginCredentials)
        verify(autofillResponseWriter).generateResponseGetAutofillData(any())
        verifyMessageSent()
    }

    @Test
    fun whenGetAutofillDataCalledNoCredentialsAvailableThenNoCredentialsCallbackInvoked() = runTest {
        whenever(autofillStore.getCredentials(any())).thenReturn(emptyList())
        initiateGetAutofillDataRequest()
        assertCredentialsUnavailable()
    }

    @Test
    fun whenGetAutofillDataCalledWithCredentialsAvailableThenCredentialsAvailableCallbackInvoked() = runTest {
        whenever(autofillStore.getCredentials(any())).thenReturn(listOf(LoginCredentials(0, "example.com", "username", "password")))
        initiateGetAutofillDataRequest()
        assertCredentialsAvailable()
    }

    private fun assertCredentialsUnavailable() {
        assertNotNull("Callback has not been called", TestCallback.credentialsAvailable)
        assertFalse(TestCallback.credentialsAvailable!!)
    }

    private fun assertCredentialsAvailable() {
        assertNotNull("Callback has not been called", TestCallback.credentialsAvailable)
        assertTrue(TestCallback.credentialsAvailable!!)
    }

    private fun initiateGetAutofillDataRequest() {
        testee.getAutofillData("")
    }

    private suspend fun verifyMessageSent() {
        verify(autofillMessagePoster).postMessage(any(), anyOrNull())
    }

    object TestCallback : Callback {

        var credentials: List<LoginCredentials>? = null
        var credentialsAvailable: Boolean? = null

        override fun onCredentialsAvailableToInject(credentials: List<LoginCredentials>) {
            credentialsAvailable = true
            this.credentials = credentials
        }

        override fun onCredentialsAvailableToSave(currentUrl: String, credentials: LoginCredentials) {

        }

        override fun noCredentialsAvailable(originalUrl: String) {
            credentialsAvailable = false
        }
    }

}
