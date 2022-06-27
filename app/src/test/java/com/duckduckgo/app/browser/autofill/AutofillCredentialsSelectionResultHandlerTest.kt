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

package com.duckduckgo.app.browser.autofill

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.duckduckgo.app.browser.BrowserTabFragment
import com.duckduckgo.app.browser.autofill.AutofillCredentialsSelectionResultHandler.AutofillCredentialSaver
import com.duckduckgo.app.browser.autofill.AutofillCredentialsSelectionResultHandler.CredentialInjector
import com.duckduckgo.app.browser.autofill.AutofillCredentialsSelectionResultHandlerTest.FakeAuthenticator.AuthorizeEverything
import com.duckduckgo.app.browser.autofill.AutofillCredentialsSelectionResultHandlerTest.FakeAuthenticator.DenyEverything
import com.duckduckgo.autofill.CredentialAutofillPickerDialog
import com.duckduckgo.autofill.CredentialSavePickerDialog
import com.duckduckgo.autofill.domain.app.LoginCredentials
import com.duckduckgo.deviceauth.api.DeviceAuthenticator
import com.duckduckgo.deviceauth.api.DeviceAuthenticator.AuthResult.Failed
import com.duckduckgo.deviceauth.api.DeviceAuthenticator.AuthResult.Success
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class AutofillCredentialsSelectionResultHandlerTest {

    private val credentialsSaver: AutofillCredentialSaver = mock()
    private val credentialsInjector: CredentialInjector = mock()
    private val dummyFragment = Fragment()
    private lateinit var deviceAuthenticator: FakeAuthenticator
    private lateinit var testee: AutofillCredentialsSelectionResultHandler

    @Before
    fun setup() {
        setupAuthenticatorAlwaysAuth()
    }

    @Test
    fun whenSaveBundleMissingUrlThenNoAttemptToSaveMade() = runTest {
        val bundle = bundleForSaveDialog(url = null, credentials = someLoginCredentials())
        testee.processSaveCredentialsResult(bundle, credentialsSaver)
        verifySaveNeverCalled()
    }

    @Test
    fun whenSaveBundleMissingCredentialsThenNoAttemptToSaveMade() = runTest {
        val bundle = bundleForSaveDialog(url = "example.com", credentials = null)
        testee.processSaveCredentialsResult(bundle, credentialsSaver)
        verifySaveNeverCalled()
    }

    @Test
    fun whenSaveBundleWellFormedThenCredentialsAreSaved() = runTest {
        val loginCredentials = LoginCredentials(domain = "example.com", username = "foo", password = "bar")
        val bundle = bundleForSaveDialog("example.com", loginCredentials)
        testee.processSaveCredentialsResult(bundle, credentialsSaver)
        verify(credentialsSaver).saveCredentials(eq("example.com"), eq(loginCredentials))
    }

    @Test
    fun whenCredentialsSelectionBundleEmptyThenAuthenticatorNotCalled() {
        testee.processAutofillCredentialSelectionResult(Bundle(), dummyFragment, credentialsInjector)
        verifyAuthenticatorNeverCalled()
    }

    @Test
    fun whenCredentialsSelectionBundleEmptyThenNoAutofillResponseGiven() {
        testee.processAutofillCredentialSelectionResult(Bundle(), dummyFragment, credentialsInjector)
        verifyNoAutofillResponseGiven()
    }

    @Test
    fun whenCredentialsSelectionBundleMissingUrlThenNoAutofillResponseGiven() {
        val bundle = bundleForSelectionDialog(url = null, cancelled = false, credentials = someLoginCredentials())
        testee.processAutofillCredentialSelectionResult(bundle, dummyFragment, credentialsInjector)
        verifyNoAutofillResponseGiven()
    }

    @Test
    fun whenCredentialsSelectionIsCancelledThenAutofillRequestCancelled() {
        val bundle = bundleForSelectionDialog(url = "example.com", cancelled = true, credentials = someLoginCredentials())
        testee.processAutofillCredentialSelectionResult(bundle, dummyFragment, credentialsInjector)
        verifyAutofillResponseCancelled("example.com")
    }

    @Test
    fun whenCredentialsSelectionMadeAndAuthorizedThenCredentialsSharedWithPage() {
        setupAuthenticatorAlwaysAuth()
        val bundle = bundleForSelectionDialog(url = "example.com", cancelled = false, credentials = someLoginCredentials())
        testee.processAutofillCredentialSelectionResult(bundle, dummyFragment, credentialsInjector)
        verifyAuthenticatorIsCalled()
        verifyCredentialsSharedWithPage("example.com", someLoginCredentials())
    }

    @Test
    fun whenCredentialsSelectionMadeButNotAuthorizedThenAutofillRequestCancelled() {
        setupAuthenticatorAlwaysDeny()
        val bundle = bundleForSelectionDialog(url = "example.com", cancelled = false, credentials = someLoginCredentials())
        testee.processAutofillCredentialSelectionResult(bundle, dummyFragment, credentialsInjector)
        verifyAuthenticatorIsCalled()
        verifyAutofillResponseCancelled("example.com")
    }

    @Test
    fun whenCredentialsSelectionIsCancelledThenAuthenticatorNotCalled() {
        val bundle = bundleForSelectionDialog("example.com", cancelled = true, someLoginCredentials())
        testee.processAutofillCredentialSelectionResult(bundle, BrowserTabFragment(), credentialsInjector)
        verifyAuthenticatorNeverCalled()
    }

    private fun verifySaveNeverCalled() {
        verify(credentialsSaver, never()).saveCredentials(any(), any())
    }

    private fun verifyCredentialsSharedWithPage(url: String, credentials: LoginCredentials) {
        verify(credentialsInjector).shareCredentialsWithPage(url, credentials)
    }

    private fun verifyAutofillResponseCancelled(url: String) {
        verify(credentialsInjector).returnNoCredentialsWithPage(url)
    }

    private fun verifyAuthenticatorNeverCalled() {
        assertFalse(deviceAuthenticator.authenticateCalled)
    }

    private fun verifyAuthenticatorIsCalled() {
        assertTrue(deviceAuthenticator.authenticateCalled)
    }

    private fun verifyNoAutofillResponseGiven() {
        verify(credentialsInjector, never()).returnNoCredentialsWithPage(any())
        verify(credentialsInjector, never()).shareCredentialsWithPage(any(), any())
    }

    private fun someLoginCredentials() = LoginCredentials(domain = "example.com", username = "foo", password = "bar")

    private fun bundleForSaveDialog(url: String?, credentials: LoginCredentials?): Bundle {
        return Bundle().also {
            if (url != null) it.putString(CredentialSavePickerDialog.KEY_URL, url)
            if (credentials != null) it.putParcelable(CredentialSavePickerDialog.KEY_CREDENTIALS, credentials)
        }
    }

    private fun bundleForSelectionDialog(url: String?, cancelled: Boolean?, credentials: LoginCredentials?): Bundle {
        return Bundle().also {
            if (url != null) it.putString(CredentialAutofillPickerDialog.KEY_URL, url)
            if (cancelled != null) it.putBoolean(CredentialAutofillPickerDialog.KEY_CANCELLED, cancelled)
            if (credentials != null) it.putParcelable(CredentialSavePickerDialog.KEY_CREDENTIALS, credentials)
        }
    }

    private fun setupAuthenticatorAlwaysAuth() {
        deviceAuthenticator = AuthorizeEverything()
        testee = AutofillCredentialsSelectionResultHandler(deviceAuthenticator)
    }

    private fun setupAuthenticatorAlwaysDeny() {
        deviceAuthenticator = DenyEverything()
        testee = AutofillCredentialsSelectionResultHandler(deviceAuthenticator)
    }

    private abstract class FakeAuthenticator : DeviceAuthenticator {

        var authenticateCalled: Boolean = false
        abstract val authenticationWillSucceed: Boolean

        private fun authenticationCalled(onResult: (DeviceAuthenticator.AuthResult) -> Unit) {
            authenticateCalled = true
            if (authenticationWillSucceed) {
                onResult(Success)
            } else {
                onResult(Failed)
            }
        }

        override fun hasValidDeviceAuthentication(): Boolean = true

        override fun authenticate(
            featureToAuth: DeviceAuthenticator.Features,
            fragment: Fragment,
            onResult: (DeviceAuthenticator.AuthResult) -> Unit
        ) {
            authenticationCalled(onResult)
        }

        override fun authenticate(
            featureToAuth: DeviceAuthenticator.Features,
            fragmentActivity: FragmentActivity,
            onResult: (DeviceAuthenticator.AuthResult) -> Unit
        ) {
            authenticationCalled(onResult)
        }

        class AuthorizeEverything(override val authenticationWillSucceed: Boolean = true) : FakeAuthenticator()
        class DenyEverything(override val authenticationWillSucceed: Boolean = false) : FakeAuthenticator()
    }
}
