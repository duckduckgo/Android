package com.duckduckgo.autofill.impl.importing.gpm.webflow

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.duckduckgo.autofill.api.AutofillFeature
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.api.domain.app.LoginTriggerType.AUTOPROMPT
import com.duckduckgo.autofill.api.domain.app.LoginTriggerType.USER_INITIATED
import com.duckduckgo.autofill.impl.importing.CredentialImporter
import com.duckduckgo.autofill.impl.importing.CsvCredentialConverter
import com.duckduckgo.autofill.impl.importing.CsvCredentialConverter.CsvCredentialImportResult.Error
import com.duckduckgo.autofill.impl.importing.CsvCredentialConverter.CsvCredentialImportResult.Success
import com.duckduckgo.autofill.impl.importing.gpm.feature.AutofillImportPasswordConfigStore
import com.duckduckgo.autofill.impl.importing.gpm.feature.AutofillImportPasswordSettings
import com.duckduckgo.autofill.impl.importing.gpm.webflow.ImportGooglePasswordsWebFlowViewModel.Command.InjectCredentialsFromReauth
import com.duckduckgo.autofill.impl.importing.gpm.webflow.ImportGooglePasswordsWebFlowViewModel.Command.NoCredentialsAvailable
import com.duckduckgo.autofill.impl.importing.gpm.webflow.ImportGooglePasswordsWebFlowViewModel.Command.PromptUserToSelectFromStoredCredentials
import com.duckduckgo.autofill.impl.importing.gpm.webflow.ImportGooglePasswordsWebFlowViewModel.UserCannotImportReason.WebViewCrash
import com.duckduckgo.autofill.impl.importing.gpm.webflow.ImportGooglePasswordsWebFlowViewModel.ViewState.LoadStartPage
import com.duckduckgo.autofill.impl.importing.gpm.webflow.ImportGooglePasswordsWebFlowViewModel.ViewState.NavigatingBack
import com.duckduckgo.autofill.impl.importing.gpm.webflow.ImportGooglePasswordsWebFlowViewModel.ViewState.UserCancelledImportFlow
import com.duckduckgo.autofill.impl.importing.gpm.webflow.ImportGooglePasswordsWebFlowViewModel.ViewState.UserFinishedCannotImport
import com.duckduckgo.autofill.impl.importing.gpm.webflow.ImportGooglePasswordsWebFlowViewModel.ViewState.UserFinishedImportFlow
import com.duckduckgo.autofill.impl.store.ReAuthenticationDetails
import com.duckduckgo.autofill.impl.store.ReauthenticationHandler
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class ImportGooglePasswordsWebFlowViewModelTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val credentialImporter: CredentialImporter = mock()
    private val csvCredentialConverter: CsvCredentialConverter = mock()
    private val autofillImportConfigStore: AutofillImportPasswordConfigStore = mock()
    private val urlToStageMapper: ImportGooglePasswordUrlToStageMapper = mock()
    private val reauthenticationHandler: ReauthenticationHandler = mock()
    private val autofillFeature: AutofillFeature = mock()

    private val testee = ImportGooglePasswordsWebFlowViewModel(
        dispatchers = coroutineTestRule.testDispatcherProvider,
        credentialImporter = credentialImporter,
        csvCredentialConverter = csvCredentialConverter,
        autofillImportConfigStore = autofillImportConfigStore,
        urlToStageMapper = urlToStageMapper,
        reauthenticationHandler = reauthenticationHandler,
        autofillFeature = autofillFeature,
    )

    @Test
    fun whenOnViewCreatedThenLoadStartPageState() = runTest {
        configureFeature(launchUrlGooglePasswords = "https://example.com")
        testee.onViewCreated()
        testee.viewState.test {
            assertEquals(LoadStartPage("https://example.com"), awaitItem())
        }
    }

    @Test
    fun whenCsvParseErrorThenUserFinishedCannotImport() = runTest {
        configureCsvParseError()
        testee.viewState.test {
            awaitItem() as UserFinishedCannotImport
        }
    }

    @Test
    fun whenCsvParseSuccessNoCredentialsThenUserFinishedImportFlow() = runTest {
        configureCsvSuccess(loginCredentialsToImport = emptyList())
        testee.viewState.test {
            awaitItem() as UserFinishedImportFlow
        }
    }

    @Test
    fun whenCsvParseSuccessWithCredentialsThenUserFinishedImportFlow() = runTest {
        configureCsvSuccess(loginCredentialsToImport = listOf(creds()))
        testee.viewState.test {
            awaitItem() as UserFinishedImportFlow
        }
    }

    @Test
    fun whenBackButtonPressedAndCannotGoBackThenUserCancelledImportFlowState() = runTest {
        whenever(urlToStageMapper.getStage(any())).thenReturn("stage")
        testee.onBackButtonPressed(url = "https://example.com", canGoBack = false)
        testee.viewState.test {
            awaitItem() as UserCancelledImportFlow
        }
    }

    @Test
    fun whenBackButtonPressedAndCanGoBackThenNavigatingBackState() = runTest {
        testee.onBackButtonPressed(url = "https://example.com", canGoBack = true)
        testee.viewState.test {
            awaitItem() as NavigatingBack
        }
    }

    @Test
    fun whenCloseButtonPressedThenUserCancelledImportFlowState() = runTest {
        val expectedStage = "stage"
        whenever(urlToStageMapper.getStage(any())).thenReturn(expectedStage)
        testee.onCloseButtonPressed("https://example.com")
        testee.viewState.test {
            assertEquals(expectedStage, (awaitItem() as UserCancelledImportFlow).stage)
        }
    }

    @Test
    fun whenStoredCredentialsAvailableWithReauthAllowedAndPasswordThenInjectCredentialsFromReauth() = runTest {
        val url = "https://example.com"
        val password = "test-password"
        val credentials = listOf(creds())
        configureReAuthenticationFeatureFlagEnabled()
        whenever(reauthenticationHandler.retrieveReauthData(url)).thenReturn(ReAuthenticationDetails(password = password))

        testee.commands.test {
            testee.onStoredCredentialsAvailable(url, credentials, USER_INITIATED, scenarioAllowsReAuthentication = true)
            assertTrue(awaitItem() is InjectCredentialsFromReauth)
        }
    }

    @Test
    fun whenStoredCredentialsAvailableWithReauthAllowedButNoPasswordThenPromptUser() = runTest {
        val url = "https://example.com"
        val credentials = listOf(creds())
        configureReAuthenticationFeatureFlagEnabled()
        whenever(reauthenticationHandler.retrieveReauthData(url)).thenReturn(ReAuthenticationDetails(password = null))

        testee.commands.test {
            testee.onStoredCredentialsAvailable(url, credentials, USER_INITIATED, scenarioAllowsReAuthentication = true)
            assertTrue(awaitItem() is PromptUserToSelectFromStoredCredentials)
        }
    }

    @Test
    fun whenStoredCredentialsAvailableWithReauthNotAllowedThenPromptUser() = runTest {
        val url = "https://example.com"
        val credentials = listOf(creds())

        testee.commands.test {
            testee.onStoredCredentialsAvailable(url, credentials, AUTOPROMPT, scenarioAllowsReAuthentication = false)
            assertTrue(awaitItem() is PromptUserToSelectFromStoredCredentials)
        }
    }

    @Test
    fun whenReauthFeatureFlagDisabledAndCredentialsAvailableThenPromptUser() = runTest {
        val url = "https://example.com"
        val credentials = listOf(creds())
        configureReAuthenticationFeatureFlagDisabled()
        configureReauthData(url, "test-password") // Even with reauth data, should prompt user

        testee.commands.test {
            testee.onStoredCredentialsAvailable(url, credentials, AUTOPROMPT, scenarioAllowsReAuthentication = true)
            assertTrue(awaitItem() is PromptUserToSelectFromStoredCredentials)
        }
    }

    @Test
    fun whenReauthFeatureFlagDisabledAndNoStoredCredentialsThenNoCredentialsCommand() = runTest {
        val url = "https://example.com"
        val password = "test-password"
        configureReAuthenticationFeatureFlagDisabled()
        configureReauthData(url, password) // Even with reauth data, should return NoCredentialsAvailable when flag disabled

        testee.commands.test {
            testee.onNoStoredCredentialsAvailable(url)
            assertTrue(awaitItem() is NoCredentialsAvailable)
        }
    }

    @Test
    fun whenReauthFeatureFlagEnabledButReauthDataHasNullPasswordThenPromptUser() = runTest {
        val url = "https://example.com"
        val credentials = listOf(creds())
        configureReAuthenticationFeatureFlagEnabled()
        configureReauthData(url, null) // No reauth data available

        testee.commands.test {
            testee.onStoredCredentialsAvailable(url, credentials, USER_INITIATED, scenarioAllowsReAuthentication = true)
            assertTrue(awaitItem() is PromptUserToSelectFromStoredCredentials)
        }
    }

    @Test
    fun whenReauthFeatureFlagEnabledAndReauthDataValidThenInjectCredentials() = runTest {
        val url = "https://example.com"
        val password = "test-password"
        val credentials = listOf(creds())
        configureReAuthenticationFeatureFlagEnabled()
        configureReauthData(url, password)

        testee.commands.test {
            testee.onStoredCredentialsAvailable(url, credentials, USER_INITIATED, scenarioAllowsReAuthentication = true)
            val command = awaitItem() as InjectCredentialsFromReauth
            assertEquals(url, command.url)
            assertEquals(password, command.password)
        }
    }

    @Test
    fun whenNoStoredCredentialsAndReauthFeatureFlagDisabledButNoReauthDataThenNoCredentialsCommand() = runTest {
        val url = "https://example.com"
        configureReAuthenticationFeatureFlagDisabled()
        configureReauthData(url, null) // No reauth data available

        testee.commands.test {
            testee.onNoStoredCredentialsAvailable(url)
            assertTrue(awaitItem() is NoCredentialsAvailable)
        }
    }

    @Test
    fun whenNoStoredCredentialsAndReauthFeatureFlagEnabledButNoReauthDataThenNoCredentialsCommand() = runTest {
        val url = "https://example.com"
        configureReAuthenticationFeatureFlagEnabled()
        configureReauthData(url, null)

        testee.commands.test {
            testee.onNoStoredCredentialsAvailable(url)
            assertTrue(awaitItem() is NoCredentialsAvailable)
        }
    }

    @Test
    fun whenNoStoredCredentialsAndReauthFeatureFlagEnabledWithReauthDataThenInjectCredentials() = runTest {
        val url = "https://example.com"
        val password = "test-password"
        configureReAuthenticationFeatureFlagEnabled()
        configureReauthData(url, password)

        testee.commands.test {
            testee.onNoStoredCredentialsAvailable(url)
            val command = awaitItem() as InjectCredentialsFromReauth
            assertEquals(url, command.url)
            assertEquals(password, command.password)
        }
    }

    @Test
    fun whenAutofillCredentialsWithPasswordAndReauthFeatureFlagEnabledThenStoreReauthData() = runTest {
        val url = "https://example.com"
        val password = "test-password"
        configureReAuthenticationFeatureFlagEnabled()

        testee.onCredentialsAutofilled(url, password)

        verify(reauthenticationHandler).storeForReauthentication(url, password)
    }

    @Test
    fun whenCredentialsAvailableToSaveWithPasswordAndReauthFeatureFlagEnabledThenStoreReauthData() = runTest {
        val url = "https://example.com"
        val password = "test-password"
        val credentials = creds(password = password)
        configureReAuthenticationFeatureFlagEnabled()

        testee.onCredentialsAvailableToSave(url, credentials)

        verify(reauthenticationHandler).storeForReauthentication(url, password)
    }

    @Test
    fun whenAutofillCredentialsWithPasswordAndReauthFeatureFlagDisabledThenDoNotStoreReauthData() = runTest {
        val url = "https://example.com"
        val password = "test-password"
        configureReAuthenticationFeatureFlagDisabled()

        testee.onCredentialsAutofilled(url, password)

        verify(reauthenticationHandler, never()).storeForReauthentication(any(), any())
    }

    @Test
    fun whenCredentialsAvailableToSaveWithPasswordAndReauthFeatureFlagDisabledThenDoNotStoreReauthData() = runTest {
        val url = "https://example.com"
        val password = "test-password"
        val credentials = creds(password = password)
        configureReAuthenticationFeatureFlagDisabled()

        testee.onCredentialsAvailableToSave(url, credentials)

        verify(reauthenticationHandler, never()).storeForReauthentication(any(), any())
    }

    @Test
    fun whenGetReauthDataAndReauthFeatureFlagDisabledThenReturnNull() = runTest {
        val url = "https://example.com"
        configureReAuthenticationFeatureFlagDisabled()

        val result = testee.getReauthData(url)

        assertEquals(null, result)
    }

    @Test
    fun whenGetReauthDataAndReauthFeatureFlagEnabledThenReturnReauthData() = runTest {
        val url = "https://example.com"
        val expectedReauthData = ReAuthenticationDetails(password = "test-password")
        configureReAuthenticationFeatureFlagEnabled()
        whenever(reauthenticationHandler.retrieveReauthData(url)).thenReturn(expectedReauthData)

        val result = testee.getReauthData(url)

        assertEquals(expectedReauthData, result)
    }

    @Test
    fun whenOnWebViewCrashThenUserFinishedCannotImportWithWebViewCrash() = runTest {
        testee.onWebViewCrash()
        testee.viewState.test {
            val viewState = awaitItem() as UserFinishedCannotImport
            assertEquals(WebViewCrash, viewState.reason)
        }
    }

    private fun configureReAuthenticationFeatureFlagEnabled() {
        val mockToggle: Toggle = mock()
        whenever(mockToggle.isEnabled()).thenReturn(true)
        whenever(autofillFeature.canReAuthenticateGoogleLoginsAutomatically()).thenReturn(mockToggle)
    }

    private fun configureReAuthenticationFeatureFlagDisabled() {
        val mockToggle: Toggle = mock()
        whenever(mockToggle.isEnabled()).thenReturn(false)
        whenever(autofillFeature.canReAuthenticateGoogleLoginsAutomatically()).thenReturn(mockToggle)
    }

    private fun configureReauthData(url: String, password: String?) {
        whenever(reauthenticationHandler.retrieveReauthData(url)).thenReturn(ReAuthenticationDetails(password = password))
    }

    private suspend fun configureFeature(
        canImportFromGooglePasswords: Boolean = true,
        launchUrlGooglePasswords: String = "https://example.com",
        javascriptConfigGooglePasswords: String = "\"{}\"",
    ) {
        whenever(autofillImportConfigStore.getConfig()).thenReturn(
            AutofillImportPasswordSettings(
                canImportFromGooglePasswords = canImportFromGooglePasswords,
                launchUrlGooglePasswords = launchUrlGooglePasswords,
                javascriptConfigGooglePasswords = javascriptConfigGooglePasswords,
                canInjectJavascript = true,
                urlMappings = emptyList(),
            ),
        )
    }

    private suspend fun configureCsvParseError() {
        whenever(csvCredentialConverter.readCsv(any<String>())).thenReturn(Error)
        testee.onCsvAvailable("")
    }

    private suspend fun configureCsvSuccess(
        loginCredentialsToImport: List<LoginCredentials> = emptyList(),
        numberCredentialsInSource: Int = loginCredentialsToImport.size,
    ) {
        whenever(csvCredentialConverter.readCsv(any<String>())).thenReturn(Success(numberCredentialsInSource, loginCredentialsToImport))
        testee.onCsvAvailable("")
    }

    private fun creds(
        domain: String? = "example.com",
        username: String? = "username",
        password: String? = "password",
        notes: String? = "notes",
        domainTitle: String? = "example title",
    ): LoginCredentials {
        return LoginCredentials(
            domainTitle = domainTitle,
            domain = domain,
            username = username,
            password = password,
            notes = notes,
        )
    }
}
