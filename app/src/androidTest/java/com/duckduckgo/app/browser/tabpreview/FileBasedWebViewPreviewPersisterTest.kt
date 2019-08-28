/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.app.browser.tabpreview

import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.global.file.FileDeleter
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class FileBasedWebViewPreviewPersisterTest {

    private lateinit var testee: FileBasedWebViewPreviewPersister
    private val mockFileDeleter: FileDeleter = mock()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setup() {
        testee = FileBasedWebViewPreviewPersister(context, mockFileDeleter)
    }

    @Test
    fun whenFullPathReturnedForPreviewFileThenCacheDirectoryUsed() {
        val tabId = "ABC-123"
        val previewFileName = "12345.jpg"
        val path = testee.fullPathForFile(tabId, previewFileName)
        verifyCacheDirectoryUsed(path)
    }

    @Test
    fun whenFullPathReturnedForPreviewFileThenSpecificTabPreviewDirectoryUsed() {
        val tabId = "ABC-123"
        val previewFileName = "12345.jpg"
        val path = testee.fullPathForFile(tabId, previewFileName)
        verifyTabPreviewDirectoryUse(path)
    }

    @Test
    fun whenFullPathReturnedForPreviewFileThenTabIdUsedAsParentDirectory() {
        val tabId = "ABC-123"
        val previewFileName = "12345.jpg"
        val path = File(testee.fullPathForFile(tabId, previewFileName))
        verifyTabIdUsedAsDirectory(tabId, path)
    }

    @Test
    fun whenDeleteAllCalledThenEntireTabPreviewDirectoryDeleted() = runBlocking<Unit> {
        testee.deleteAll()
        val captor = argumentCaptor<File>()
        verify(mockFileDeleter).deleteDirectory(captor.capture())
        verifyTabPreviewDirectoryUse(captor.firstValue.absolutePath)
    }

    @Test
    fun whenDeletingOnlyPreviewForATabThenTabDirectoryRemoved() = runBlocking<Unit> {
        val tabId = "ABC-123"
        testee.deletePreviewsForTab(tabId, currentPreviewImage = null)
        verify(mockFileDeleter).deleteDirectory(any())
    }

    @Test
    fun whenDeletingOldPreviewForATabButANewOneExistsThenOnlySinglePreviewImageDeleted() = runBlocking<Unit> {
        val tabId = "ABC-123"
        val newTabPreviewFilename = "new.jpg"
        val captor = argumentCaptor<List<String>>()
        testee.deletePreviewsForTab(tabId, newTabPreviewFilename)
        verify(mockFileDeleter).deleteContents(any(), captor.capture())
        verifyExistingPreviewExcludedFromDeletion(captor.firstValue, newTabPreviewFilename)
    }

    private fun verifyExistingPreviewExcludedFromDeletion(exclusionList: List<String>, newTabPreviewFilename: String) {
        assertEquals(1, exclusionList.size)
        assertTrue(exclusionList.contains(newTabPreviewFilename))
    }

    private fun verifyTabIdUsedAsDirectory(tabId: String, path: File) {
        assertTrue(path.parent.endsWith(tabId))
    }

    private fun verifyCacheDirectoryUsed(path: String) {
        assertTrue(path.startsWith(context.cacheDir.absolutePath))
    }

    private fun verifyTabPreviewDirectoryUse(path: String) {
        assertTrue(path.contains("/${FileBasedWebViewPreviewPersister.TAB_PREVIEW_DIRECTORY}"))
    }
}