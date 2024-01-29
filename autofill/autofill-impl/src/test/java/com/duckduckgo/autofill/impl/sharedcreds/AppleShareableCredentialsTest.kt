package com.duckduckgo.autofill.impl.sharedcreds

import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.encoding.TestUrlUnicodeNormalizer
import com.duckduckgo.autofill.impl.sharedcreds.SharedCredentialsParser.SharedCredentialConfig
import com.duckduckgo.autofill.impl.store.InternalAutofillStore
import com.duckduckgo.autofill.impl.urlmatcher.AutofillDomainNameUrlMatcher
import com.duckduckgo.autofill.impl.urlmatcher.AutofillUrlMatcher.ExtractedUrlParts
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class AppleShareableCredentialsTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val jsonParser: SharedCredentialsParser = mock()
    private val shareableCredentialsUrlGenerator: ShareableCredentialsUrlGenerator = mock()
    private val autofillStore: InternalAutofillStore = mock()
    private val autofillUrlMatcher = AutofillDomainNameUrlMatcher(TestUrlUnicodeNormalizer())

    private val testee = AppleShareableCredentials(
        jsonParser = jsonParser,
        dispatchers = coroutineTestRule.testDispatcherProvider,
        shareableCredentialsUrlGenerator = shareableCredentialsUrlGenerator,
        autofillStore = autofillStore,
        autofillUrlMatcher = autofillUrlMatcher,
        appCoroutineScope = coroutineTestRule.testScope,
    )

    @Test
    fun whenNoMatchingShareableCredentialsFoundThenEmptyListReturned() = runTest {
        configureMatchingLogins(emptyList())
        val list = testee.shareableCredentials("example.com")
        assertTrue(list.isEmpty())
    }

    @Test
    fun whenSingleMatchingShareableCredentialFoundThenReturned() = runTest {
        configureMatchingLogins(listOf("example.com"))
        val list = testee.shareableCredentials("example.com")
        assertEquals(1, list.size)
    }

    @Test
    fun whenMultipleDifferentMatchingShareableCredentialsFoundThenAllReturned() = runTest {
        configureMatchingLogins(
            listOf(
                "example.com",
                "foo.com",
                "bar.com",
            ),
        )
        val list = testee.shareableCredentials("example.com")
        assertEquals(3, list.size)
    }

    @Test
    fun whenSameLoginFoundMultipleTimesThenOnlyIncludedOnce() = runTest {
        configureMatchingLogins(
            listOf(
                "example.com",
                "example.com",
            ),
        )
        val list = testee.shareableCredentials("example.com")
        assertEquals(1, list.size)
    }

    private suspend fun configureMatchingLogins(domains: List<String>) {
        whenever(shareableCredentialsUrlGenerator.generateShareableUrls(any(), anyOrNull())).thenReturn(
            domains.map { urlParts(it) },
        )
        whenever(autofillStore.getCredentials(any())).thenReturn(
            domains.map { login(it) },
        )
    }

    private fun emptyConfig(): SharedCredentialConfig {
        return SharedCredentialConfig(omnidirectionalRules = emptyList(), unidirectionalRules = emptyList())
    }

    private suspend fun SharedCredentialConfig.use() {
        whenever(jsonParser.read()).thenReturn(this)
        whenever(shareableCredentialsUrlGenerator.generateShareableUrls(any(), any())).thenReturn(emptyList())
    }

    private fun login(domain: String): LoginCredentials {
        return LoginCredentials(domain = domain, username = "username", password = "password")
    }

    private fun urlParts(eTldPlus1: String): ExtractedUrlParts {
        return ExtractedUrlParts(eTldPlus1 = eTldPlus1, userFacingETldPlus1 = eTldPlus1, subdomain = null, port = null)
    }
}
