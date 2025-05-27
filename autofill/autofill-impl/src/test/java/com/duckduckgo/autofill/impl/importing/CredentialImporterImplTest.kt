package com.duckduckgo.autofill.impl.importing

import app.cash.turbine.test
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.importing.CredentialImporter.ImportResult
import com.duckduckgo.autofill.impl.store.InternalAutofillStore
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class CredentialImporterImplTest {

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private val autofillStore: InternalAutofillStore = mock()
    private val dispatchers = coroutinesTestRule.testDispatcherProvider
    private val appCoroutineScope: CoroutineScope = coroutinesTestRule.testScope

    private val credentialsAlreadyInDb = mutableListOf<LoginCredentials>()

    private val testee = CredentialImporterImpl(
        autofillStore = autofillStore,
        dispatchers = dispatchers,
        appCoroutineScope = appCoroutineScope,
    )

    @Before
    fun before() = runTest {
        whenever(autofillStore.bulkInsert(any())).thenAnswer { invocation ->
            val credentialsToInsert = invocation.getArgument<List<LoginCredentials>>(0)

            // Filter out the credentials that are already in the database
            credentialsToInsert.filterNot { newCredential ->
                credentialsAlreadyInDb.any { existingCredential ->
                    existingCredential == newCredential // Adjust this comparison as needed
                }
            }
        }
    }

    @Test
    fun whenImportingEmptyListThenResultIsCorrect() = runTest {
        listOf<LoginCredentials>().import()
        assertResult(numberSkippedExpected = 0, importListSizeExpected = 0)
    }

    @Test
    fun whenImportingSingleItemNotADuplicateThenResultIsCorrect() = runTest {
        listOf(creds()).import()
        assertResult(numberSkippedExpected = 0, importListSizeExpected = 1)
    }

    @Test
    fun whenImportingMultipleItemsNoDuplicatesThenResultIsCorrect() = runTest {
        listOf(
            creds(username = "username1"),
            creds(username = "username2"),
        ).import()
        assertResult(numberSkippedExpected = 0, importListSizeExpected = 2)
    }

    @Test
    fun whenImportingSingleItemWhichIsADuplicateThenResultIsCorrect() = runTest {
        val duplicatedLogin = creds(username = "username")
        duplicatedLogin.treatAsDuplicate()
        listOf(duplicatedLogin).import()
        assertResult(numberSkippedExpected = 1, importListSizeExpected = 0)
    }

    @Test
    fun whenImportingMultipleItemsAllDuplicatesThenResultIsCorrect() = runTest {
        val duplicatedLogin1 = creds(username = "username1")
        val duplicatedLogin2 = creds(username = "username2")
        duplicatedLogin1.treatAsDuplicate()
        duplicatedLogin2.treatAsDuplicate()

        listOf(duplicatedLogin1, duplicatedLogin2).import()
        assertResult(numberSkippedExpected = 2, importListSizeExpected = 0)
    }

    @Test
    fun whenImportingMultipleItemsSomeDuplicatesThenResultIsCorrect() = runTest {
        val duplicatedLogin1 = creds(username = "username1")
        val duplicatedLogin2 = creds(username = "username2")
        val notADuplicate = creds(username = "username3")
        duplicatedLogin1.treatAsDuplicate()
        duplicatedLogin2.treatAsDuplicate()

        listOf(duplicatedLogin1, duplicatedLogin2, notADuplicate).import()
        assertResult(numberSkippedExpected = 2, importListSizeExpected = 1)
    }

    @Test
    fun whenAllPasswordsSkippedAlreadyBeforeImportThenResultIsCorrect() = runTest {
        listOf<LoginCredentials>().import(originalListSize = 3)
        assertResult(numberSkippedExpected = 3, importListSizeExpected = 0)
    }

    @Test
    fun whenSomePasswordsSkippedAlreadyBeforeImportThenResultIsCorrect() = runTest {
        listOf(creds()).import(originalListSize = 3)
        assertResult(numberSkippedExpected = 2, importListSizeExpected = 1)
    }

    @Test
    fun whenImportingCredentialsSuccessfullyThenHasEverImportedPasswordsIsSetToTrue() = runTest {
        listOf(creds()).import()
        verify(autofillStore).hasEverImportedPasswords = true
    }

    @Test
    fun whenImportingNoCredentialsThenHasEverImportedPasswordsIsNotSet() = runTest {
        listOf<LoginCredentials>().import()
        verify(autofillStore, never()).hasEverImportedPasswords = true
    }

    @Test
    fun whenImportingOnlyDuplicatesThenHasEverImportedPasswordsIsNotSet() = runTest {
        val duplicatedLogin = creds(username = "username")
        duplicatedLogin.treatAsDuplicate()
        listOf(duplicatedLogin).import()
        verify(autofillStore, never()).hasEverImportedPasswords = true
    }

    private suspend fun List<LoginCredentials>.import(originalListSize: Int = this.size) {
        testee.import(this, originalListSize)
    }

    private suspend fun assertResult(
        numberSkippedExpected: Int,
        importListSizeExpected: Int,
    ) {
        testee.getImportStatus().test {
            with(awaitItem() as ImportResult.Finished) {
                assertEquals("Wrong number of duplicates in result", numberSkippedExpected, numberSkipped)
                assertEquals("Wrong import size in result", importListSizeExpected, savedCredentials)
            }
        }
    }

    private fun creds(
        id: Long? = null,
        domain: String? = "example.com",
        username: String? = "username",
        password: String? = "password",
        notes: String? = "notes",
        domainTitle: String? = "example title",
    ): LoginCredentials {
        return LoginCredentials(
            id = id,
            domainTitle = domainTitle,
            domain = domain,
            username = username,
            password = password,
            notes = notes,
        )
    }

    private fun LoginCredentials.treatAsDuplicate() {
        credentialsAlreadyInDb.add(this)
    }
}
