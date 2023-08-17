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
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.browser.BrowserTabFragment
import com.duckduckgo.app.browser.autofill.AutofillCredentialsSelectionResultHandler.AutofillCredentialSaver
import com.duckduckgo.app.browser.autofill.AutofillCredentialsSelectionResultHandler.CredentialInjector
import com.duckduckgo.app.browser.autofill.AutofillCredentialsSelectionResultHandlerTest.FakeAuthenticator.AuthorizeEverything
import com.duckduckgo.app.browser.autofill.AutofillCredentialsSelectionResultHandlerTest.FakeAuthenticator.CancelEverything
import com.duckduckgo.app.browser.autofill.AutofillCredentialsSelectionResultHandlerTest.FakeAuthenticator.FailEverything
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autofill.api.AutofillCapabilityChecker
import com.duckduckgo.autofill.api.CredentialAutofillPickerDialog
import com.duckduckgo.autofill.api.CredentialSavePickerDialog
import com.duckduckgo.autofill.api.CredentialUpdateExistingCredentialsDialog
import com.duckduckgo.autofill.api.CredentialUpdateExistingCredentialsDialog.CredentialUpdateType
import com.duckduckgo.autofill.api.ExistingCredentialMatchDetector
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.api.passwordgeneration.AutomaticSavedLoginsMonitor
import com.duckduckgo.autofill.api.store.AutofillStore
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames
import com.duckduckgo.autofill.impl.ui.credential.saving.declines.AutofillDeclineCounter
import com.duckduckgo.deviceauth.api.DeviceAuthenticator
import com.duckduckgo.deviceauth.api.DeviceAuthenticator.AuthResult
import com.duckduckgo.deviceauth.api.DeviceAuthenticator.AuthResult.Success
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class AutofillCredentialsSelectionResultHandlerTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val credentialsSaver: AutofillCredentialSaver = mock()
    private val credentialsInjector: CredentialInjector = mock()
    private val declineCounter: AutofillDeclineCounter = mock()
    private val autofillStore: AutofillStore = mock()
    private val pixel: Pixel = mock()
    private val dummyFragment = Fragment()
    private val autofillDialogSuppressor: AutofillFireproofDialogSuppressor = mock()
    private lateinit var deviceAuthenticator: FakeAuthenticator
    private lateinit var testee: AutofillCredentialsSelectionResultHandler
    private val autoSavedLoginsMonitor: AutomaticSavedLoginsMonitor = mock()
    private val existingCredentialMatchDetector: ExistingCredentialMatchDetector = mock()
    private val autofillCapabilityChecker: AutofillCapabilityChecker = mock()

    @Test
    fun whenSaveBundleMissingUrlThenNoAttemptToSaveMade() = runTest {
        setupAuthenticatorAlwaysAuth()
        val bundle = bundleForSaveDialog(url = null, credentials = someLoginCredentials())
        testee.processSaveCredentialsResult(bundle, credentialsSaver)
        verifySaveNeverCalled()
    }

    @Test
    fun whenSaveBundleMissingCredentialsThenNoAttemptToSaveMade() = runTest {
        setupAuthenticatorAlwaysAuth()
        val bundle = bundleForSaveDialog(url = "example.com", credentials = null)
        testee.processSaveCredentialsResult(bundle, credentialsSaver)
        verifySaveNeverCalled()
    }

    @Test
    fun whenSaveBundleWellFormedThenCredentialsAreSaved() = runTest {
        setupAuthenticatorAlwaysAuth()
        val loginCredentials = LoginCredentials(domain = "example.com", username = "foo", password = "bar")
        val bundle = bundleForSaveDialog("example.com", loginCredentials)
        testee.processSaveCredentialsResult(bundle, credentialsSaver)
        verify(credentialsSaver).saveCredentials(eq("example.com"), eq(loginCredentials))
    }

    @Test
    fun whenSaveCredentialsForFirstTimeThenDisableDeclineCountMonitoringFlag() = runTest {
        setupAuthenticatorAlwaysAuth()
        val loginCredentials = LoginCredentials(domain = "example.com", username = "foo", password = "bar")
        val bundle = bundleForSaveDialog("example.com", loginCredentials)
        whenever(credentialsSaver.saveCredentials(any(), any())).thenReturn(loginCredentials)
        testee.processSaveCredentialsResult(bundle, credentialsSaver)
        verify(declineCounter).disableDeclineCounter()
    }

    @Test
    fun whenSaveCredentialsUnsuccessfulThenDoesDisableDeclineCountMonitoringFlag() = runTest {
        setupAuthenticatorAlwaysAuth()
        val bundle = bundleForSaveDialog("example.com", null)
        whenever(credentialsSaver.saveCredentials(any(), any())).thenReturn(null)
        testee.processSaveCredentialsResult(bundle, credentialsSaver)
        verify(declineCounter, never()).disableDeclineCounter()
    }

    @Test
    fun whenUpdateBundleMissingUrlThenNoAttemptToUpdateMade() = runTest {
        setupAuthenticatorAlwaysAuth()
        val bundle = bundleForUpdateDialog(url = null, credentials = someLoginCredentials(), CredentialUpdateType.Password)
        testee.processUpdateCredentialsResult(bundle, credentialsSaver)
        verifyUpdateNeverCalled()
    }

    @Test
    fun whenUpdateBundleMissingCredentialsThenNoAttemptToSaveMade() = runTest {
        setupAuthenticatorAlwaysAuth()
        val bundle = bundleForUpdateDialog(url = "example.com", credentials = null, CredentialUpdateType.Password)
        testee.processUpdateCredentialsResult(bundle, credentialsSaver)
        verifyUpdateNeverCalled()
    }

    @Test
    fun whenUpdateBundleWellFormedThenCredentialsAreUpdated() = runTest {
        setupAuthenticatorAlwaysAuth()
        val loginCredentials = LoginCredentials(domain = "example.com", username = "foo", password = "bar")
        val bundle = bundleForUpdateDialog("example.com", loginCredentials, CredentialUpdateType.Password)
        testee.processUpdateCredentialsResult(bundle, credentialsSaver)
        verify(credentialsSaver).updateCredentials(eq("example.com"), eq(loginCredentials), eq(CredentialUpdateType.Password))
        verifySaveNeverCalled()
    }

    @Test
    fun whenCredentialsSelectionBundleEmptyThenAuthenticatorNotCalled() = runTest {
        setupAuthenticatorAlwaysAuth()
        testee.processAutofillCredentialSelectionResult(Bundle(), dummyFragment, credentialsInjector)
        verifyAuthenticatorNeverCalled()
    }

    @Test
    fun whenCredentialsSelectionBundleEmptyThenNoAutofillResponseGiven() = runTest {
        setupAuthenticatorAlwaysAuth()
        testee.processAutofillCredentialSelectionResult(Bundle(), dummyFragment, credentialsInjector)
        verifyNoAutofillResponseGiven()
    }

    @Test
    fun whenCredentialsSelectionBundleMissingUrlThenNoAutofillResponseGiven() = runTest {
        setupAuthenticatorAlwaysAuth()
        val bundle = bundleForSelectionDialog(url = null, cancelled = false, credentials = someLoginCredentials())
        testee.processAutofillCredentialSelectionResult(bundle, dummyFragment, credentialsInjector)
        verifyNoAutofillResponseGiven()
    }

    @Test
    fun whenCredentialsSelectionIsCancelledThenAutofillRequestCancelled() = runTest {
        setupAuthenticatorAlwaysAuth()
        val bundle = bundleForSelectionDialog(url = "example.com", cancelled = true, credentials = someLoginCredentials())
        testee.processAutofillCredentialSelectionResult(bundle, dummyFragment, credentialsInjector)
        verifyAutofillResponseCancelled("example.com")
    }

    @Test
    fun whenCredentialsSelectionMadeAndAuthorizedThenCredentialsSharedWithPage() = runTest {
        setupAuthenticatorAlwaysAuth()
        val bundle = bundleForSelectionDialog(url = "example.com", cancelled = false, credentials = someLoginCredentials())
        testee.processAutofillCredentialSelectionResult(bundle, dummyFragment, credentialsInjector)
        verifyAuthenticatorIsCalled()
        verifyCredentialsSharedWithPage("example.com", someLoginCredentials())
    }

    @Test
    fun whenCredentialsSelectionMadeThenAuthShownPixelFired() = runTest {
        setupAuthenticatorAlwaysAuth()
        val bundle = bundleForSelectionDialog(url = "example.com", cancelled = false, credentials = someLoginCredentials())
        testee.processAutofillCredentialSelectionResult(bundle, dummyFragment, credentialsInjector)
        verify(pixel).fire(AutofillPixelNames.AUTOFILL_AUTHENTICATION_TO_AUTOFILL_SHOWN)
    }

    @Test
    fun whenCredentialsSelectionMadeAndAuthorizedThenCorrectPixelFired() = runTest {
        setupAuthenticatorAlwaysAuth()
        val bundle = bundleForSelectionDialog(url = "example.com", cancelled = false, credentials = someLoginCredentials())
        testee.processAutofillCredentialSelectionResult(bundle, dummyFragment, credentialsInjector)
        verify(pixel).fire(AutofillPixelNames.AUTOFILL_AUTHENTICATION_TO_AUTOFILL_AUTH_SUCCESSFUL)
    }

    @Test
    fun whenCredentialsSelectionMadeButNotAuthorizedThenAutofillRequestCancelled() = runTest {
        setupAuthenticatorAlwaysCancel()
        val bundle = bundleForSelectionDialog(url = "example.com", cancelled = false, credentials = someLoginCredentials())
        testee.processAutofillCredentialSelectionResult(bundle, dummyFragment, credentialsInjector)
        verifyAuthenticatorIsCalled()
        verifyAutofillResponseCancelled("example.com")
    }

    @Test
    fun whenCredentialsSelectionMadeButAuthCancelledThenCorrectPixelFired() = runTest {
        setupAuthenticatorAlwaysCancel()
        val bundle = bundleForSelectionDialog(url = "example.com", cancelled = false, credentials = someLoginCredentials())
        testee.processAutofillCredentialSelectionResult(bundle, dummyFragment, credentialsInjector)
        verify(pixel).fire(AutofillPixelNames.AUTOFILL_AUTHENTICATION_TO_AUTOFILL_AUTH_CANCELLED)
    }

    @Test
    fun whenCredentialsSelectionMadeButAuthFailedThenCorrectPixelFired() = runTest {
        setupAuthenticatorAlwaysFail()
        val bundle = bundleForSelectionDialog(url = "example.com", cancelled = false, credentials = someLoginCredentials())
        testee.processAutofillCredentialSelectionResult(bundle, dummyFragment, credentialsInjector)
        verify(pixel).fire(AutofillPixelNames.AUTOFILL_AUTHENTICATION_TO_AUTOFILL_AUTH_FAILURE)
    }

    @Test
    fun whenCredentialsSelectionIsCancelledThenAuthenticatorNotCalled() = runTest {
        setupAuthenticatorAlwaysAuth()
        val bundle = bundleForSelectionDialog("example.com", cancelled = true, someLoginCredentials())
        testee.processAutofillCredentialSelectionResult(bundle, BrowserTabFragment(), credentialsInjector)
        verifyAuthenticatorNeverCalled()
    }

    @Test
    fun whenSaveOrUpdateDialogDismissedThenAutofillDialogSuppressorCalled() = runTest {
        setupAuthenticatorAlwaysAuth()
        testee.processSaveOrUpdatePromptDismissed()
        verify(autofillDialogSuppressor).autofillSaveOrUpdateDialogVisibilityChanged(visible = false)
    }

    @Test
    fun whenSaveOrUpdateDialogShownThenAutofillDialogSuppressorCalled() = runTest {
        setupAuthenticatorAlwaysAuth()
        testee.processSaveOrUpdatePromptShown()
        verify(autofillDialogSuppressor).autofillSaveOrUpdateDialogVisibilityChanged(visible = true)
    }

    @Test
    fun whenPrivateDuckAddressSelectedButAutoSaveLoginIsFalseThenNoLoginAutomaticallySaved() = runTest {
        setupAuthenticatorAlwaysAuth()
        configureSavingPasswordCapabilityEnabled()
        testee.processPrivateDuckAddressInjectedEvent(
            duckAddress = "foo@duck.com",
            tabId = "abc",
            originalUrl = "example.com",
            autoSaveLogin = false,
        )
        verifySaveNeverCalled()
    }

    @Test
    fun whenPrivateDuckAddressSelectedButSavingPasswordsDisabledGeneratedThenNoLoginAutomaticallySaved() = runTest {
        setupAuthenticatorAlwaysAuth()
        configureSavingPasswordCapabilityDisabled()
        testee.processPrivateDuckAddressInjectedEvent(
            duckAddress = "foo@duck.com",
            tabId = "abc",
            originalUrl = "example.com",
            autoSaveLogin = true,
        )
        verifySaveNeverCalled()
    }

    @Test
    fun whenPrivateDuckAddressSelectedAndSavingPasswordsEnabledGeneratedThenLoginAutomaticallySaved() = runTest {
        setupAuthenticatorAlwaysAuth()
        configureSavingPasswordCapabilityEnabled()
        testee.processPrivateDuckAddressInjectedEvent(
            duckAddress = "foo@duck.com",
            tabId = "abc",
            originalUrl = "example.com",
            autoSaveLogin = true,
        )
        verify(autofillStore).saveCredentials(any(), any())
    }

    @Test
    fun whenPrivateDuckAddressSelectedWithSameUsernameAsAlreadyAutosavedLoginThenLoginNeitherSavedNorUpdated() = runTest {
        setupAuthenticatorAlwaysAuth()
        configureSavingPasswordCapabilityEnabled()
        configurePreviouslyAutosavedLogin()
        testee.processPrivateDuckAddressInjectedEvent(
            duckAddress = "foo",
            tabId = "abc",
            originalUrl = "example.com",
            autoSaveLogin = true,
        )
        verifySaveNeverCalled()
        verifyUpdateNeverCalled()
    }

    @Test
    fun whenPrivateDuckAddressSelectedWithDifferentUsernameToAlreadyAutosavedLoginThenLoginAutomaticallyUpdated() = runTest {
        setupAuthenticatorAlwaysAuth()
        configureSavingPasswordCapabilityEnabled()
        configurePreviouslyAutosavedLogin()
        testee.processPrivateDuckAddressInjectedEvent(
            duckAddress = "foo@duck.com",
            tabId = "abc",
            originalUrl = "example.com",
            autoSaveLogin = true,
        )
        verify(autofillStore).updateCredentials(any())
    }

    private suspend fun configurePreviouslyAutosavedLogin() {
        whenever(autoSavedLoginsMonitor.getAutoSavedLoginId(any())).thenReturn(1)
        whenever(autofillStore.getCredentialsWithId(any())).thenReturn(someLoginCredentials())
    }

    private suspend fun configureSavingPasswordCapabilityEnabled() {
        whenever(autofillCapabilityChecker.canSaveCredentialsFromWebView(any())).thenReturn(true)
    }

    private suspend fun configureSavingPasswordCapabilityDisabled() {
        whenever(autofillCapabilityChecker.canSaveCredentialsFromWebView(any())).thenReturn(false)
    }

    private suspend fun verifySaveNeverCalled() {
        verify(credentialsSaver, never()).saveCredentials(any(), any())
    }

    private suspend fun verifyUpdateNeverCalled() {
        verify(credentialsSaver, never()).updateCredentials(any(), any(), any())
    }

    private fun verifyCredentialsSharedWithPage(
        url: String,
        credentials: LoginCredentials,
    ) {
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

    private fun bundleForSaveDialog(
        url: String?,
        credentials: LoginCredentials?,
    ): Bundle {
        return Bundle().also {
            if (url != null) it.putString(CredentialSavePickerDialog.KEY_URL, url)
            if (credentials != null) it.putParcelable(CredentialSavePickerDialog.KEY_CREDENTIALS, credentials)
        }
    }

    private fun bundleForUpdateDialog(
        url: String?,
        credentials: LoginCredentials?,
        updateType: CredentialUpdateType,
    ): Bundle {
        return Bundle().also {
            if (url != null) it.putString(CredentialUpdateExistingCredentialsDialog.KEY_URL, url)
            if (credentials != null) it.putParcelable(CredentialUpdateExistingCredentialsDialog.KEY_CREDENTIALS, credentials)
            it.putParcelable(CredentialUpdateExistingCredentialsDialog.KEY_CREDENTIAL_UPDATE_TYPE, updateType)
        }
    }

    private fun bundleForSelectionDialog(
        url: String?,
        cancelled: Boolean?,
        credentials: LoginCredentials?,
    ): Bundle {
        return Bundle().also {
            if (url != null) it.putString(CredentialAutofillPickerDialog.KEY_URL, url)
            if (cancelled != null) it.putBoolean(CredentialAutofillPickerDialog.KEY_CANCELLED, cancelled)
            if (credentials != null) it.putParcelable(CredentialSavePickerDialog.KEY_CREDENTIALS, credentials)
        }
    }

    private fun TestScope.setupAuthenticatorAlwaysAuth() {
        deviceAuthenticator = AuthorizeEverything()
        testee = initialiseTestee()
    }

    private fun TestScope.setupAuthenticatorAlwaysCancel() {
        deviceAuthenticator = CancelEverything()
        testee = initialiseTestee()
    }

    private fun TestScope.setupAuthenticatorAlwaysFail() {
        deviceAuthenticator = FailEverything()
        testee = initialiseTestee()
    }

    private fun TestScope.initialiseTestee(): AutofillCredentialsSelectionResultHandler {
        return AutofillCredentialsSelectionResultHandler(
            deviceAuthenticator = deviceAuthenticator,
            declineCounter = declineCounter,
            autofillStore = autofillStore,
            appCoroutineScope = this,
            pixel = pixel,
            autofillDialogSuppressor = autofillDialogSuppressor,
            autoSavedLoginsMonitor = autoSavedLoginsMonitor,
            existingCredentialMatchDetector = existingCredentialMatchDetector,
            dispatchers = coroutineTestRule.testDispatcherProvider,
            autofillCapabilityChecker = autofillCapabilityChecker,
            appBuildConfig = mock(),
        )
    }

    private abstract class FakeAuthenticator : DeviceAuthenticator {

        sealed interface Result {
            object Success : Result
            object Cancelled : Result
            object Failure : Result
        }

        var authenticateCalled: Boolean = false
        abstract val authResult: Result

        private fun authenticationCalled(onResult: (DeviceAuthenticator.AuthResult) -> Unit) {
            authenticateCalled = true
            when (authResult) {
                is Result.Success -> onResult(Success)
                is Result.Cancelled -> onResult(AuthResult.UserCancelled)
                is Result.Failure -> onResult(AuthResult.Error("Authentication failed"))
            }
        }

        override fun hasValidDeviceAuthentication(): Boolean = true

        override fun authenticate(
            featureToAuth: DeviceAuthenticator.Features,
            fragment: Fragment,
            onResult: (DeviceAuthenticator.AuthResult) -> Unit,
        ) {
            authenticationCalled(onResult)
        }

        override fun authenticate(
            featureToAuth: DeviceAuthenticator.Features,
            fragmentActivity: FragmentActivity,
            onResult: (DeviceAuthenticator.AuthResult) -> Unit,
        ) {
            authenticationCalled(onResult)
        }

        class AuthorizeEverything(override val authResult: Result = Result.Success) : FakeAuthenticator()
        class FailEverything(override val authResult: Result = Result.Failure) : FakeAuthenticator()
        class CancelEverything(override val authResult: Result = Result.Cancelled) : FakeAuthenticator()
    }
}
