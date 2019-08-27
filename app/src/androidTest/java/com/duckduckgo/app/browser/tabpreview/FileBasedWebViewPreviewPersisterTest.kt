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

package com.duckduckgo.app.browser.tabpreview

import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.global.file.FileDeleter
import com.nhaarman.mockitokotlin2.mock
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

    private fun verifyTabIdUsedAsDirectory(tabId: String, path: File) {
        assertTrue(path.parent.endsWith(tabId))
    }

    private fun verifyCacheDirectoryUsed(path: String) {
        assertTrue(path.startsWith(context.cacheDir.absolutePath))
    }

    private fun verifyTabPreviewDirectoryUse(path: String) {
        assertTrue(path.contains("/${FileBasedWebViewPreviewPersister.TAB_PREVIEW_DIRECTORY}/"))
    }
}