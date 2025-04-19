package com.duckduckgo.autofill.impl.importing.gpm.webflow

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.importing.CredentialImporter
import com.duckduckgo.autofill.impl.importing.CsvCredentialConverter
import com.duckduckgo.autofill.impl.importing.CsvCredentialConverter.CsvCredentialImportResult.Error
import com.duckduckgo.autofill.impl.importing.CsvCredentialConverter.CsvCredentialImportResult.Success
import com.duckduckgo.autofill.impl.importing.gpm.feature.AutofillImportPasswordConfigStore
import com.duckduckgo.autofill.impl.importing.gpm.feature.AutofillImportPasswordSettings
import com.duckduckgo.autofill.impl.importing.gpm.webflow.ImportGooglePasswordsWebFlowViewModel.ViewState.LoadStartPage
import com.duckduckgo.autofill.impl.importing.gpm.webflow.ImportGooglePasswordsWebFlowViewModel.ViewState.NavigatingBack
import com.duckduckgo.autofill.impl.importing.gpm.webflow.ImportGooglePasswordsWebFlowViewModel.ViewState.UserCancelledImportFlow
import com.duckduckgo.autofill.impl.importing.gpm.webflow.ImportGooglePasswordsWebFlowViewModel.ViewState.UserFinishedCannotImport
import com.duckduckgo.autofill.impl.importing.gpm.webflow.ImportGooglePasswordsWebFlowViewModel.ViewState.UserFinishedImportFlow
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class ImportGooglePasswordsWebFlowViewModelTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val credentialImporter: CredentialImporter = mock()
    private val csvCredentialConverter: CsvCredentialConverter = mock()
    private val autofillImportConfigStore: AutofillImportPasswordConfigStore = mock()
    private val urlToStageMapper: ImportGooglePasswordUrlToStageMapper = mock()

    private val testee = ImportGooglePasswordsWebFlowViewModel(
        dispatchers = coroutineTestRule.testDispatcherProvider,
        credentialImporter = credentialImporter,
        csvCredentialConverter = csvCredentialConverter,
        autofillImportConfigStore = autofillImportConfigStore,
        urlToStageMapper = urlToStageMapper,
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
