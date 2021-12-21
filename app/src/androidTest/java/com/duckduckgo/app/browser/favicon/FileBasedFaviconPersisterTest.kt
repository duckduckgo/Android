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

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import com.duckduckgo.app.global.file.FileDeleter
import com.duckduckgo.app.global.sha256
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.FileOutputStream

@ExperimentalCoroutinesApi
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

    @Before
    fun setup() {
        testee = FileBasedFaviconPersister(context, mockFileDeleter, coroutineRule.testDispatcherProvider)
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
        verifyCacheDirectoryUsed(file.absolutePath)
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

    private fun verifyExistingFaviconExcludedFromDeletion(exclusionList: List<String>, newTabFaviconFilename: String) {
        assertEquals(1, exclusionList.size)
        assertTrue(exclusionList.contains(newTabFaviconFilename))
    }

    private fun verifySubfolderUsedAsDirectory(subFolder: String, path: File) {
        assertTrue(path.parent!!.endsWith(subFolder))
    }

    private fun verifyCacheDirectoryUsed(path: String) {
        assertTrue(path.startsWith(context.cacheDir.absolutePath))
    }

    private fun verifyDirectoryUse(path: String, directory: String) {
        assertTrue(path.contains("/$directory"))
    }

    private fun createNewFile() {
        val previewFileDestination = File(File(context.cacheDir, testDirectory), subFolder)
        previewFileDestination.mkdirs()
        val file = File(previewFileDestination, filename(domain))
        writeBytesToFile(file)
    }

    private fun getTestFile(): File {
        val previewFileDestination = File(File(context.cacheDir, testDirectory), subFolder)
        return File(previewFileDestination, filename(domain))
    }

    private fun writeBytesToFile(previewFile: File) {
        FileOutputStream(previewFile).use { outputStream ->
            outputStream.write("1".toByteArray())
            outputStream.flush()
        }
    }

    private fun filename(name: String): String = "${name.sha256}.png"

    private fun deleteTestFolders() {
        val dirToDelete = File(File(context.cacheDir, testDirectory), "")
        dirToDelete.deleteRecursively()
        val secondaryDirToDelete = File(File(context.cacheDir, secondaryTestDirectory), "")
        secondaryDirToDelete.deleteRecursively()
    }
}
