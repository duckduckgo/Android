package com.duckduckgo.autofill.impl.importing

import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.test.FileUtilities
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class GooglePasswordManagerCsvPasswordParserTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val testee = GooglePasswordManagerCsvPasswordParser(
        dispatchers = coroutineTestRule.testDispatcherProvider,
    )

    @Test
    fun whenEmptyStringThenNoPasswords() = runTest {
        val passwords = testee.parseCsv("")
        assertEquals(0, passwords.size)
    }

    @Test
    fun whenHeaderRowOnlyThenNoPasswords() = runTest {
        val csv = "gpm_import_header_row_only".readFile()
        val passwords = testee.parseCsv(csv)
        assertEquals(0, passwords.size)
    }

    @Test
    fun whenHeaderRowHasUnknownFieldThenNoPasswords() = runTest {
        val csv = "gpm_import_header_row_unknown_field".readFile()
        val passwords = testee.parseCsv(csv)
        assertEquals(0, passwords.size)
    }

    @Test
    fun whenHeadersRowAndOnePasswordRowThen1Password() = runTest {
        val csv = "gpm_import_one_valid_basic_password".readFile()
        val passwords = testee.parseCsv(csv)
        assertEquals(1, passwords.size)
        passwords.first().verifyMatchesCreds1()
    }

    @Test
    fun whenHeadersRowAndTwoDifferentPasswordsThen2Passwords() = runTest {
        val csv = "gpm_import_two_valid_basic_passwords".readFile()
        val passwords = testee.parseCsv(csv)
        assertEquals(2, passwords.size)
        passwords[0].verifyMatchesCreds1()
        passwords[1].verifyMatchesCreds2()
    }

    @Test
    fun whenTwoIdenticalPasswordsThen2Passwords() = runTest {
        val csv = "gpm_import_two_valid_identical_passwords".readFile()
        val passwords = testee.parseCsv(csv)
        assertEquals(2, passwords.size)
        passwords[0].verifyMatchesCreds1()
        passwords[1].verifyMatchesCreds1()
    }

    @Test
    fun whenPasswordContainsACommaThenIsParsedSuccessfully() = runTest {
        val csv = "gpm_import_password_has_a_comma".readFile()
        val passwords = testee.parseCsv(csv)

        assertEquals(1, passwords.size)
        val expected = LoginCredentials(
            domain = "https://example.com",
            domainTitle = "example.com",
            username = "user",
            password = "password, a comma it has",
            notes = "notes",
        )
        passwords.first().verifyMatches(expected)
    }

    @Test
    fun whenPasswordContainsOtherSpecialCharactersThenIsParsedSuccessfully() = runTest {
        val csv = "gpm_import_password_has_special_characters".readFile()
        val passwords = testee.parseCsv(csv)

        assertEquals(1, passwords.size)
        val expected = creds1.copy(password = "p\$ssw0rd`\"[]'\\")
        passwords.first().verifyMatches(expected)
    }

    @Test
    fun whenNotesIsEmptyThenIsParsedSuccessfully() = runTest {
        val csv = "gpm_import_missing_notes".readFile()
        val passwords = testee.parseCsv(csv)

        assertEquals(1, passwords.size)
        passwords.first().verifyMatches(creds1.copy(notes = null))
    }

    @Test
    fun whenUsernameIsEmptyThenIsParsedSuccessfully() = runTest {
        val csv = "gpm_import_missing_username".readFile()
        val passwords = testee.parseCsv(csv)

        assertEquals(1, passwords.size)
        passwords.first().verifyMatches(creds1.copy(username = null))
    }

    @Test
    fun whenPasswordIsEmptyThenIsParsedSuccessfully() = runTest {
        val csv = "gpm_import_missing_password".readFile()
        val passwords = testee.parseCsv(csv)

        assertEquals(1, passwords.size)
        passwords.first().verifyMatches(creds1.copy(password = null))
    }

    @Test
    fun whenTitleIsEmptyThenIsParsedSuccessfully() = runTest {
        val csv = "gpm_import_missing_title".readFile()
        val passwords = testee.parseCsv(csv)

        assertEquals(1, passwords.size)
        passwords.first().verifyMatches(creds1.copy(domainTitle = null))
    }

    private fun LoginCredentials.verifyMatchesCreds1() = verifyMatches(creds1)
    private fun LoginCredentials.verifyMatchesCreds2() = verifyMatches(creds2)

    private fun LoginCredentials.verifyMatches(expected: LoginCredentials) {
        assertEquals(expected.domainTitle, domainTitle)
        assertEquals(expected.domain, domain)
        assertEquals(expected.username, username)
        assertEquals(expected.password, password)
        assertEquals(expected.notes, notes)
    }

    private val creds1 = LoginCredentials(
        domain = "https://example.com",
        domainTitle = "example.com",
        username = "user",
        password = "password",
        notes = "note",
    )

    private val creds2 = LoginCredentials(
        domain = "https://example.net",
        domainTitle = "example.net",
        username = "user2",
        password = "password2",
        notes = "note2",
    )

    private fun String.readFile(): String {
        val fileContents = kotlin.runCatching {
            FileUtilities.loadText(
                GooglePasswordManagerCsvPasswordParserTest::class.java.classLoader!!,
                "csv/autofill/$this.csv",
            )
        }.getOrNull()

        if (fileContents == null) {
            throw IllegalArgumentException("Failed to load specified CSV file: $this")
        }
        return fileContents
    }
}
