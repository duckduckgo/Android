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

package com.duckduckgo.app.browser.favicon

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.file.FileDeleter
import com.duckduckgo.app.global.sha256
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

interface FaviconPersister {
    fun faviconFile(directory: String, subFolder: String, domain: String): File?
    suspend fun store(directory: String, subFolder: String, bitmap: Bitmap, domain: String): File?
    suspend fun copyToDirectory(file: File, directory: String, newSubfolder: String, newFilename: String)
    suspend fun deleteAll(directory: String)
    suspend fun deletePersistedFavicon(domain: String)
    suspend fun deleteFaviconsForSubfolder(directory: String, subFolder: String, domain: String?)
}

class FileBasedFaviconPersister(
    val context: Context,
    private val fileDeleter: FileDeleter,
    private val dispatcherProvider: DispatcherProvider
) : FaviconPersister {

    override suspend fun deleteAll(directory: String) {
        fileDeleter.deleteDirectory(faviconDirectory(directory))
    }

    override fun faviconFile(directory: String, subFolder: String, domain: String): File? {
        val file = fileForFavicon(directory, subFolder, domain)
        return if (file.exists()) {
            file
        } else {
            null
        }
    }

    override suspend fun copyToDirectory(file: File, directory: String, newSubfolder: String, newFilename: String) {
        withContext(dispatcherProvider.io()) {
            val persistedFile = fileForFavicon(directory, newSubfolder, newFilename)
            file.copyTo(persistedFile, overwrite = true)
        }
    }

    override suspend fun store(directory: String, subFolder: String, bitmap: Bitmap, domain: String): File? {
        return withContext(dispatcherProvider.io() + NonCancellable) {
            writeToDisk(directory, subFolder, bitmap, domain)
        }
    }

    override suspend fun deletePersistedFavicon(domain: String) {
        val directoryToDelete = directoryForFavicon(FAVICON_PERSISTED_DIR, "")
        fileDeleter.deleteFilesFromDirectory(directoryToDelete, listOf(filename(domain)))
    }

    override suspend fun deleteFaviconsForSubfolder(directory: String, subFolder: String, domain: String?) {
        val directoryToDelete = directoryForFavicon(directory, subFolder)

        if (domain == null) {
            fileDeleter.deleteDirectory(directoryToDelete)
        } else {
            val exclusionList = listOf(domain)
            fileDeleter.deleteContents(directoryToDelete, exclusionList)
        }
    }

    private fun fileForFavicon(directory: String, subFolder: String, domain: String): File {
        val tabFaviconDirectory = directoryForFavicon(directory, subFolder)
        return File(tabFaviconDirectory, filename(domain))
    }

    private fun directoryForFavicon(directory: String, subFolder: String): File {
        return File(faviconDirectory(directory), subFolder)
    }

    private fun prepareDestinationFile(directory: String, tabId: String, url: String): File {
        val fileDestination = directoryForFavicon(directory, tabId)
        fileDestination.mkdirs()

        return File(fileDestination, filename(url))
    }

    @Synchronized
    private fun writeToDisk(directory: String, subFolder: String, bitmap: Bitmap, domain: String): File? {
        val existingFile = fileForFavicon(directory, subFolder, domain)

        if (existingFile.exists()) {

            val existingFavicon = BitmapFactory.decodeFile(existingFile.absolutePath)

            existingFavicon?.let {
                if (it.width > bitmap.width) {
                    return null // Stored file has better quality
                }
            }
        }

        val faviconFile = prepareDestinationFile(directory, subFolder, domain)
        writeBytesToFile(faviconFile, bitmap)

        return faviconFile
    }

    private fun writeBytesToFile(file: File, bitmap: Bitmap) {
        FileOutputStream(file).use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            outputStream.flush()
        }
    }

    private fun faviconDirectory(directory: String): File {
        return File(context.cacheDir, directory)
    }

    private fun filename(name: String): String = "${name.sha256}.png"

    companion object {
        const val FAVICON_TEMP_DIR = "faviconsTemp"
        const val FAVICON_PERSISTED_DIR = "favicons"
        const val NO_SUBFOLDER = ""
    }

}
