package com.duckduckgo.autofill.impl.service

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.securestorage.SecureStorage
import com.duckduckgo.autofill.sync.CredentialsFixtures.toWebsiteLoginCredentials
import com.duckduckgo.autofill.sync.CredentialsFixtures.twitterCredentials
import com.duckduckgo.autofill.sync.FakeSecureStorage
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class AutofillProviderChooseViewModelTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val autofillProviderDeviceAuth: AutofillProviderDeviceAuth = mock()

    private val secureStorage: SecureStorage = FakeSecureStorage()

    private val testee = AutofillProviderChooseViewModel(
        autofillProviderDeviceAuth = autofillProviderDeviceAuth,
        dispatchers = coroutineRule.testDispatcherProvider,
        secureStorage = secureStorage,
    )

    @Test
    fun whenAuthenticationRequiredThenEmitCommandRequestAuthentication() = runTest {
        whenever(autofillProviderDeviceAuth.isAuthRequired()).thenReturn(true)
        testee.commands().test {
            val awaitItem = awaitItem()
            assertEquals(AutofillProviderChooseViewModel.Command.RequestAuthentication, awaitItem)
        }
    }

    @Test
    fun whenAuthenticationNotRequiredthenEmitCommandContinueWithoutAuthentication() = runTest {
        whenever(autofillProviderDeviceAuth.isAuthRequired()).thenReturn(false)
        testee.commands().test {
            val awaitItem = awaitItem()
            assertEquals(AutofillProviderChooseViewModel.Command.ContinueWithoutAuthentication, awaitItem)
        }
    }

    @Test
    fun whenUserAuthenticatedSuccessfullyThenRecordAuthorization() = runTest {
        testee.onUserAuthenticatedSuccessfully()
        verify(autofillProviderDeviceAuth).recordSuccessfulAuthorization()
    }

    @Test
    fun whenUserAuthenticatedSuccessfullyThenEmitContinueWithoutAuthentication() = runTest {
        whenever(autofillProviderDeviceAuth.isAuthRequired()).thenReturn(true)
        testee.commands().test {
            awaitItem() // requires auth
            testee.onUserAuthenticatedSuccessfully()
            val awaitItem = awaitItem()
            assertEquals(AutofillProviderChooseViewModel.Command.ContinueWithoutAuthentication, awaitItem)
        }
    }

    @Test
    fun whenContinueAfterAuthenticationThenEmitAutofillLogin() = runTest {
        givenLocalCredentials(twitterCredentials)
        whenever(autofillProviderDeviceAuth.isAuthRequired()).thenReturn(true)

        testee.commands().test {
            awaitItem() // requires auth
            testee.continueAfterAuthentication(twitterCredentials.id!!)
            val awaitItem = awaitItem()
            assertEquals(AutofillProviderChooseViewModel.Command.AutofillLogin(twitterCredentials), awaitItem)
        }
    }

    private suspend fun givenLocalCredentials(vararg credentials: LoginCredentials) {
        credentials.forEach {
            secureStorage.addWebsiteLoginDetailsWithCredentials(it.toWebsiteLoginCredentials())
        }
    }
}
