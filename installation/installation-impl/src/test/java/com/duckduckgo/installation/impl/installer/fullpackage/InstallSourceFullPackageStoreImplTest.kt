package com.duckduckgo.installation.impl.installer.fullpackage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.installation.impl.installer.fullpackage.InstallSourceFullPackageStore.IncludedPackages
import com.duckduckgo.installation.impl.installer.fullpackage.feature.InstallSourceFullPackageListJsonParser
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class InstallSourceFullPackageStoreImplTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val mockInstallSourceFullPackageListJsonParser: InstallSourceFullPackageListJsonParser = mock()
    private val temporaryFolder = TemporaryFolder.builder().assureDeletion().build().also { it.create() }

    private val testDataStore: DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = coroutineTestRule.testScope,
            produceFile = { temporaryFolder.newFile("temp.preferences_pb") },
        )

    private val testee = InstallSourceFullPackageStoreImpl(
        dispatchers = coroutineTestRule.testDispatcherProvider,
        jsonParser = mockInstallSourceFullPackageListJsonParser,
        dataStore = testDataStore,
    )

    @Test
    fun whenPackagesIsEmptyThenEmptySetReturned() = runTest {
        configurePackages(emptySet())
        val result = testee.getInstallSourceFullPackages()
        assertTrue(result.list.isEmpty())
    }

    @Test
    fun whenPackageHasSingleEntryThenCorrectSetReturned() = runTest {
        configurePackages(setOf("a.b.c"))
        val result = testee.getInstallSourceFullPackages()
        assertEquals(1, result.list.size)
    }

    @Test
    fun whenPackageHasMultipleUniqueEntriesThenCorrectSetReturned() = runTest {
        configurePackages(setOf("some value", "*", "not a wildcard"))
        val result = testee.getInstallSourceFullPackages()
        assertEquals(3, result.list.size)
    }

    @Test
    fun whenPackageHasMultipleEntriesWithDuplicatesThenCorrectSetReturned() = runTest {
        configurePackages(setOf("this is a duplicate", "this is a duplicate", "unique"))
        val result = testee.getInstallSourceFullPackages()
        assertEquals(2, result.list.size)
    }

    @Test
    fun whenUpdateFirstCalledThenListIsPersisted() = runTest {
        val someJson = "{}"
        whenever(mockInstallSourceFullPackageListJsonParser.parseJson(someJson)).thenReturn(IncludedPackages(listOf("a.b.c")))
        testee.updateInstallSourceFullPackages(someJson)
        assertTrue(testee.getInstallSourceFullPackages().list.contains("a.b.c"))
    }

    @Test
    fun whenUpdateCalledAgainWithEmptyListThenListIsPersisted() = runTest {
        val someJson = "{}"
        whenever(mockInstallSourceFullPackageListJsonParser.parseJson(someJson)).thenReturn(IncludedPackages(listOf("a.b.c")))
        testee.updateInstallSourceFullPackages(someJson)

        whenever(mockInstallSourceFullPackageListJsonParser.parseJson(someJson)).thenReturn(IncludedPackages(emptyList()))
        testee.updateInstallSourceFullPackages(someJson)

        assertTrue(testee.getInstallSourceFullPackages().list.isEmpty())
    }

    @Test
    fun whenUpdateCalledAgainWithDifferentListThenListIsPersisted() = runTest {
        val someJson = "{}"
        whenever(mockInstallSourceFullPackageListJsonParser.parseJson(someJson)).thenReturn(IncludedPackages(listOf("a.b.c")))
        testee.updateInstallSourceFullPackages(someJson)

        whenever(mockInstallSourceFullPackageListJsonParser.parseJson(someJson)).thenReturn(IncludedPackages(listOf("d.e.f")))
        testee.updateInstallSourceFullPackages(someJson)

        assertEquals("d.e.f", testee.getInstallSourceFullPackages().list[0])
    }

    private suspend fun configurePackages(set: Set<String>) {
        testDataStore.edit { it[InstallSourceFullPackageStoreImpl.packageInstallersKey] = set }
    }
}
