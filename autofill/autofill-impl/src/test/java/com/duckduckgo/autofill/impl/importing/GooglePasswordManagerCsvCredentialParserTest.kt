package com.duckduckgo.autofill.impl.importing

import com.duckduckgo.autofill.impl.importing.CsvCredentialParser.ParseResult.Success
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.test.FileUtilities
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class GooglePasswordManagerCsvCredentialParserTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val testee = GooglePasswordManagerCsvCredentialParser(
        dispatchers = coroutineTestRule.testDispatcherProvider,
    )

    @Test
    fun whenEmptyStringThenNoPasswords() = runTest {
        val result = testee.parseCsv("")
        assertTrue(result is CsvCredentialParser.ParseResult.Error)
    }

    @Test
    fun whenHeaderRowOnlyThenNoCredentials() = runTest {
        val csv = "gpm_import_header_row_only".readFile()
        with(testee.parseCsv(csv) as Success) {
            assertEquals(0, credentials.size)
        }
    }

    @Test
    fun whenHeaderRowHasUnknownFieldThenNoCredentials() = runTest {
        val csv = "gpm_import_header_row_unknown_field".readFile()
        val result = testee.parseCsv(csv)
        assertTrue(result is CsvCredentialParser.ParseResult.Error)
    }

    @Test
    fun whenHeadersRowAndOneCredentialsRowThen1Credential() = runTest {
        val csv = "gpm_import_one_valid_basic_password".readFile()
        with(testee.parseCsv(csv) as Success) {
            assertEquals(1, credentials.size)
            credentials.first().verifyMatchesCreds1()
        }
    }

    @Test
    fun whenHeadersRowAndTwoDifferentPasswordsThen2Passwords() = runTest {
        val csv = "gpm_import_two_valid_basic_passwords".readFile()
        with(testee.parseCsv(csv) as Success) {
            assertEquals(2, credentials.size)
            credentials[0].verifyMatchesCreds1()
            credentials[1].verifyMatchesCreds2()
        }
    }

    @Test
    fun whenTwoIdenticalPasswordsThen2Passwords() = runTest {
        val csv = "gpm_import_two_valid_identical_passwords".readFile()
        with(testee.parseCsv(csv) as Success) {
            assertEquals(2, credentials.size)
            credentials[0].verifyMatchesCreds1()
            credentials[1].verifyMatchesCreds1()
        }
    }

    @Test
    fun whenPasswordContainsACommaThenIsParsedSuccessfully() = runTest {
        val csv = "gpm_import_password_has_a_comma".readFile()
        with(testee.parseCsv(csv) as Success) {
            assertEquals(1, credentials.size)
            val expected = GoogleCsvLoginCredential(
                url = "https://example.com",
                title = "example.com",
                username = "user",
                password = "password, a comma it has",
                notes = "notes",
            )
            credentials.first().verifyMatches(expected)
        }
    }

    @Test
    fun whenPasswordContainsOtherSpecialCharactersThenIsParsedSuccessfully() = runTest {
        val csv = "gpm_import_password_has_special_characters".readFile()
        with(testee.parseCsv(csv) as Success) {
            assertEquals(1, credentials.size)
            val expected = creds1.copy(password = "p\$ssw0rd`\"[]'\\")
            credentials.first().verifyMatches(expected)
        }
    }

    @Test
    fun whenNotesIsEmptyThenIsParsedSuccessfully() = runTest {
        val csv = "gpm_import_missing_notes".readFile()
        with(testee.parseCsv(csv) as Success) {
            assertEquals(1, credentials.size)
            credentials.first().verifyMatches(creds1.copy(notes = null))
        }
    }

    @Test
    fun whenUsernameIsEmptyThenIsParsedSuccessfully() = runTest {
        val csv = "gpm_import_missing_username".readFile()
        with(testee.parseCsv(csv) as Success) {
            assertEquals(1, credentials.size)
            credentials.first().verifyMatches(creds1.copy(username = null))
        }
    }

    @Test
    fun whenPasswordIsEmptyThenIsParsedSuccessfully() = runTest {
        val csv = "gpm_import_missing_password".readFile()
        with(testee.parseCsv(csv) as Success) {
            assertEquals(1, credentials.size)
            credentials.first().verifyMatches(creds1.copy(password = null))
        }
    }

    @Test
    fun whenTitleIsEmptyThenIsParsedSuccessfully() = runTest {
        val csv = "gpm_import_missing_title".readFile()
        with(testee.parseCsv(csv) as Success) {
            assertEquals(1, credentials.size)
            credentials.first().verifyMatches(creds1.copy(title = null))
        }
    }

    @Test
    fun whenDomainIsEmptyThenIsParsedSuccessfully() = runTest {
        val csv = "gpm_import_missing_domain".readFile()
        with(testee.parseCsv(csv) as Success) {
            assertEquals(1, credentials.size)
            credentials.first().verifyMatches(creds1.copy(url = null))
        }
    }

    private fun GoogleCsvLoginCredential.verifyMatchesCreds1() = verifyMatches(creds1)
    private fun GoogleCsvLoginCredential.verifyMatchesCreds2() = verifyMatches(creds2)

    private fun GoogleCsvLoginCredential.verifyMatches(expected: GoogleCsvLoginCredential) {
        assertEquals(expected.title, title)
        assertEquals(expected.url, url)
        assertEquals(expected.username, username)
        assertEquals(expected.password, password)
        assertEquals(expected.notes, notes)
    }

    private val creds1 = GoogleCsvLoginCredential(
        url = "https://example.com",
        title = "example.com",
        username = "user",
        password = "password",
        notes = "note",
    )

    private val creds2 = GoogleCsvLoginCredential(
        url = "https://example.net",
        title = "example.net",
        username = "user2",
        password = "password2",
        notes = "note2",
    )

    private fun String.readFile(): String {
        val fileContents = kotlin.runCatching {
            FileUtilities.loadText(
                GooglePasswordManagerCsvCredentialParserTest::class.java.classLoader!!,
                "csv/autofill/$this.csv",
            )
        }.getOrNull()

        if (fileContents == null) {
            throw IllegalArgumentException("Failed to load specified CSV file: $this")
        }
        return fileContents
    }
}
