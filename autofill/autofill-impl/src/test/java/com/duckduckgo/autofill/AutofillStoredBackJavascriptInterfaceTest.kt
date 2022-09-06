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
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.autofill.AutofillStoredBackJavascriptInterface.UrlProvider
import com.duckduckgo.autofill.domain.app.LoginCredentials
import com.duckduckgo.autofill.domain.app.LoginTriggerType
import com.duckduckgo.autofill.jsbridge.AutofillMessagePoster
import com.duckduckgo.autofill.jsbridge.request.AutofillDataRequest
import com.duckduckgo.autofill.jsbridge.request.AutofillRequestParser
import com.duckduckgo.autofill.jsbridge.request.AutofillStoreFormDataCredentialsRequest
import com.duckduckgo.autofill.jsbridge.request.AutofillStoreFormDataRequest
import com.duckduckgo.autofill.jsbridge.request.SupportedAutofillInputMainType.CREDENTIALS
import com.duckduckgo.autofill.jsbridge.request.SupportedAutofillInputSubType.PASSWORD
import com.duckduckgo.autofill.jsbridge.request.SupportedAutofillInputSubType.USERNAME
import com.duckduckgo.autofill.jsbridge.request.SupportedAutofillTriggerType.USER_INITIATED
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

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class AutofillStoredBackJavascriptInterfaceTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val requestParser: AutofillRequestParser = mock()
    private val autofillStore: AutofillStore = mock()
    private val autofillMessagePoster: AutofillMessagePoster = mock()
    private val autofillResponseWriter: AutofillResponseWriter = mock()
    private val currentUrlProvider: UrlProvider = mock()
    private val autofillDomainFormatter: AutofillDomainFormatter = AutofillDomainFormatterDomainNameOnly()
    private val coroutineScope: CoroutineScope = TestScope()

    private val testWebView = WebView(getApplicationContext())
    private lateinit var testee: AutofillStoredBackJavascriptInterface

    private val testCallback = TestCallback()

    @Before
    fun setUp() = runTest {
        testee = AutofillStoredBackJavascriptInterface(
            requestParser = requestParser,
            autofillStore = autofillStore,
            autofillMessagePoster = autofillMessagePoster,
            autofillResponseWriter = autofillResponseWriter,
            coroutineScope = coroutineScope,
            currentUrlProvider = currentUrlProvider,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            autofillDomainFormatter = autofillDomainFormatter
        )
        testee.callback = testCallback
        testee.webView = testWebView

        whenever(currentUrlProvider.currentUrl(testWebView)).thenReturn("https://example.com")
        whenever(requestParser.parseAutofillDataRequest(any())).thenReturn(AutofillDataRequest(CREDENTIALS, USERNAME, USER_INITIATED))
        whenever(autofillResponseWriter.generateEmptyResponseGetAutofillData()).thenReturn("")
        whenever(autofillResponseWriter.generateResponseGetAutofillData(any())).thenReturn("")
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
        whenever(autofillStore.getCredentials(any())).thenReturn(emptyList())
        initiateGetAutofillDataRequest()
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

    @Test
    fun whenStoreFormDataCalledWithNoUsernameThenCallbackInvoked() = runTest {
        configureRequestParserToReturn(username = null, password = "password")
        testee.storeFormData("")
        assertNotNull(testCallback.credentialsToSave)
    }

    @Test
    fun whenStoreFormDataCalledWithNoPasswordThenCallbackInvoked() = runTest {
        configureRequestParserToReturn(username = "dax@duck.com", password = null)
        testee.storeFormData("")
        assertNotNull(testCallback.credentialsToSave)
        assertEquals("dax@duck.com", testCallback.credentialsToSave!!.username)
    }

    @Test
    fun whenStoreFormDataCalledWithNullUsernameAndPasswordThenCallbackNotInvoked() = runTest {
        configureRequestParserToReturn(username = null, password = null)
        testee.storeFormData("")
        assertNull(testCallback.credentialsToSave)
    }

    @Test
    fun whenStoreFormDataCalledWithBlankUsernameThenCallbackInvoked() = runTest {
        configureRequestParserToReturn(username = " ", password = "password")
        testee.storeFormData("")
        assertEquals(" ", testCallback.credentialsToSave!!.username)
        assertEquals("password", testCallback.credentialsToSave!!.password)
    }

    @Test
    fun whenStoreFormDataCalledWithBlankPasswordThenCallbackInvoked() = runTest {
        configureRequestParserToReturn(username = "username", password = " ")
        testee.storeFormData("")
        assertEquals("username", testCallback.credentialsToSave!!.username)
        assertEquals(" ", testCallback.credentialsToSave!!.password)
    }

    @Test
    fun whenStoreFormDataCalledWithBlankUsernameAndBlankPasswordThenCallbackNotInvoked() = runTest {
        configureRequestParserToReturn(username = " ", password = " ")
        testee.storeFormData("")
        assertNull(testCallback.credentialsToSave)
    }

    private suspend fun configureRequestParserToReturn(username: String?, password: String?) {
        val credentials = AutofillStoreFormDataCredentialsRequest(username = username, password = password)
        val topLevelRequest = AutofillStoreFormDataRequest(credentials)
        whenever(requestParser.parseStoreFormDataRequest(any())).thenReturn(topLevelRequest)
    }

    private fun assertCredentialsContains(
        property: (LoginCredentials) -> String?,
        vararg expected: String?
    ) {
        val numberExpected = expected.size
        val numberMatched = testCallback.credentialsToInject?.filter { expected.contains(property(it)) }?.count()
        assertEquals("Wrong number of matched properties. Expected $numberExpected but found $numberMatched", numberExpected, numberMatched)
    }

    private fun loginCredential(
        username: String?,
        password: String?
    ) = LoginCredentials(0, "example.com", username, password)

    private suspend fun setupRequestForSubTypeUsername() {
        whenever(requestParser.parseAutofillDataRequest(any())).thenReturn(AutofillDataRequest(CREDENTIALS, USERNAME, USER_INITIATED))
    }

    private suspend fun setupRequestForSubTypePassword() {
        whenever(requestParser.parseAutofillDataRequest(any())).thenReturn(AutofillDataRequest(CREDENTIALS, PASSWORD, USER_INITIATED))
    }

    private fun assertCredentialsUnavailable() {
        assertNotNull("Callback has not been called", testCallback.credentialsAvailableToInject)
        assertFalse(testCallback.credentialsAvailableToInject!!)
    }

    private fun assertCredentialsAvailable() {
        assertNotNull("Callback has not been called", testCallback.credentialsAvailableToInject)
        assertTrue(testCallback.credentialsAvailableToInject!!)
    }

    private fun initiateGetAutofillDataRequest() {
        testee.getAutofillData("")
    }

    private suspend fun verifyMessageSent() {
        verify(autofillMessagePoster).postMessage(any(), anyOrNull())
    }

    class TestCallback : Callback {

        // for injection
        var credentialsToInject: List<LoginCredentials>? = null
        var credentialsAvailableToInject: Boolean? = null

        // for saving
        var credentialsToSave: LoginCredentials? = null

        override suspend fun onCredentialsAvailableToInject(credentials: List<LoginCredentials>, triggerType: LoginTriggerType) {
            credentialsAvailableToInject = true
            this.credentialsToInject = credentials
        }

        override suspend fun onCredentialsAvailableToSave(
            currentUrl: String,
            credentials: LoginCredentials
        ) {
            credentialsToSave = credentials
        }

        override fun noCredentialsAvailable(originalUrl: String) {
            credentialsAvailableToInject = false
        }
    }
}
