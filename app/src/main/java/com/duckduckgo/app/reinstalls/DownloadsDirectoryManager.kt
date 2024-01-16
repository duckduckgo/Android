/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.reinstalls

import android.os.Environment
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import java.io.File
import javax.inject.Inject
import timber.log.Timber

interface DownloadsDirectoryManager {

    fun getDownloadsDirectory(): File
    fun createNewDirectory(directoryName: String)
}

@ContributesBinding(AppScope::class)
class DownloadsDirectoryManagerImpl @Inject constructor() : DownloadsDirectoryManager {

    override fun getDownloadsDirectory(): File {
        val downloadDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadDirectory.exists()) {
            Timber.i("Download directory doesn't exist; trying to create it. %s", downloadDirectory.absolutePath)
            downloadDirectory.mkdirs()
        }
        return downloadDirectory
    }

    override fun createNewDirectory(directoryName: String) {
        val directory = File(getDownloadsDirectory(), directoryName)
        val success = directory.mkdirs()
        Timber.i("Directory creation success: %s", success)
        if (!success) {
            Timber.e("Directory creation failed")
            kotlin.runCatching {
                val directoryCreationSuccess = directory.createNewFile()
                Timber.i("File creation success: %s", directoryCreationSuccess)
            }.onFailure {
                Timber.w("Failed to create file: %s", it.message)
            }
        }
    }
}
