package com.duckduckgo.app.browser.aura

import com.duckduckgo.common.test.FileUtilities
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class AuraExperimentListJsonParserImplTest {

    private val testee = AuraExperimentListJsonParserImpl()

    @Test
    fun whenGibberishInputThenReturnsReturnsEmptyPackages() = runTest {
        val result = testee.parseJson("invalid json")
        assertTrue(result.list.isEmpty())
    }

    @Test
    fun whenInstallerListIsMissingFieldThenReturnsEmptyPackages() = runTest {
        val result = testee.parseJson("{}")
        assertTrue(result.list.isEmpty())
    }

    @Test
    fun whenInstallerListIsEmptyThenReturnsEmptyPackages() = runTest {
        val result = testee.parseJson("auraExperiment_emptyList".loadJsonFile())
        assertTrue(result.list.isEmpty())
    }

    @Test
    fun whenInstallerListHasSingleEntryThenReturnsSinglePackage() = runTest {
        val result = testee.parseJson("auraExperiment_singleEntryList".loadJsonFile())
        assertEquals(1, result.list.size)
        assertEquals("a.b.c", result.list[0])
    }

    @Test
    fun whenInstallerListHasMultipleEntriesThenReturnsMultiplePackages() = runTest {
        val result = testee.parseJson("auraExperiment_multipleEntryList".loadJsonFile())
        assertEquals(3, result.list.size)
        assertEquals("a.b.c", result.list[0])
        assertEquals("d.e.f", result.list[1])
        assertEquals("g.h.i", result.list[2])
    }

    private fun String.loadJsonFile(): String {
        return FileUtilities.loadText(
            AuraExperimentListJsonParserImplTest::class.java.classLoader!!,
            "json/$this.json",
        )
    }
}
