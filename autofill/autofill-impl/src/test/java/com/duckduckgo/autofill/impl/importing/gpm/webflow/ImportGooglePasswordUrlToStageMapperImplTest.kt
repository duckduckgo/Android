package com.duckduckgo.autofill.impl.importing.gpm.webflow

import com.duckduckgo.autofill.impl.importing.gpm.feature.AutofillImportPasswordConfigStore
import com.duckduckgo.autofill.impl.importing.gpm.feature.AutofillImportPasswordSettings
import com.duckduckgo.autofill.impl.importing.gpm.feature.UrlMapping
import com.duckduckgo.autofill.impl.importing.gpm.webflow.ImportGooglePasswordUrlToStageMapperImpl.Companion.UNKNOWN
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ImportGooglePasswordUrlToStageMapperImplTest {

    private val importPasswordConfigStore: AutofillImportPasswordConfigStore = mock()

    private val testee = ImportGooglePasswordUrlToStageMapperImpl(importPasswordConfigStore = importPasswordConfigStore)

    @Before
    fun setup() = runTest {
        whenever(importPasswordConfigStore.getConfig()).thenReturn(config())
    }

    @Test
    fun whenUrlIsEmptyStringThenStageIsUnknown() = runTest {
        assertEquals(UNKNOWN, testee.getStage(""))
    }

    @Test
    fun whenUrlIsNullThenStageIsUnknown() = runTest {
        assertEquals(UNKNOWN, testee.getStage(null))
    }

    @Test
    fun whenUrlStartsWithKnownMappingThenValueReturned() = runTest {
        listOf(UrlMapping("key", "https://example.com")).configureMappings()
        assertEquals("key", testee.getStage("https://example.com"))
    }

    @Test
    fun whenUrlMatchesMultipleThenFirstValueReturned() = runTest {
        listOf(
            UrlMapping("key1", "https://example.com"),
            UrlMapping("key2", "https://example.com"),
        ).configureMappings()
        assertEquals("key1", testee.getStage("https://example.com"))
    }

    @Test
    fun whenUrlHasDifferentPrefixThenNotAMatch() = runTest {
        listOf(UrlMapping("key", "https://example.com")).configureMappings()
        assertEquals(UNKNOWN, testee.getStage("example.com"))
    }

    private suspend fun List<UrlMapping>.configureMappings() {
        whenever(importPasswordConfigStore.getConfig()).thenReturn(config().copy(urlMappings = this))
    }

    private fun config(): AutofillImportPasswordSettings {
        return AutofillImportPasswordSettings(
            launchUrlGooglePasswords = "https://example.com",
            canImportFromGooglePasswords = true,
            canInjectJavascript = true,
            javascriptConfigGooglePasswords = "{}",
            urlMappings = emptyList(),
        )
    }
}
