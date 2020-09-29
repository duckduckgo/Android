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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

interface FileDeleter {

    /**
     * Delete the contents of the given directory, but don't delete the directory itself
     *
     * Optionally: specify an exclusion list. Files with names exactly matching will not be deleted.
     * Note, the exclusion list only applies to the top-level directory. All files in subdirectories will be deleted, regardless of exclusion list.
     */
    suspend fun deleteContents(parentDirectory: File, excludedFiles: List<String> = emptyList())

    /**
     * Delete the contents of the given directory, and deletes the directory itself.
     */
    suspend fun deleteDirectory(directoryToDelete: File)

    /**
     * Delete a file(s) of the given directory, but don't delete the directory itself
     */
    suspend fun deleteFilesFromDirectory(parentDirectory: File, files: List<String>)
}

class AndroidFileDeleter : FileDeleter {
    override suspend fun deleteContents(parentDirectory: File, excludedFiles: List<String>) {
        withContext(Dispatchers.IO) {
            val files = parentDirectory.listFiles() ?: return@withContext
            val filesToDelete = files.filterNot { excludedFiles.contains(it.name) }
            filesToDelete.forEach { it.deleteRecursively() }
        }
    }

    override suspend fun deleteDirectory(directoryToDelete: File) {
        withContext(Dispatchers.IO) {
            directoryToDelete.deleteRecursively()
        }
    }

    override suspend fun deleteFilesFromDirectory(parentDirectory: File, files: List<String>) {
        withContext(Dispatchers.IO) {
            val allFiles = parentDirectory.listFiles() ?: return@withContext
            val filesToDelete = allFiles.filter { files.contains(it.name) }
            filesToDelete.forEach { it.deleteRecursively() }
        }
    }
}
