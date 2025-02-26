package com.duckduckgo.autofill.impl.deduper

import android.annotation.SuppressLint
import com.duckduckgo.autofill.api.AutofillFeature
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.username.RealAutofillUsernameComparer
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@SuppressLint("DenyListedApi")
class RealLoginDeduplicatorUsernameAndPasswordMatcherTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()
    private val autofillFeature = FakeFeatureToggleFactory.create(AutofillFeature::class.java)
    private val usernameComparer = RealAutofillUsernameComparer(autofillFeature, coroutineTestRule.testDispatcherProvider)
    private val testee = RealAutofillDeduplicationUsernameAndPasswordMatcher(usernameComparer)

    @Test
    fun whenEmptyListInThenEmptyListOut() = runTest {
        val input = emptyList<LoginCredentials>()
        val output = testee.groupDuplicateCredentials(input)
        assertTrue(output.isEmpty())
    }

    @Test
    fun whenSingleEntryInThenSingleEntryOut() = runTest {
        val input = listOf(
            creds("username", "password"),
        )
        val output = testee.groupDuplicateCredentials(input)
        assertEquals(1, output.size)
    }

    @Test
    fun whenMultipleEntriesWithNoDuplicationAtAllThenNumberOfGroupsReturnedMatchesNumberOfEntriesInputted() = runTest {
        val input = listOf(
            creds("username_a", "password_x"),
            creds("username_b", "password_y"),
            creds("username_c", "password_z"),
        )
        val output = testee.groupDuplicateCredentials(input)
        assertEquals(3, output.size)
    }

    @Test
    fun whenEntriesMatchOnUsernameButNotPasswordThenNotGrouped() = runTest {
        val input = listOf(
            creds("username", "password_x"),
            creds("username", "password_y"),
        )
        val output = testee.groupDuplicateCredentials(input)
        assertEquals(2, output.size)
    }

    @Test
    fun whenEntriesMatchOnPasswordButNotUsernameThenNotGrouped() = runTest {
        val input = listOf(
            creds("username_a", "password"),
            creds("username_b", "password"),
        )
        val output = testee.groupDuplicateCredentials(input)
        assertEquals(2, output.size)
    }

    @Test
    fun whenEntriesMatchOnUsernameAndPasswordThenGrouped() = runTest {
        val input = listOf(
            creds("username", "password"),
            creds("username", "password"),
        )
        val output = testee.groupDuplicateCredentials(input)
        assertEquals(1, output.size)
    }

    @Test
    fun whenEntriesMatchOnUsernameWithDifferentCasesAndPasswordThenGrouped() = runTest {
        val input = listOf(
            creds("username", "password"),
            creds("USERNAME", "password"),
        )

        assertEquals(1, testee.groupDuplicateCredentials(input).size)

        // test with feature flag disabled
        autofillFeature.ignoreCaseOnUsernameComparisons().setRawStoredState(State(enable = false))
        assertEquals(2, testee.groupDuplicateCredentials(input).size)
    }

    private fun creds(username: String, password: String): LoginCredentials {
        return LoginCredentials(username = username, password = password, domain = "domain")
    }
}
