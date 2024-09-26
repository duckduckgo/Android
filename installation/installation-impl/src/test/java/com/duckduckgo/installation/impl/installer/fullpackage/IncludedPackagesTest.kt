package com.duckduckgo.installation.impl.installer.fullpackage

import com.duckduckgo.installation.impl.installer.fullpackage.InstallSourceFullPackageStore.IncludedPackages
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IncludedPackagesTest {

    @Test
    fun whenEmptyThenDoesNotContainWildcard() {
        val list = IncludedPackages(emptyList())
        assertFalse(list.hasWildcard())
    }

    @Test
    fun whenHasEntriesButNoWildcardThenDoesNotContainWildcard() {
        val list = IncludedPackages(
            listOf(
                "not a wildcard",
                "also not a wildcard",
            ),
        )
        assertFalse(list.hasWildcard())
    }

    @Test
    fun whenHasMultipleEntriesAndOneIsWildcardEntryThenDoesContainWildcard() {
        val list = IncludedPackages(
            listOf(
                "not a wildcard",
                "*",
                "also not a wildcard",
            ),
        )
        assertTrue(list.hasWildcard())
    }

    @Test
    fun whenHasSingleWildcardEntryThenDoesContainWildcard() {
        val list = IncludedPackages(listOf("*"))
        assertTrue(list.hasWildcard())
    }
}
