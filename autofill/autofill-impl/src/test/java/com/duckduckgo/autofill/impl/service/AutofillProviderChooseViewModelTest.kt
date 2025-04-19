package com.duckduckgo.autofill.impl.service

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autofill.api.AutofillFeature
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.fakeAutofillStore
import com.duckduckgo.autofill.fakeStorage
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_SERVICE_SUGGESTION_CONFIRMED
import com.duckduckgo.autofill.impl.securestorage.SecureStorage
import com.duckduckgo.autofill.sync.CredentialsFixtures.toLoginCredentials
import com.duckduckgo.autofill.sync.CredentialsFixtures.toWebsiteLoginCredentials
import com.duckduckgo.autofill.sync.CredentialsFixtures.twitterCredentials
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
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

    private val secureStorage: SecureStorage = fakeStorage()

    private val pixel: Pixel = mock()

    val autofillFeature = FakeFeatureToggleFactory.create(AutofillFeature::class.java)

    private val testee = AutofillProviderChooseViewModel(
        autofillProviderDeviceAuth = autofillProviderDeviceAuth,
        dispatchers = coroutineRule.testDispatcherProvider,
        autofillStore = fakeAutofillStore(
            secureStorage = secureStorage,
            autofillPrefsStore = mock(),
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            coroutineScope = coroutineRule.testScope,
            autofillFeature = autofillFeature,
        ),
        appCoroutineScope = coroutineRule.testScope,
        pixel = pixel,
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

    @Test
    fun whenContinueAfterAuthenticationThenUpdateLastUsedTimestamp() = runTest {
        givenLocalCredentials(twitterCredentials)
        whenever(autofillProviderDeviceAuth.isAuthRequired()).thenReturn(true)

        testee.commands().test {
            awaitItem() // requires auth
            testee.continueAfterAuthentication(twitterCredentials.id!!)
            awaitItem()
            val updatedCredential = secureStorage.getWebsiteLoginDetailsWithCredentials(twitterCredentials.id!!)!!.toLoginCredentials()
            assertFalse(twitterCredentials.lastUsedMillis == updatedCredential.lastUsedMillis)
        }
    }

    @Test
    fun whenContinueAfterAuthenticationThenFireAutofillServiceSuggestionConfirmedPixel() = runTest {
        givenLocalCredentials(twitterCredentials)
        whenever(autofillProviderDeviceAuth.isAuthRequired()).thenReturn(true)

        testee.commands().test {
            awaitItem() // requires auth
            testee.continueAfterAuthentication(twitterCredentials.id!!)
            awaitItem()
            verify(pixel).fire(AUTOFILL_SERVICE_SUGGESTION_CONFIRMED)
        }
    }

    private suspend fun givenLocalCredentials(vararg credentials: LoginCredentials) {
        credentials.forEach {
            secureStorage.addWebsiteLoginDetailsWithCredentials(it.toWebsiteLoginCredentials())
        }
    }
}
