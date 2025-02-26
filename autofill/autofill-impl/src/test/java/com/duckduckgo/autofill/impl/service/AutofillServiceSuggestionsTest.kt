package com.duckduckgo.autofill.impl.service

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.deduper.AutofillLoginDeduplicator
import com.duckduckgo.autofill.impl.service.mapper.fakes.FakeAppCredentialsProvider
import com.duckduckgo.autofill.impl.service.mapper.fakes.FakeAutofillStore
import com.duckduckgo.autofill.impl.ui.credential.selecting.AutofillSelectCredentialsGrouper
import com.duckduckgo.autofill.impl.ui.credential.selecting.AutofillSelectCredentialsGrouper.Groups
import com.duckduckgo.autofill.noopDeduplicator
import com.duckduckgo.autofill.noopGroupBuilder
import com.duckduckgo.autofill.sync.CredentialsFixtures.amazonCredentials
import com.duckduckgo.autofill.sync.CredentialsFixtures.spotifyCredentials
import com.duckduckgo.autofill.sync.CredentialsFixtures.twitterCredentials
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AutofillServiceSuggestionsTest {
    @get:Rule var coroutineRule = CoroutineTestRule()

    private val fakeAutofillStore = FakeAutofillStore(
        listOf(
            twitterCredentials,
            spotifyCredentials,
            amazonCredentials,
        ),
    )

    private val fakeAppCredentialsProvider = FakeAppCredentialsProvider(
        listOf(
            twitterCredentials,
            spotifyCredentials,
            amazonCredentials,
        ),
    )

    @Test
    fun whenGetSiteSuggestionsThenReturnsSiteSuggestions() = runTest {
        val testee = AutofillServiceSuggestions(
            autofillStore = fakeAutofillStore,
            loginDeduplicator = noopDeduplicator(),
            grouper = noopGroupBuilder(),
            appCredentialProvider = fakeAppCredentialsProvider,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
        )

        val result = testee.getSiteSuggestions(twitterCredentials.domain!!)
        assertEquals(1, result.size)
        assertEquals(twitterCredentials, result.first())
    }

    @Test
    fun whenGetSiteSuggestionsDoesNotHaveResultsThenReturnsEmtpyList() = runTest {
        val testee = AutofillServiceSuggestions(
            autofillStore = fakeAutofillStore,
            loginDeduplicator = noopDeduplicator(),
            grouper = noopGroupBuilder(),
            appCredentialProvider = fakeAppCredentialsProvider,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
        )

        val result = testee.getSiteSuggestions("random.com")
        assertEquals(0, result.size)
    }

    @Test
    fun whenGetAppSuggestionsThenReturnsAppSuggestions() = runTest {
        val testee = AutofillServiceSuggestions(
            autofillStore = fakeAutofillStore,
            loginDeduplicator = noopDeduplicator(),
            grouper = noopGroupBuilder(),
            appCredentialProvider = fakeAppCredentialsProvider,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
        )

        val result = testee.getAppSuggestions(twitterCredentials.domain!!)
        assertEquals(1, result.size)
        assertEquals(twitterCredentials, result.first())
    }

    @Test
    fun whenGetAppSuggestionsDoesNotHaveResultsThenReturnsEmtpyList() = runTest {
        val testee = AutofillServiceSuggestions(
            autofillStore = fakeAutofillStore,
            loginDeduplicator = noopDeduplicator(),
            grouper = noopGroupBuilder(),
            appCredentialProvider = fakeAppCredentialsProvider,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
        )

        val result = testee.getAppSuggestions("random.package")
        assertEquals(0, result.size)
    }

    @Test
    fun whenGetSuggestionsThenGroupedCredentialsAreFlattened() = runTest {
        val grouper = object : AutofillSelectCredentialsGrouper {
            override fun group(
                originalUrl: String,
                unsortedCredentials: List<LoginCredentials>,
            ): Groups {
                return Groups(
                    listOf(twitterCredentials),
                    mapOf(spotifyCredentials.domain!! to listOf(spotifyCredentials)),
                    mapOf(amazonCredentials.domain!! to listOf(amazonCredentials)),
                )
            }
        }

        val testee = AutofillServiceSuggestions(
            autofillStore = fakeAutofillStore,
            loginDeduplicator = noopDeduplicator(),
            grouper = grouper,
            appCredentialProvider = fakeAppCredentialsProvider,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
        )

        testee.getSiteSuggestions(twitterCredentials.domain!!).let {
            assertEquals(3, it.size)
            assertTrue(it.contains(twitterCredentials))
            assertTrue(it.contains(spotifyCredentials))
            assertTrue(it.contains(amazonCredentials))
        }
    }

    @Test
    fun whenGetSiteSuggestionsThenDedupedResultAreProcessed() = runTest {
        val testee = AutofillServiceSuggestions(
            autofillStore = FakeAutofillStore(
                listOf(
                    twitterCredentials,
                    twitterCredentials.copy(username = "other"),
                    twitterCredentials.copy(username = "another"),
                ),
            ),
            loginDeduplicator = object : AutofillLoginDeduplicator {
                override suspend fun deduplicate(
                    originalUrl: String,
                    logins: List<LoginCredentials>,
                ): List<LoginCredentials> {
                    return listOf(logins.first())
                }
            },
            grouper = noopGroupBuilder(),
            appCredentialProvider = fakeAppCredentialsProvider,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
        )

        val result = testee.getSiteSuggestions(twitterCredentials.domain!!)

        assertEquals(1, result.size)
    }
}
