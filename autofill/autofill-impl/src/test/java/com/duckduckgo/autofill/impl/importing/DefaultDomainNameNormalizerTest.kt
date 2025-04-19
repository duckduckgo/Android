package com.duckduckgo.autofill.impl.importing

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.autofill.impl.encoding.UrlUnicodeNormalizerImpl
import com.duckduckgo.autofill.impl.urlmatcher.AutofillDomainNameUrlMatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DefaultDomainNameNormalizerTest {

    private val testee = DefaultDomainNameNormalizer(AutofillDomainNameUrlMatcher(UrlUnicodeNormalizerImpl()))

    @Test
    fun whenInputIsEmptyStringThenEmptyOutput() = runTest {
        val output = testee.normalize("")
        assertEquals("", output)
    }

    @Test
    fun whenInputDomainAlreadyNormalizedThenIncludedInOutput() = runTest {
        val output = testee.normalize("example.com")
        assertEquals("example.com", output)
    }

    @Test
    fun whenInputDomainNotAlreadyNormalizedThenNormalizedAndIncludedInOutput() = runTest {
        val output = testee.normalize("https://example.com/foo/bar")
        assertEquals("example.com", output)
    }

    @Test
    fun whenInputDomainIsNullThenNormalizedToNullDomain() = runTest {
        val output = testee.normalize(null)
        assertEquals(null, output)
    }

    @Test
    fun whenDomainCannotBeNormalizedThenIsIncludedUnmodified() = runTest {
        val output = testee.normalize("unnormalizable")
        assertEquals("unnormalizable", output)
    }
}
