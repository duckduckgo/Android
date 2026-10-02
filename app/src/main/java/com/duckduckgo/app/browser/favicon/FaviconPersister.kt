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
import com.duckduckgo.app.global.file.FileDeleter
import com.duckduckgo.browser.feature.toggles.AndroidBrowserConfigFeature
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.sha256
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import logcat.LogPriority
import logcat.LogPriority.INFO
import logcat.logcat
import java.io.File
import java.io.FileOutputStream

interface FaviconPersister {
    fun faviconFile(
        directory: String,
        subFolder: String,
        domain: String,
    ): File?

    suspend fun store(
        directory: String,
        subFolder: String,
        bitmap: Bitmap,
        domain: String,
    ): File?

    suspend fun copyToDirectory(
        file: File,
        directory: String,
        newSubfolder: String,
        newFilename: String,
    )

    suspend fun deleteAll(directory: String)
    suspend fun deletePersistedFavicon(domain: String)
    suspend fun deleteFaviconsForSubfolder(
        directory: String,
        subFolder: String,
        domain: String?,
    )
}

class FileBasedFaviconPersister(
    val context: Context,
    private val fileDeleter: FileDeleter,
    private val androidBrowserConfigFeature: AndroidBrowserConfigFeature,
    private val dispatcherProvider: DispatcherProvider,
) : FaviconPersister {

    val mutex = Mutex()
    private val legacyMigrationLock = Any()

    override suspend fun deleteAll(directory: String) {
        fileDeleter.deleteDirectory(faviconDirectory(directory))
    }

    override fun faviconFile(
        directory: String,
        subFolder: String,
        domain: String,
    ): File? {
        val file = fileForFavicon(directory, subFolder, domain)
        return if (file.exists()) {
            file
        } else {
            null
        }
    }

    override suspend fun copyToDirectory(
        file: File,
        directory: String,
        newSubfolder: String,
        newFilename: String,
    ) {
        withContext(dispatcherProvider.io()) {
            val persistedFile = fileForFavicon(directory, newSubfolder, newFilename)
            if (androidBrowserConfigFeature.atomicFaviconWrites().isEnabled()) {
                val tmp = File(persistedFile.parent, "${persistedFile.name}$TMP_FILE_SUFFIX")
                runCatching {
                    file.copyTo(tmp, overwrite = true)
                    if (!tmp.renameTo(persistedFile)) {
                        tmp.delete()
                        logcat(LogPriority.WARN) { "FaviconPersister [copyToDirectory][atomic]: failed to rename to ${persistedFile.name}" }
                    }
                }.onFailure {
                    tmp.delete()
                }
            } else {
                try {
                    file.copyTo(persistedFile, overwrite = true)
                } catch (e: FileAlreadyExistsException) {
                    logcat { "FaviconPersister copyToDirectory [legacy]: failed to overwrite ${persistedFile.name}: ${e.message}" }
                }
            }
        }
    }

    override suspend fun store(
        directory: String,
        subFolder: String,
        bitmap: Bitmap,
        domain: String,
    ): File? {
        return withContext(dispatcherProvider.io() + NonCancellable) {
            if (androidBrowserConfigFeature.storeFaviconSuspend().isEnabled()) {
                writeToDiskAsync(directory, subFolder, bitmap, domain)
            } else {
                writeToDisk(directory, subFolder, bitmap, domain)
            }
        }
    }

    override suspend fun deletePersistedFavicon(domain: String) {
        val directoryToDelete = directoryForFavicon(FAVICON_PERSISTED_DIR, "")
        fileDeleter.deleteFilesFromDirectory(directoryToDelete, listOf(filename(domain)))
    }

    override suspend fun deleteFaviconsForSubfolder(
        directory: String,
        subFolder: String,
        domain: String?,
    ) {
        val directoryToDelete = directoryForFavicon(directory, subFolder)

        if (domain == null) {
            fileDeleter.deleteDirectory(directoryToDelete)
        } else {
            val exclusionList = listOf(domain)
            fileDeleter.deleteContents(directoryToDelete, exclusionList)
        }
    }

    private fun fileForFavicon(
        directory: String,
        subFolder: String,
        domain: String,
    ): File {
        val tabFaviconDirectory = directoryForFavicon(directory, subFolder)
        return File(tabFaviconDirectory, filename(domain))
    }

    private fun directoryForFavicon(
        directory: String,
        subFolder: String,
    ): File {
        return File(faviconDirectory(directory), subFolder)
    }

    private fun prepareDestinationFile(
        directory: String,
        tabId: String,
        url: String,
    ): File {
        val fileDestination = directoryForFavicon(directory, tabId)
        fileDestination.mkdirs()

        return File(fileDestination, filename(url))
    }

    @Synchronized
    private fun writeToDisk(
        directory: String,
        subFolder: String,
        bitmap: Bitmap,
        domain: String,
    ): File? {
        val existingFile = fileForFavicon(directory, subFolder, domain)

        if (existingFile.exists()) {
            logcat(INFO) { "Favicon favicon exists for $domain in $subFolder" }
            val existingFavicon = BitmapFactory.decodeFile(existingFile.absolutePath)

            existingFavicon?.let {
                if (it.width > bitmap.width) {
                    return null // Stored file has better quality
                }
            }
        }

        val faviconFile = prepareDestinationFile(directory, subFolder, domain)
        writeBytesToFile(faviconFile, bitmap)

        return if (faviconFile.exists()) {
            faviconFile
        } else {
            null
        }
    }

    private suspend fun writeToDiskAsync(
        directory: String,
        subFolder: String,
        bitmap: Bitmap,
        domain: String,
    ): File? {
        mutex.withLock {
            val existingFile = fileForFavicon(directory, subFolder, domain)

            if (existingFile.exists()) {
                logcat(INFO) { "Favicon favicon exists for $domain in $subFolder" }
                val existingFavicon = BitmapFactory.decodeFile(existingFile.absolutePath)

                existingFavicon?.let {
                    if (it.width > bitmap.width) {
                        return null // Stored file has better quality
                    }
                }
            }

            val faviconFile = prepareDestinationFile(directory, subFolder, domain)
            runCatching {
                if (androidBrowserConfigFeature.atomicFaviconWrites().isEnabled()) {
                    writeBitmapAtomically(faviconFile, bitmap)
                } else {
                    FileOutputStream(faviconFile).use { outputStream ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                        outputStream.flush()
                    }
                }
            }

            return if (faviconFile.exists()) {
                faviconFile
            } else {
                null
            }
        }
    }

    @Synchronized
    private fun writeBytesToFile(
        file: File,
        bitmap: Bitmap,
    ) {
        runCatching {
            if (androidBrowserConfigFeature.atomicFaviconWrites().isEnabled()) {
                writeBitmapAtomically(file, bitmap)
            } else {
                FileOutputStream(file).use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                    outputStream.flush()
                }
            }
        }
    }

    private fun writeBitmapAtomically(file: File, bitmap: Bitmap) {
        val tmp = File(file.parent, "${file.name}$TMP_FILE_SUFFIX")
        FileOutputStream(tmp).use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            outputStream.flush()
        }
        if (!tmp.renameTo(file)) {
            // should be very unlikely to get here; try to clean up tmp file if it happens
            tmp.delete()
            logcat(LogPriority.WARN) { "FaviconPersister [atomic]: failed to rename ${tmp.name} to ${file.name}" }
        }
    }

    private fun faviconDirectory(directory: String): File {
        // FAVICON_TEMP_DIR holds per-tab favicons and is fine to live under cacheDir, which the OS
        // may clear under storage pressure. FAVICON_PERSISTED_DIR (bookmarks/favorites) and
        // FAVICON_WIDGET_PLACEHOLDERS_DIR must survive that, so they live in non-cache storage.
        if (directory == FAVICON_TEMP_DIR) {
            return File(context.cacheDir, directory)
        }
        return File(context.filesDir, directory).also { migrateLegacyCacheDirectory(directory, it) }
    }

    /**
     * Persisted favicons used to live under cacheDir. Moving them on first access keeps existing
     * bookmark and favorite icons after an upgrade instead of showing placeholders until each site
     * is visited again. The legacy directory is removed afterwards, so this is a single exists()
     * check on every later call.
     */
    private fun migrateLegacyCacheDirectory(
        directory: String,
        destination: File,
    ) {
        val legacyDirectory = File(context.cacheDir, directory)
        if (!legacyDirectory.exists()) return

        synchronized(legacyMigrationLock) {
            if (!legacyDirectory.exists()) return

            val allMoved = legacyDirectory.walkTopDown()
                .filter { it.isFile && !it.name.endsWith(TMP_FILE_SUFFIX) }
                .map { legacyFile -> moveLegacyFile(legacyFile, File(destination, legacyFile.relativeTo(legacyDirectory).path)) }
                .toList()
                .all { it }

            // Only drop the legacy directory once every file is safely in the new location. A partial
            // failure (e.g. no space for the copy fallback) leaves the remaining files where they are so
            // the next lookup can retry them instead of losing them.
            if (allMoved) {
                legacyDirectory.deleteRecursively()
            } else {
                logcat(LogPriority.WARN) { "FaviconPersister: some legacy $directory favicons could not be migrated, will retry on next access" }
            }
        }
    }

    private fun moveLegacyFile(
        source: File,
        target: File,
    ): Boolean = runCatching {
        // A file already written by this version is newer than the legacy one, keep it.
        if (target.exists()) return@runCatching true
        target.parentFile?.mkdirs()
        if (source.renameTo(target)) return@runCatching true

        // Copy through a temp file so a failure part-way never leaves a truncated favicon behind.
        val tmp = File(target.parent, "${target.name}$TMP_FILE_SUFFIX")
        val copied = runCatching {
            source.copyTo(tmp, overwrite = true)
            tmp.renameTo(target)
        }.getOrDefault(false)
        if (!copied) {
            tmp.delete()
            return@runCatching false
        }
        source.delete()
        true
    }.getOrElse {
        logcat(LogPriority.WARN) { "FaviconPersister: failed to migrate ${source.name}: ${it.message}" }
        false
    }

    private fun filename(name: String): String = "${name.sha256}.png"

    companion object {
        const val FAVICON_TEMP_DIR = "faviconsTemp"
        const val FAVICON_PERSISTED_DIR = "favicons"
        const val FAVICON_WIDGET_PLACEHOLDERS_DIR = "faviconsWidgetPlaceholders"
        const val NO_SUBFOLDER = ""
        private const val TMP_FILE_SUFFIX = ".tmp"
    }
}
