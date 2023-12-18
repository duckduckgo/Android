package com.duckduckgo.autofill.impl.deduper

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.encoding.TestUrlUnicodeNormalizer
import com.duckduckgo.autofill.impl.urlmatcher.AutofillDomainNameUrlMatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RealAutofillLoginDeduplicatorTest {
    private val urlMatcher = AutofillDomainNameUrlMatcher(TestUrlUnicodeNormalizer())
    private val matchTypeDetector = RealAutofillDeduplicationMatchTypeDetector(urlMatcher)
    private val testee = RealAutofillLoginDeduplicator(
        usernamePasswordMatcher = RealAutofillDeduplicationUsernameAndPasswordMatcher(),
        bestMatchFinder = RealAutofillDeduplicationBestMatchFinder(
            urlMatcher = urlMatcher,
            matchTypeDetector = matchTypeDetector,
        ),
    )

    @Test
    fun whenEmptyListInThenEmptyListOut() = runTest {
        val result = testee.deduplicate("example.com", emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun whenSingleEntryInThenSingleEntryReturned() {
        val inputList = listOf(
            aLogin("domain", "username", "password"),
        )
        val result = testee.deduplicate("example.com", inputList)
        assertEquals(1, result.size)
    }

    @Test
    fun whenEntriesCompletelyUnrelatedThenNoDeduplication() {
        val inputList = listOf(
            aLogin("domain_A", "username_A", "password_A"),
            aLogin("domain_B", "username_B", "password_B"),
        )
        val result = testee.deduplicate("example.com", inputList)
        assertEquals(2, result.size)
        assertNotNull(result.find { it.domain == "domain_A" })
        assertNotNull(result.find { it.domain == "domain_B" })
    }

    @Test
    fun whenEntriesShareUsernameAndPasswordButNotDomainThenDeduped() {
        val inputList = listOf(
            aLogin("foo.com", "username", "password"),
            aLogin("bar.com", "username", "password"),
        )
        val result = testee.deduplicate("example.com", inputList)
        assertEquals(1, result.size)
    }

    @Test
    fun whenEntriesShareDomainAndUsernameButNotPasswordThenNoDeduplication() {
        val inputList = listOf(
            aLogin("example.com", "username", "123"),
            aLogin("example.com", "username", "xyz"),
        )
        val result = testee.deduplicate("example.com", inputList)
        assertEquals(2, result.size)
        assertNotNull(result.find { it.password == "123" })
        assertNotNull(result.find { it.password == "xyz" })
    }

    @Test
    fun whenEntriesShareDomainAndPasswordButNotUsernameThenNoDeduplication() {
        val inputList = listOf(
            aLogin("example.com", "user_A", "password"),
            aLogin("example.com", "user_B", "password"),
        )
        val result = testee.deduplicate("example.com", inputList)
        assertEquals(2, result.size)
        assertNotNull(result.find { it.username == "user_A" })
        assertNotNull(result.find { it.username == "user_B" })
    }

    @Test
    fun whenEntriesShareMultipleCredentialsWhichArePerfectDomainMatchesThenDeduped() {
        val inputList = listOf(
            aLogin("example.com", "username", "password"),
            aLogin("example.com", "username", "password"),
        )
        val result = testee.deduplicate("example.com", inputList)
        assertEquals(1, result.size)
    }

    @Test
    fun whenEntriesShareMultipleCredentialsWhichArePartialDomainMatchesThenDeduped() {
        val inputList = listOf(
            aLogin("a.example.com", "username", "password"),
            aLogin("b.example.com", "username", "password"),
        )
        val result = testee.deduplicate("example.com", inputList)
        assertEquals(1, result.size)
    }

    @Test
    fun whenEntriesShareMultipleCredentialsWhichAreNotDomainMatchesThenDeduped() {
        val inputList = listOf(
            aLogin("foo.com", "username", "password"),
            aLogin("bar.com", "username", "password"),
        )
        val result = testee.deduplicate("example.com", inputList)
        assertEquals(1, result.size)
    }

    @Test
    fun whenEntriesShareCredentialsAcrossPerfectAndPartialMatchesThenDedupedToPerfectMatch() {
        val inputList = listOf(
            aLogin("example.com", "username", "password"),
            aLogin("a.example.com", "username", "password"),
        )
        val result = testee.deduplicate("example.com", inputList)
        assertEquals(1, result.size)
        assertNotNull(result.find { it.domain == "example.com" })
    }

    @Test
    fun whenEntriesShareCredentialsAcrossPerfectAndNonDomainMatchesThenDedupedToPerfectMatch() {
        val inputList = listOf(
            aLogin("example.com", "username", "password"),
            aLogin("bar.com", "username", "password"),
        )
        val result = testee.deduplicate("example.com", inputList)
        assertEquals(1, result.size)
        assertNotNull(result.find { it.domain == "example.com" })
    }

    @Test
    fun whenEntriesShareCredentialsAcrossPartialAndNonDomainMatchesThenDedupedToPerfectMatch() {
        val inputList = listOf(
            aLogin("a.example.com", "username", "password"),
            aLogin("bar.com", "username", "password"),
        )
        val result = testee.deduplicate("example.com", inputList)
        assertEquals(1, result.size)
        assertNotNull(result.find { it.domain == "a.example.com" })
    }

    @Test
    fun whenEntriesShareCredentialsAcrossPerfectAndPartialAndNonDomainMatchesThenDedupedToPerfectMatch() {
        val inputList = listOf(
            aLogin("a.example.com", "username", "password"),
            aLogin("example.com", "username", "password"),
            aLogin("bar.com", "username", "password"),
        )
        val result = testee.deduplicate("example.com", inputList)
        assertEquals(1, result.size)
        assertNotNull(result.find { it.domain == "example.com" })
    }

    private fun aLogin(domain: String, username: String, password: String): LoginCredentials {
        return LoginCredentials(username = username, password = password, domain = domain)
    }
}
