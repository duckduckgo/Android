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
import com.duckduckgo.autofill.AutofillStoredBackJavascriptInterface.UrlProvider
import com.duckduckgo.autofill.domain.app.LoginCredentials
import com.duckduckgo.autofill.jsbridge.AutofillMessagePoster
import com.duckduckgo.autofill.jsbridge.request.AutofillDataRequest
import com.duckduckgo.autofill.jsbridge.request.AutofillRequestParser
import com.duckduckgo.autofill.jsbridge.request.SupportedAutofillInputMainType.CREDENTIALS
import com.duckduckgo.autofill.jsbridge.request.SupportedAutofillInputSubType.PASSWORD
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
class AutofillStoredBackJavascriptInterfaceTest {

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
        testee = AutofillStoredBackJavascriptInterface(
            requestParser = requestParser,
            autofillStore = autofillStore,
            autofillMessagePoster = autofillMessagePoster,
            autofillResponseWriter = autofillResponseWriter,
            emailManager = emailManager,
            coroutineScope = coroutineScope,
            currentUrlProvider = currentUrlProvider,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
        )
        testee.callback = TestCallback
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

    @Test
    fun whenRequestSpecifiesSubtypeUsernameAndNoEntriesThenNoCredentialsCallbackInvoked() = runTest {
        setupRequestForSubTypeUsername()
        whenever(autofillStore.getCredentials(any())).thenReturn(emptyList())
        initiateGetAutofillDataRequest()
        assertCredentialsUnavailable()
    }

    @Test
    fun whenRequestSpecifiesSubtypeUsernameAndNoEntriesWithAUsernameThenNoCredentialsCallbackInvoked() = runTest {
        setupRequestForSubTypeUsername()
        whenever(autofillStore.getCredentials(any())).thenReturn(
            listOf(
                loginCredential(username = null, password = "foo"),
                loginCredential(username = null, password = "bar")
            )
        )
        initiateGetAutofillDataRequest()
        assertCredentialsUnavailable()
    }

    @Test
    fun whenRequestSpecifiesSubtypeUsernameAndSingleEntryWithAUsernameThenCredentialsAvailableCallbackInvoked() = runTest {
        setupRequestForSubTypeUsername()
        whenever(autofillStore.getCredentials(any())).thenReturn(
            listOf(
                loginCredential(username = null, password = "foo"),
                loginCredential(username = null, password = "bar"),
                loginCredential(username = "foo", password = "bar")
            )
        )
        initiateGetAutofillDataRequest()
        assertCredentialsAvailable()
        assertCredentialsContains({ it.username }, "foo")
    }

    @Test
    fun whenRequestSpecifiesSubtypeUsernameAndMultipleEntriesWithAUsernameThenCredentialsAvailableCallbackInvoked() = runTest {
        setupRequestForSubTypeUsername()
        whenever(autofillStore.getCredentials(any())).thenReturn(
            listOf(
                loginCredential(username = null, password = "foo"),
                loginCredential(username = "username1", password = "bar"),
                loginCredential(username = null, password = "bar"),
                loginCredential(username = null, password = "bar"),
                loginCredential(username = "username2", password = null),
            )
        )
        initiateGetAutofillDataRequest()
        assertCredentialsAvailable()
        assertCredentialsContains({ it.username }, "username1", "username2")
    }

    @Test
    fun whenRequestSpecifiesSubtypePasswordAndNoEntriesThenNoCredentialsCallbackInvoked() = runTest {
        setupRequestForSubTypePassword()
        initiateGetAutofillDataRequest()
        whenever(autofillStore.getCredentials(any())).thenReturn(emptyList())
        assertCredentialsUnavailable()
    }

    @Test
    fun whenRequestSpecifiesSubtypePasswordAndNoEntriesWithAPasswordThenNoCredentialsCallbackInvoked() = runTest {
        setupRequestForSubTypePassword()
        whenever(autofillStore.getCredentials(any())).thenReturn(
            listOf(
                loginCredential(username = "foo", password = null),
                loginCredential(username = "bar", password = null)
            )
        )
        initiateGetAutofillDataRequest()
        assertCredentialsUnavailable()
    }

    @Test
    fun whenRequestSpecifiesSubtypePasswordAndSingleEntryWithAPasswordThenCredentialsAvailableCallbackInvoked() = runTest {
        setupRequestForSubTypePassword()
        whenever(autofillStore.getCredentials(any())).thenReturn(
            listOf(
                loginCredential(username = null, password = null),
                loginCredential(username = "foobar", password = null),
                loginCredential(username = "foo", password = "bar")
            )
        )
        initiateGetAutofillDataRequest()
        assertCredentialsAvailable()
        assertCredentialsContains({ it.password }, "bar")
    }

    @Test
    fun whenRequestSpecifiesSubtypePasswordAndMultipleEntriesWithAPasswordThenCredentialsAvailableCallbackInvoked() = runTest {
        setupRequestForSubTypePassword()
        whenever(autofillStore.getCredentials(any())).thenReturn(
            listOf(
                loginCredential(username = null, password = null),
                loginCredential(username = "username2", password = null),
                loginCredential(username = "username1", password = "password1"),
                loginCredential(username = null, password = "password2"),
                loginCredential(username = null, password = "password3"),

            )
        )
        initiateGetAutofillDataRequest()
        assertCredentialsAvailable()
        assertCredentialsContains({ it.password }, "password1", "password2", "password3")
    }

    private fun assertCredentialsContains(property: (LoginCredentials) -> String?, vararg expected: String?) {
        val numberExpected = expected.size
        val numberMatched = TestCallback.credentials?.filter { expected.contains(property(it)) }?.count()
        assertEquals("Wrong number of matched properties. Expected $numberExpected but found $numberMatched", numberExpected, numberMatched)
    }

    private fun loginCredential(username: String?, password: String?) = LoginCredentials(0, "example.com", username, password)

    private suspend fun setupRequestForSubTypeUsername() {
        whenever(requestParser.parseAutofillDataRequest(any())).thenReturn(AutofillDataRequest(CREDENTIALS, USERNAME))
    }

    private suspend fun setupRequestForSubTypePassword() {
        whenever(requestParser.parseAutofillDataRequest(any())).thenReturn(AutofillDataRequest(CREDENTIALS, PASSWORD))
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

        override suspend fun onCredentialsAvailableToInject(credentials: List<LoginCredentials>) {
            credentialsAvailable = true
            this.credentials = credentials
        }

        override suspend fun onCredentialsAvailableToSave(currentUrl: String, credentials: LoginCredentials) {

        }

        override fun noCredentialsAvailable(originalUrl: String) {
            credentialsAvailable = false
        }
    }

}
