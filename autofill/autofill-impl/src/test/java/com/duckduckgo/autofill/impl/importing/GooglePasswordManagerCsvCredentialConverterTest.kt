package com.duckduckgo.autofill.impl.importing

import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.importing.CsvCredentialConverter.CsvCredentialImportResult
import com.duckduckgo.autofill.impl.importing.CsvCredentialParser.ParseResult
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class GooglePasswordManagerCsvCredentialConverterTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val parser: CsvCredentialParser = mock()
    private val fileReader: CsvFileReader = mock()
    private val passthroughValidator = object : ImportedCredentialValidator {
        override fun isValid(loginCredentials: GoogleCsvLoginCredential): Boolean = true
    }
    private val passthroughDomainNormalizer = object : DomainNameNormalizer {
        override suspend fun normalize(unnormalizedUrl: String?): String? {
            return unnormalizedUrl
        }
    }
    private val blobDecoder: GooglePasswordBlobDecoder = mock()
    private val passthroughExistingCredentialMatchDetector = object : ExistingCredentialMatchDetector {
        override suspend fun filterExistingCredentials(newCredentials: List<LoginCredentials>): List<LoginCredentials> {
            return newCredentials
        }
    }

    private val testee = GooglePasswordManagerCsvCredentialConverter(
        parser = parser,
        fileReader = fileReader,
        credentialValidator = passthroughValidator,
        domainNameNormalizer = passthroughDomainNormalizer,
        dispatchers = coroutineTestRule.testDispatcherProvider,
        blobDecoder = blobDecoder,
        existingCredentialMatchDetector = passthroughExistingCredentialMatchDetector,
    )

    @Before
    fun before() = runTest {
        whenever(blobDecoder.decode(any())).thenReturn("")
    }

    @Test
    fun whenBlobDecodedIntoEmptyListThenSuccessReturned() = runTest {
        val result = configureParseResult(emptyList())
        assertEquals(0, result.numberCredentialsInSource)
        assertEquals(0, result.loginCredentialsToImport.size)
    }

    @Test
    fun whenBlobDecodedIntoSingleItemNotADuplicateListThenSuccessReturned() = runTest {
        val importSource = listOf(creds())
        val result = configureParseResult(importSource)
        assertEquals(1, result.numberCredentialsInSource)
        assertEquals(1, result.loginCredentialsToImport.size)
    }

    @Test
    fun whenFailureToParseThen() = runTest {
        whenever(parser.parseCsv(any())).thenThrow(RuntimeException())
        testee.readCsv("") as CsvCredentialImportResult.Error
    }

    private suspend fun configureParseResult(passwords: List<GoogleCsvLoginCredential>): CsvCredentialImportResult.Success {
        whenever(parser.parseCsv(any())).thenReturn(ParseResult.Success(passwords))
        return testee.readCsv("") as CsvCredentialImportResult.Success
    }

    private fun creds(
        url: String? = "example.com",
        username: String? = "username",
        password: String? = "password",
        notes: String? = "notes",
        title: String? = "example title",
    ): GoogleCsvLoginCredential {
        return GoogleCsvLoginCredential(
            title = title,
            url = url,
            username = username,
            password = password,
            notes = notes,
        )
    }
}
