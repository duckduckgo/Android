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

package com.duckduckgo.app.global.file

import com.duckduckgo.common.utils.DispatcherProvider
import kotlinx.coroutines.withContext
import logcat.LogPriority.ERROR
import logcat.LogPriority.INFO
import logcat.logcat
import java.io.File

interface FileDeleter {

    /**
     * Delete the contents of the given directory, but don't delete the directory itself
     *
     * Optionally: specify an exclusion list. Files with names exactly matching will not be deleted.
     * Note, the exclusion list only applies to the top-level directory. All files in subdirectories will be deleted, regardless of exclusion list.
     */
    suspend fun deleteContents(
        parentDirectory: File,
        excludedFiles: List<String> = emptyList(),
    )

    /**
     * Delete the contents of the given directory, and deletes the directory itself.
     */
    suspend fun deleteDirectory(directoryToDelete: File)

    /**
     * Delete a file(s) of the given directory, but don't delete the directory itself
     */
    suspend fun deleteFilesFromDirectory(
        parentDirectory: File,
        files: List<String>,
    )
}

class AndroidFileDeleter(private val dispatchers: DispatcherProvider) : FileDeleter {
    override suspend fun deleteContents(
        parentDirectory: File,
        excludedFiles: List<String>,
    ) {
        logcat(INFO) { "Deleting contents of directory: $parentDirectory" }
        withContext(dispatchers.io()) {
            runCatching {
                val files = parentDirectory.listFiles() ?: return@withContext
                val filesToDelete = files.filterNot { excludedFiles.contains(it.name) }
                filesToDelete.forEach { file ->
                    try {
                        val success = file.deleteRecursively()
                        if (success) {
                            logcat(INFO) { "Deleted file: ${file.name}" }
                        } else {
                            logcat(ERROR) { "Failed to delete file: ${file.name}" }
                        }
                    } catch (e: Exception) {
                        logcat(ERROR) { "Error deleting file ${file.name}: ${e.message}" }
                    }
                }
            }.onFailure {
                logcat(ERROR) { "Failed to delete contents of directory: $parentDirectory" }
            }
        }
    }

    override suspend fun deleteDirectory(directoryToDelete: File) {
        logcat(INFO) { "Deleting directory: $directoryToDelete" }
        withContext(dispatchers.io()) {
            runCatching {
                val success = directoryToDelete.deleteRecursively()
                if (success) {
                    logcat(INFO) { "Successfully deleted directory: $directoryToDelete" }
                } else {
                    logcat(ERROR) { "Failed to delete directory: $directoryToDelete" }
                }
            }.onFailure {
                logcat(ERROR) { "Error while deleting directory: ${it.message}" }
            }
        }
    }

    override suspend fun deleteFilesFromDirectory(
        parentDirectory: File,
        files: List<String>,
    ) {
        logcat(INFO) { "Deleting files from directory: $parentDirectory" }
        withContext(dispatchers.io()) {
            runCatching {
                val allFiles = parentDirectory.listFiles() ?: return@withContext
                val filesToDelete = allFiles.filter { files.contains(it.name) }
                val results = filesToDelete.map { file ->
                    try {
                        val success = file.deleteRecursively()
                        if (!success) {
                            logcat(ERROR) { "Failed to delete file: ${file.name}" }
                        }
                        success
                    } catch (e: Exception) {
                        logcat(ERROR) { "Error deleting file ${file.name}: ${e.message}" }
                        false
                    }
                }

                val deletedCount = results.count { it }
                logcat(INFO) { "Successfully deleted $deletedCount/${filesToDelete.size} files" }
            }.onFailure {
                logcat(ERROR) { "Failed to process file deletion: ${it.message}" }
            }
        }
    }
}