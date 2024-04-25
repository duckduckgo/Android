/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.autofill.impl.ui.credential.selecting

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autofill.api.AutofillEventListener
import com.duckduckgo.autofill.api.AutofillWebMessageRequest
import com.duckduckgo.autofill.api.CredentialAutofillPickerDialog
import com.duckduckgo.autofill.api.ExistingCredentialMatchDetector
import com.duckduckgo.autofill.api.ExistingCredentialMatchDetector.ContainsCredentialsResult.NoMatch
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.deviceauth.FakeAuthenticator
import com.duckduckgo.autofill.impl.jsbridge.AutofillMessagePoster
import com.duckduckgo.autofill.impl.jsbridge.response.AutofillResponseWriter
import com.duckduckgo.autofill.impl.store.InternalAutofillStore
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*

@RunWith(AndroidJUnit4::class)
class ResultHandlerCredentialSelectionTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val pixel: Pixel = mock()
    private val existingCredentialMatchDetector: ExistingCredentialMatchDetector = mock()
    private val callback: AutofillEventListener = mock()
    private lateinit var deviceAuthenticator: FakeAuthenticator
    private lateinit var testee: ResultHandlerCredentialSelection
    private val autofillStore: InternalAutofillStore = mock()
    private val messagePoster: AutofillMessagePoster = mock()
    private val responseWriter: AutofillResponseWriter = mock()

    @Before
    fun setup() = runTest {
        whenever(
            existingCredentialMatchDetector.determine(
                any(),
                any(),
                any(),
            ),
        ).thenReturn(NoMatch)
    }

    @Test
    fun whenUserRejectedToUseCredentialThenCorrectResponsePosted() = runTest {
        configureSuccessfulAuth()
        val bundle = bundleForUserCancelling("example.com")
        testee.processResult(bundle, context, "tab-id-123", Fragment(), callback)

        verify(responseWriter).generateEmptyResponseGetAutofillData()
        verify(messagePoster).postMessage(anyOrNull(), any())
    }

    @Test
    fun whenUserAcceptedToUseCredentialsAndSuccessfullyAuthenticatedThenCorrectResponsePosted() = runTest {
        configureSuccessfulAuth()
        val bundle = bundleForUserAcceptingToAutofill("example.com")
        testee.processResult(bundle, context, "tab-id-123", Fragment(), callback)

        verify(responseWriter).generateResponseGetAutofillData(any())
        verify(messagePoster).postMessage(anyOrNull(), any())
    }

    @Test
    fun whenUserAcceptedToUseCredentialsAndCancelsAuthenticationThenCorrectResponsePosted() = runTest {
        configureCancelledAuth()
        val bundle = bundleForUserAcceptingToAutofill("example.com")
        testee.processResult(bundle, context, "tab-id-123", Fragment(), callback)

        verify(responseWriter).generateEmptyResponseGetAutofillData()
        verify(messagePoster).postMessage(anyOrNull(), any())
    }

    @Test
    fun whenUserAcceptedToUseCredentialsAndAuthenticationFailsThenCorrectResponsePosted() = runTest {
        configureFailedAuth()
        val bundle = bundleForUserAcceptingToAutofill("example.com")
        testee.processResult(bundle, context, "tab-id-123", Fragment(), callback)

        verify(responseWriter).generateEmptyResponseGetAutofillData()
        verify(messagePoster).postMessage(anyOrNull(), any())
    }

    @Test
    fun whenUserAcceptedToUseCredentialsButMissingInBundleThenNoCallbackInvoked() = runTest {
        configureSuccessfulAuth()
        val bundle = bundleMissingCredentials("example.com")
        testee.processResult(bundle, context, "tab-id-123", Fragment(), callback)
        verifyNoInteractions(messagePoster)
    }

    @Test
    fun whenMissingUrlThenNoCallbackInvoked() = runTest {
        configureSuccessfulAuth()
        val bundle = bundleMissingUrl()
        testee.processResult(bundle, context, "tab-id-123", Fragment(), callback)
        verifyNoInteractions(callback)
    }

    private fun bundleForUserCancelling(url: String): Bundle {
        return Bundle().also {
            it.putParcelable(CredentialAutofillPickerDialog.KEY_URL_REQUEST, url.asUrlRequest())
            it.putBoolean(CredentialAutofillPickerDialog.KEY_CANCELLED, true)
        }
    }

    private fun bundleForUserAcceptingToAutofill(url: String): Bundle {
        return Bundle().also {
            it.putParcelable(CredentialAutofillPickerDialog.KEY_URL_REQUEST, url.asUrlRequest())
            it.putBoolean(CredentialAutofillPickerDialog.KEY_CANCELLED, false)
            it.putParcelable(CredentialAutofillPickerDialog.KEY_CREDENTIALS, aLogin())
        }
    }

    private fun bundleMissingUrl(): Bundle = Bundle()
    private fun bundleMissingCredentials(url: String): Bundle {
        return Bundle().also {
            it.putParcelable(CredentialAutofillPickerDialog.KEY_URL_REQUEST, url.asUrlRequest())
        }
    }

    private fun aLogin(): LoginCredentials {
        return LoginCredentials(domain = "example.com", username = "foo", password = "bar")
    }

    private fun configureSuccessfulAuth() {
        deviceAuthenticator = FakeAuthenticator.AuthorizeEverything()
        instantiateClassUnderTest(deviceAuthenticator)
    }

    private fun configureFailedAuth() {
        deviceAuthenticator = FakeAuthenticator.FailEverything()
        instantiateClassUnderTest(deviceAuthenticator)
    }

    private fun configureCancelledAuth() {
        deviceAuthenticator = FakeAuthenticator.CancelEverything()
        instantiateClassUnderTest(deviceAuthenticator)
    }

    private fun instantiateClassUnderTest(deviceAuthenticator: FakeAuthenticator) {
        testee = ResultHandlerCredentialSelection(
            dispatchers = coroutineTestRule.testDispatcherProvider,
            appCoroutineScope = coroutineTestRule.testScope,
            pixel = pixel,
            deviceAuthenticator = deviceAuthenticator,
            autofillStore = autofillStore,
            messagePoster = messagePoster,
            autofillResponseWriter = responseWriter,
        )
    }

    private fun String.asUrlRequest(): AutofillWebMessageRequest {
        return AutofillWebMessageRequest(this, this, "request-id-123")
    }
}
