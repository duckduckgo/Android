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


import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream


interface WebViewPreviewPersister {

    suspend fun save(bitmap: Bitmap, tabId: String): String
    fun delete(filename: String)
    fun fullPathForFile(previewName: String): String
}

class FileBasedWebViewPreviewPersister(val context: Context) : WebViewPreviewPersister {

    override suspend fun save(bitmap: Bitmap, tabId: String): String {
        return withContext(Dispatchers.IO) {

            val previewFile = prepareDestinationFile(tabId)
            writeBytesToFile(previewFile, bitmap)

            Timber.d("Wrote bitmap preview to ${previewFile.absolutePath}")
            return@withContext previewFile.name
        }
    }

    override fun delete(filename: String) {
        val fileToDelete = File(previewDestinationDirectory(), filename)
        fileToDelete.delete()
    }

    override fun fullPathForFile(previewName: String): String {
        return File(previewDestinationDirectory(), previewName).absolutePath
    }

    private fun prepareDestinationFile(tabId: String): File {
        val previewFileDestination = previewDestinationDirectory()
        previewFileDestination.mkdirs()

        val timestamp = System.currentTimeMillis()
        return File(previewFileDestination, "$tabId-$timestamp.jpg")
    }

    private fun writeBytesToFile(previewFile: File, bitmap: Bitmap) {
        val outputStream = FileOutputStream(previewFile)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
        outputStream.flush()
        outputStream.close()
    }

    private fun previewDestinationDirectory(): File {
        return File(context.cacheDir, TAB_PREVIEW_DIRECTORY)
    }

    companion object {
        private const val TAB_PREVIEW_DIRECTORY = "tabPreviews"
    }

}