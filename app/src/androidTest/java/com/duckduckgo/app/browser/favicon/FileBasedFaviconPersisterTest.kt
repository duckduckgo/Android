/*
 * Copyright (c) 2020 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:Suppress("RemoveExplicitTypeArguments", "SameParameterValue")

package com.duckduckgo.app.browser.favicon

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.global.file.FileDeleter
import com.duckduckgo.browser.feature.toggles.AndroidBrowserConfigFeature
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.sha256
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File
import java.io.FileOutputStream

@SuppressLint("DenyListedApi")
class FileBasedFaviconPersisterTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private lateinit var testee: FileBasedFaviconPersister
    private val mockFileDeleter: FileDeleter = mock()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val testDirectory = "test"
    private val secondaryTestDirectory = "otherTest"
    private val subFolder = "subFolder"
    private val domain = "www.example.com"
    private val fakeBrowserConfigFeature = FakeFeatureToggleFactory.create(AndroidBrowserConfigFeature::class.java)

    @Before
    fun setup() = runBlocking {
        fakeBrowserConfigFeature.storeFaviconSuspend().setRawStoredState(Toggle.State(true))
        whenever(mockFileDeleter.deleteContents(any(), any())).thenReturn(Result.success(Unit))
        testee = FileBasedFaviconPersister(context, mockFileDeleter, fakeBrowserConfigFeature, coroutineRule.testDispatcherProvider)
    }

    @After
    fun after() = runTest {
        deleteTestFolders()
    }

    @Test
    fun whenDeleteAllCalledThenEntireDirectoryDeleted() = runTest {
        testee.deleteAll(testDirectory)
        val captor = argumentCaptor<File>()

        verify(mockFileDeleter).deleteDirectory(captor.capture())
        verifyDirectoryUse(captor.firstValue.absolutePath, testDirectory)
    }

    @Test
    fun whenFaviconFileReturnedThenCorrectDirectoryUsed() = runTest {
        createNewFile()
        val file = testee.faviconFile(testDirectory, subFolder, domain)

        assertNotNull(file)
        verifyDirectoryUse(file!!.absolutePath, testDirectory)
        verifyExpectedBaseDirectoryUsed(file.absolutePath, testDirectory)
    }

    @Test
    fun whenFaviconFileIsForTempDirectoryThenCacheDirUsed() = runTest {
        createNewFile(FileBasedFaviconPersister.FAVICON_TEMP_DIR)
        val file = testee.faviconFile(FileBasedFaviconPersister.FAVICON_TEMP_DIR, subFolder, domain)

        assertNotNull(file)
        assertTrue(file!!.absolutePath.startsWith(context.cacheDir.absolutePath))
    }

    @Test
    fun whenFaviconFileIsForPersistedDirectoryThenNonCacheDirUsed() = runTest {
        createNewFile(FileBasedFaviconPersister.FAVICON_PERSISTED_DIR)
        val file = testee.faviconFile(FileBasedFaviconPersister.FAVICON_PERSISTED_DIR, subFolder, domain)

        assertNotNull(file)
        assertTrue(file!!.absolutePath.startsWith(context.filesDir.absolutePath))
        assertFalse(file.absolutePath.startsWith(context.cacheDir.absolutePath))
    }

    @Test
    fun whenLegacyCacheFaviconExistsThenItIsMovedToFilesDirOnFirstAccess() = runTest {
        val legacyFile = createLegacyCacheFile(FileBasedFaviconPersister.FAVICON_PERSISTED_DIR, content = "legacy")

        val file = testee.faviconFile(FileBasedFaviconPersister.FAVICON_PERSISTED_DIR, subFolder, domain)

        assertNotNull(file)
        assertTrue(file!!.absolutePath.startsWith(context.filesDir.absolutePath))
        assertEquals("legacy", file.readText())
        assertFalse(legacyFile.exists())
        assertFalse(File(context.cacheDir, FileBasedFaviconPersister.FAVICON_PERSISTED_DIR).exists())
    }

    @Test
    fun whenLegacyCacheFaviconAndNewFaviconBothExistThenNewOneIsKept() = runTest {
        createLegacyCacheFile(FileBasedFaviconPersister.FAVICON_PERSISTED_DIR, content = "legacy")
        createNewFile(FileBasedFaviconPersister.FAVICON_PERSISTED_DIR, content = "new")

        val file = testee.faviconFile(FileBasedFaviconPersister.FAVICON_PERSISTED_DIR, subFolder, domain)

        assertNotNull(file)
        assertEquals("new", file!!.readText())
        assertFalse(File(context.cacheDir, FileBasedFaviconPersister.FAVICON_PERSISTED_DIR).exists())
    }

    @Test
    fun whenLegacyCacheWidgetPlaceholderExistsThenItIsMovedToFilesDirOnFirstAccess() = runTest {
        createLegacyCacheFile(FileBasedFaviconPersister.FAVICON_WIDGET_PLACEHOLDERS_DIR, content = "legacy")

        val file = testee.faviconFile(FileBasedFaviconPersister.FAVICON_WIDGET_PLACEHOLDERS_DIR, subFolder, domain)

        assertNotNull(file)
        assertTrue(file!!.absolutePath.startsWith(context.filesDir.absolutePath))
        assertFalse(File(context.cacheDir, FileBasedFaviconPersister.FAVICON_WIDGET_PLACEHOLDERS_DIR).exists())
    }

    @Test
    fun whenNoLegacyCacheDirectoryExistsThenNothingIsCreatedInCache() = runTest {
        createNewFile(FileBasedFaviconPersister.FAVICON_PERSISTED_DIR)

        val file = testee.faviconFile(FileBasedFaviconPersister.FAVICON_PERSISTED_DIR, subFolder, domain)

        assertNotNull(file)
        assertFalse(File(context.cacheDir, FileBasedFaviconPersister.FAVICON_PERSISTED_DIR).exists())
    }

    @Test
    fun whenLegacyCacheDirectoryIsEmptyThenItIsRemoved() = runTest {
        val legacyDirectory = File(context.cacheDir, FileBasedFaviconPersister.FAVICON_PERSISTED_DIR)
        legacyDirectory.mkdirs()

        testee.faviconFile(FileBasedFaviconPersister.FAVICON_PERSISTED_DIR, subFolder, domain)

        assertFalse(legacyDirectory.exists())
    }

    @Test
    fun whenLegacyCacheContainsNestedFilesThenStructureIsPreserved() = runTest {
        createLegacyCacheFile(FileBasedFaviconPersister.FAVICON_PERSISTED_DIR, content = "legacy")
        val nested = File(File(File(context.cacheDir, FileBasedFaviconPersister.FAVICON_PERSISTED_DIR), "nested"), "inner.png")
        nested.parentFile!!.mkdirs()
        writeBytesToFile(nested, "nested")

        testee.faviconFile(FileBasedFaviconPersister.FAVICON_PERSISTED_DIR, subFolder, domain)

        val migratedNested = File(File(File(context.filesDir, FileBasedFaviconPersister.FAVICON_PERSISTED_DIR), "nested"), "inner.png")
        assertEquals("nested", migratedNested.readText())
        assertFalse(File(context.cacheDir, FileBasedFaviconPersister.FAVICON_PERSISTED_DIR).exists())
    }

    @Test
    fun whenLegacyCacheContainsTmpFileThenItIsNotMigrated() = runTest {
        createLegacyCacheFile(FileBasedFaviconPersister.FAVICON_PERSISTED_DIR, content = "legacy")
        val legacyDirectory = File(File(context.cacheDir, FileBasedFaviconPersister.FAVICON_PERSISTED_DIR), subFolder)
        writeBytesToFile(File(legacyDirectory, "${filename(domain)}.tmp"), "partial")

        testee.faviconFile(FileBasedFaviconPersister.FAVICON_PERSISTED_DIR, subFolder, domain)

        val migratedDirectory = File(File(context.filesDir, FileBasedFaviconPersister.FAVICON_PERSISTED_DIR), subFolder)
        assertTrue(File(migratedDirectory, filename(domain)).exists())
        assertFalse(File(migratedDirectory, "${filename(domain)}.tmp").exists())
        assertFalse(File(context.cacheDir, FileBasedFaviconPersister.FAVICON_PERSISTED_DIR).exists())
    }

    @Test
    fun whenLegacyCacheIsAccessedConcurrentlyThenMigrationHappensOnceWithoutErrors() = runTest {
        createLegacyCacheFile(FileBasedFaviconPersister.FAVICON_PERSISTED_DIR, content = "legacy")

        val results = (1..20).map {
            async(Dispatchers.IO) {
                testee.faviconFile(FileBasedFaviconPersister.FAVICON_PERSISTED_DIR, subFolder, domain)
            }
        }.awaitAll()

        assertTrue(results.all { it != null && it.absolutePath.startsWith(context.filesDir.absolutePath) })
        assertEquals("legacy", results.first()!!.readText())
        assertFalse(File(context.cacheDir, FileBasedFaviconPersister.FAVICON_PERSISTED_DIR).exists())
    }

    @Test
    fun whenTempDirectoryIsAccessedThenNothingIsMigrated() = runTest {
        val legacyFile = createLegacyCacheFile(FileBasedFaviconPersister.FAVICON_PERSISTED_DIR, content = "legacy")

        testee.faviconFile(FileBasedFaviconPersister.FAVICON_TEMP_DIR, subFolder, domain)

        assertTrue(legacyFile.exists())
    }

    @Test
    fun whenCopyToDirectoryThenFileCopiedToNewDirectory() = runTest {
        val filename = "newFileName"
        createNewFile()

        testee.copyToDirectory(getTestFile(), secondaryTestDirectory, subFolder, filename)

        val newFile = testee.faviconFile(secondaryTestDirectory, subFolder, filename)
        verifyDirectoryUse(newFile!!.absolutePath, secondaryTestDirectory)
    }

    @Test
    fun whenCopyToDirectoryAndAtomicWritesEnabledThenFileCopiedAndNoTmpFileLeft() = runTest {
        fakeBrowserConfigFeature.atomicFaviconWrites().setRawStoredState(Toggle.State(true))
        val filename = "newFileName"
        createNewFile()

        testee.copyToDirectory(getTestFile(), secondaryTestDirectory, subFolder, filename)

        val newFile = testee.faviconFile(secondaryTestDirectory, subFolder, filename)
        assertNotNull(newFile)
        verifyDirectoryUse(newFile!!.absolutePath, secondaryTestDirectory)
        assertFalse(File(newFile.parent, "${newFile.name}.tmp").exists())
    }

    @Test
    fun whenStoreBitmapCorrectlyThenReturnFile() = runTest {
        val bitmap: Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.RGB_565)

        val file = testee.store(testDirectory, subFolder, bitmap, "filename")

        assertTrue(file!!.exists())
        verifySubfolderUsedAsDirectory(subFolder, file)
    }

    @Test
    fun whenStoreBitmapAndSizeIsBiggerThanPreviouslySavedThenReturnNewFile() = runTest {
        val bitmap: Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.RGB_565)
        val newBitmap: Bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.RGB_565)

        testee.store(testDirectory, subFolder, bitmap, "filename")
        val file = testee.store(testDirectory, subFolder, newBitmap, "filename")

        assertTrue(file!!.exists())
        val returnedBitmap = BitmapFactory.decodeFile(file.absolutePath)
        assertEquals(2, returnedBitmap.width)
    }

    @Test
    fun whenStoreBitmapAndSizeIsSmallerThanPreviouslySavedThenReturnNull() = runTest {
        val bitmap: Bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.RGB_565)
        val newBitmap: Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.RGB_565)

        testee.store(testDirectory, subFolder, bitmap, "filename")
        val file = testee.store(testDirectory, subFolder, newBitmap, "filename")

        assertNull(file)
    }

    @Test
    fun whenStoreBitmapAndSizeIsEqualsThanPreviouslySavedThenDoesNotReturnNull() = runTest {
        val bitmap: Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.RGB_565)
        val newBitmap: Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.RGB_565)

        testee.store(testDirectory, subFolder, bitmap, "filename")
        val file = testee.store(testDirectory, subFolder, newBitmap, "filename")

        assertNotNull(file)
    }

    @Test
    fun whenStoreFaviconSuspendDisabledAndAtomicWritesEnabledThenFileWrittenAndNoTmpFileLeft() = runTest {
        fakeBrowserConfigFeature.storeFaviconSuspend().setRawStoredState(Toggle.State(false))
        fakeBrowserConfigFeature.atomicFaviconWrites().setRawStoredState(Toggle.State(true))
        val bitmap: Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.RGB_565)

        val file = testee.store(testDirectory, subFolder, bitmap, "filename")

        assertTrue(file!!.exists())
        assertFalse(File(file.parent, "${file.name}.tmp").exists())
    }

    @Test
    fun whenDeletePersistedFaviconThenDeleteTheFile() = runTest {
        val captor = argumentCaptor<List<String>>()

        testee.deletePersistedFavicon("domain")
        verify(mockFileDeleter).deleteFilesFromDirectory(any(), captor.capture())
    }

    @Test
    fun whenDeletingNonSpecificFaviconForSubfolderThenDeleteTheDirectory() = runTest {
        testee.deleteFaviconsForSubfolder(testDirectory, subFolder, domain = null)
        verify(mockFileDeleter).deleteDirectory(any())
    }

    @Test
    fun whenDeletingOldFaviconForATabButANewOneExistsThenOnlySingleFaviconDeleted() = runTest {
        val newFaviconFilename = "newFavicon"
        val captor = argumentCaptor<List<String>>()
        testee.deleteFaviconsForSubfolder(testDirectory, subFolder, newFaviconFilename)
        verify(mockFileDeleter).deleteContents(any(), captor.capture())
        verifyExistingFaviconExcludedFromDeletion(captor.firstValue, newFaviconFilename)
    }

    private fun verifyExistingFaviconExcludedFromDeletion(
        exclusionList: List<String>,
        newTabFaviconFilename: String,
    ) {
        assertEquals(1, exclusionList.size)
        assertTrue(exclusionList.contains(newTabFaviconFilename))
    }

    private fun verifySubfolderUsedAsDirectory(
        subFolder: String,
        path: File,
    ) {
        assertTrue(path.parent!!.endsWith(subFolder))
    }

    private fun verifyExpectedBaseDirectoryUsed(
        path: String,
        directory: String,
    ) {
        assertTrue(path.startsWith(expectedBaseDir(directory).absolutePath))
    }

    private fun verifyDirectoryUse(
        path: String,
        directory: String,
    ) {
        assertTrue(path.contains("/$directory"))
    }

    // Mirrors FileBasedFaviconPersister's private faviconDirectory() base-dir decision.
    private fun expectedBaseDir(directory: String): File {
        return if (directory == FileBasedFaviconPersister.FAVICON_TEMP_DIR) context.cacheDir else context.filesDir
    }

    private fun createNewFile(
        directory: String = testDirectory,
        content: String = "1",
    ) {
        val previewFileDestination = File(File(expectedBaseDir(directory), directory), subFolder)
        previewFileDestination.mkdirs()
        val file = File(previewFileDestination, filename(domain))
        writeBytesToFile(file, content)
    }

    // Where FileBasedFaviconPersister stored persisted favicons before they moved out of cacheDir.
    private fun createLegacyCacheFile(
        directory: String,
        content: String,
    ): File {
        val legacyDirectory = File(File(context.cacheDir, directory), subFolder)
        legacyDirectory.mkdirs()
        return File(legacyDirectory, filename(domain)).also { writeBytesToFile(it, content) }
    }

    private fun getTestFile(): File {
        val previewFileDestination = File(File(expectedBaseDir(testDirectory), testDirectory), subFolder)
        return File(previewFileDestination, filename(domain))
    }

    private fun writeBytesToFile(
        previewFile: File,
        content: String = "1",
    ) {
        FileOutputStream(previewFile).use { outputStream ->
            outputStream.write(content.toByteArray())
            outputStream.flush()
        }
    }

    private fun filename(name: String): String = "${name.sha256}.png"

    private fun deleteTestFolders() {
        listOf(
            testDirectory,
            secondaryTestDirectory,
            FileBasedFaviconPersister.FAVICON_TEMP_DIR,
            FileBasedFaviconPersister.FAVICON_PERSISTED_DIR,
            FileBasedFaviconPersister.FAVICON_WIDGET_PLACEHOLDERS_DIR,
        ).forEach {
            File(expectedBaseDir(it), it).deleteRecursively()
            File(context.cacheDir, it).deleteRecursively()
        }
    }
}
