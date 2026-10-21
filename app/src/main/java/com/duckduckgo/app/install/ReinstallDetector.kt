/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.app.install

import android.os.Build
import android.os.Environment
import androidx.core.content.edit
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.ContributesBinding
import kotlinx.coroutines.withContext
import logcat.LogPriority.ERROR
import logcat.LogPriority.INFO
import logcat.LogPriority.WARN
import logcat.logcat
import java.io.File
import javax.inject.Inject

interface ReinstallDetector {
    /**
     * @return true if a previous install was found on this device. Always false on Android 10 and older, where it can't be detected.
     */
    suspend fun isAppReinstall(): Boolean
}

@ContributesBinding(AppScope::class)
class RealReinstallDetector @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    private val sharedPreferencesProvider: SharedPreferencesProvider,
) : ReinstallDetector {

    private val sdkInt: Int = Build.VERSION.SDK_INT

    private val preferences by lazy {
        sharedPreferencesProvider.getSharedPreferences(PREFERENCES_FILE_NAME, false, false)
    }

    override suspend fun isAppReinstall(): Boolean = withContext(dispatcherProvider.io()) {
        return@withContext kotlin.runCatching {
            if (sdkInt < 30) {
                return@withContext false
            }

            if (preferences.contains(APP_REINSTALLED_KEY)) {
                return@withContext preferences.getBoolean(APP_REINSTALLED_KEY, false)
            }

            val downloadDirectory = getDownloadsDirectory()
            val ddgDirectoryExists = (downloadDirectory.list()?.asList() ?: emptyList()).contains(DDG_DOWNLOADS_DIRECTORY)
            val appReinstallValue = if (!ddgDirectoryExists) {
                createNewDirectory(DDG_DOWNLOADS_DIRECTORY)
                // this is a new install
                false
            } else {
                true
            }
            preferences.edit(commit = true) { putBoolean(APP_REINSTALLED_KEY, appReinstallValue) }
            return@withContext appReinstallValue
        }.getOrDefault(false)
    }

    private fun getDownloadsDirectory(): File {
        val downloadDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadDirectory.exists()) {
            logcat(INFO) { "Download directory doesn't exist; trying to create it. ${downloadDirectory.absolutePath}" }
            downloadDirectory.mkdirs()
        }
        return downloadDirectory
    }

    private fun createNewDirectory(directoryName: String) {
        val directory = File(getDownloadsDirectory(), directoryName)
        val success = directory.mkdirs()
        logcat(INFO) { "Directory creation success: $success" }
        if (!success) {
            logcat(ERROR) { "Directory creation failed" }
            kotlin.runCatching {
                val directoryCreationSuccess = directory.createNewFile()
                logcat(INFO) { "File creation success: $directoryCreationSuccess" }
            }.onFailure {
                logcat(WARN) { "Failed to create file: ${it.message}" }
            }
        }
    }

    companion object {
        // Existing installs saved their answer under this file and key, so neither can change
        private const val PREFERENCES_FILE_NAME = "com.duckduckgo.app.buildconfig.cache"
        private const val APP_REINSTALLED_KEY = "appReinstalled"
        private const val DDG_DOWNLOADS_DIRECTORY = "DuckDuckGo"
    }
}
