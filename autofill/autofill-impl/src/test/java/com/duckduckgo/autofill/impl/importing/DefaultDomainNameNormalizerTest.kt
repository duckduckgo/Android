package com.duckduckgo.autofill.impl.importing

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.encoding.UrlUnicodeNormalizerImpl
import com.duckduckgo.autofill.impl.urlmatcher.AutofillDomainNameUrlMatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DefaultDomainNameNormalizerTest {

    private val testee = DefaultDomainNameNormalizer(AutofillDomainNameUrlMatcher(UrlUnicodeNormalizerImpl()))

    @Test
    fun whenEmptyInputThenEmptyOutput() = runTest {
        val input = emptyList<LoginCredentials>()
        val output = testee.normalizeDomains(input)
        assertTrue(output.isEmpty())
    }

    @Test
    fun whenInputDomainAlreadyNormalizedThenIncludedInOutput() = runTest {
        val input = listOf(creds(domain = "example.com"))
        val output = testee.normalizeDomains(input)
        assertEquals(1, output.size)
        assertEquals(input.first(), output.first())
    }

    @Test
    fun whenInputDomainNotAlreadyNormalizedThenNormalizedAndIncludedInOutput() = runTest {
        val input = listOf(creds(domain = "https://example.com/foo/bar"))
        val output = testee.normalizeDomains(input)
        assertEquals(1, output.size)
        assertEquals(input.first().copy(domain = "example.com"), output.first())
    }

    @Test
    fun whenInputDomainIsNullThenNormalizedToNullDomain() = runTest {
        val input = listOf(creds(domain = null))
        val output = testee.normalizeDomains(input)
        assertEquals(1, output.size)
        assertEquals(null, output.first().domain)
    }

    @Test
    fun whenDomainCannotBeNormalizedThenIsIncludedUnmodified() = runTest {
        val input = listOf(creds(domain = "unnormalizable"))
        val output = testee.normalizeDomains(input)
        assertEquals("unnormalizable", output.first().domain)
    }

    private fun creds(
        domain: String? = null,
        username: String? = null,
        password: String? = null,
        notes: String? = null,
        domainTitle: String? = null,
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
