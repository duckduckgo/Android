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
import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Unique
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.persistentstorage.api.PersistentStorage
import com.duckduckgo.persistentstorage.api.PersistentStorageKey
import com.duckduckgo.persistentstorage.api.PersistentStorageUnavailableException
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import logcat.LogPriority.ERROR
import logcat.LogPriority.INFO
import logcat.LogPriority.WARN
import logcat.logcat
import java.io.File
import javax.inject.Inject

interface ReinstallDetector {
    /**
     * @return true if a previous install was found on this device. On Android 10 and older this relies on a Block Store flag,
     * which is only kept through an uninstall when the device has Android backup turned on.
     */
    suspend fun isAppReinstall(): Boolean
}

/**
 * Stays on the device (no cloud backup) and holds no identifier: its presence only says the app was installed here before.
 */
object InstallSeenPersistentStorageKey : PersistentStorageKey(
    key = "com.duckduckgo.app.install.seen",
    shouldBackupToCloud = false,
)

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<ReinstallDetector>())
@ContributesIntoSet(AppScope::class, binding = binding<MainProcessLifecycleObserver>())
class RealReinstallDetector @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    private val sharedPreferencesProvider: SharedPreferencesProvider,
    private val persistentStorage: PersistentStorage,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val pixel: Pixel,
) : ReinstallDetector, MainProcessLifecycleObserver {

    private val sdkInt: Int = Build.VERSION.SDK_INT

    private val preferences by lazy {
        sharedPreferencesProvider.getSharedPreferences(PREFERENCES_FILE_NAME, false, false)
    }

    // Onboarding and the pre-ATB plugins can ask at the same time on first launch. Without the lock, a second
    // caller can see the marker the first one just created and answer "reinstall" for a new install.
    // Not reentrant: nothing run while holding it may call isAppReinstall().
    private val reinstallCheckMutex = Mutex()

    // Keeps the two flag writers (after a new-install check, and on app start) from calling Block Store at once
    private val installSeenFlagWriteMutex = Mutex()

    override fun onCreate(owner: LifecycleOwner) {
        if (sdkInt >= 30) return

        // Below Android 11 a later reinstall is only detected if this install wrote the Block Store flag. Installs from before
        // the flag existed, or whose first write failed, may never ask on their own, so make sure it gets written.
        appCoroutineScope.launch(dispatcherProvider.io()) {
            isAppReinstall()
            storeInstallSeenFlagIfMissing()
        }
    }

    override suspend fun isAppReinstall(): Boolean = withContext(dispatcherProvider.io()) {
        savedAppReinstallValueOrNull() ?: reinstallCheckMutex.withLock {
            // The caller holding the lock can be cancelled, e.g. when onboarding's screen closes. Finishing the check keeps
            // creating the marker and saving the answer together, so the next caller can't find one without the other.
            withContext(NonCancellable) {
                savedAppReinstallValueOrNull() ?: detectAndSaveAppReinstall()
            }
        }
    }

    private suspend fun detectAndSaveAppReinstall(): Boolean =
        if (sdkInt < 30) {
            detectAndSaveWithBlockStore()
        } else {
            runCatching { detectAndSaveWithDownloadsFolder() }.getOrDefault(false)
        }

    private suspend fun detectAndSaveWithBlockStore(): Boolean {
        val installSeen = callBlockStore { persistentStorage.retrieve(InstallSeenPersistentStorageKey) }
        val checkResult = when {
            installSeen == null -> BlockStoreCheckResult.TIMEOUT
            installSeen.exceptionOrNull() is PersistentStorageUnavailableException -> BlockStoreCheckResult.UNAVAILABLE
            installSeen.isFailure -> BlockStoreCheckResult.ERROR
            installSeen.getOrNull() != null -> BlockStoreCheckResult.FOUND
            else -> BlockStoreCheckResult.NOT_FOUND
        }
        if (installSeen?.isFailure == true) {
            logcat(WARN) { "Reinstall check: Block Store read failed ${installSeen.exceptionOrNull()}" }
        }
        val isReinstall = checkResult == BlockStoreCheckResult.FOUND
        logcat(INFO) { "Reinstall check (Block Store): result=${checkResult.value}, reinstall=$isReinstall" }
        // Without it there's no way to tell "Block Store dropped the flag" from "few reinstalls" from "Play services unavailable"
        pixel.fire(
            ReinstallDetectorPixelName.BLOCK_STORE_CHECK_RESULT,
            parameters = mapOf(PARAM_RESULT to checkResult.value),
            type = Unique(),
        )

        // Saved even when Block Store failed, so every caller gets the same answer for this install instead of retrying a slow call
        runCatching { saveAppReinstallValue(isReinstall) }

        if (isReinstall) {
            runCatching { preferences.edit(commit = true) { putBoolean(INSTALL_SEEN_FLAG_STORED_KEY, true) } }
        } else {
            // Written outside the lock: the answer is already saved, so callers shouldn't also wait for a second Block Store call
            appCoroutineScope.launch(dispatcherProvider.io()) { storeInstallSeenFlagIfMissing() }
        }
        return isReinstall
    }

    private suspend fun storeInstallSeenFlagIfMissing() = installSeenFlagWriteMutex.withLock {
        // Only once this install's answer is saved, so the flag can't make this install look like a reinstall
        val answerSaved = savedAppReinstallValueOrNull() != null
        val flagStored = runCatching { preferences.getBoolean(INSTALL_SEEN_FLAG_STORED_KEY, false) }.getOrDefault(false)
        if (answerSaved && !flagStored) {
            storeInstallSeenFlag()
        }
    }

    private suspend fun storeInstallSeenFlag() {
        val result = callBlockStore { persistentStorage.store(InstallSeenPersistentStorageKey, INSTALL_SEEN_FLAG_VALUE) }
        if (result?.isSuccess == true) {
            runCatching { preferences.edit(commit = true) { putBoolean(INSTALL_SEEN_FLAG_STORED_KEY, true) } }
        } else {
            logcat(WARN) { "Reinstall check: could not store the Block Store flag ${result?.exceptionOrNull() ?: "(timed out)"}" }
        }
    }

    /**
     * @return null if the call didn't finish within the time limit. Any exception becomes a failure, including one thrown before the
     * call reaches Block Store (e.g. while checking for Play services), so it can't escape to onboarding, the ATB step or the app scope.
     */
    private suspend fun <T> callBlockStore(call: suspend () -> Result<T>): Result<T>? = try {
        withTimeoutOrNull(BLOCK_STORE_TIME_LIMIT_MS) { call() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    private fun savedAppReinstallValueOrNull(): Boolean? = runCatching { savedAppReinstallValue() }.getOrNull()

    private fun savedAppReinstallValue(): Boolean? =
        if (preferences.contains(APP_REINSTALLED_KEY)) preferences.getBoolean(APP_REINSTALLED_KEY, false) else null

    private fun saveAppReinstallValue(isReinstall: Boolean) {
        preferences.edit(commit = true) { putBoolean(APP_REINSTALLED_KEY, isReinstall) }
    }

    private fun detectAndSaveWithDownloadsFolder(): Boolean {
        val downloadDirectory = getDownloadsDirectory()
        val ddgDirectoryExists = (downloadDirectory.list()?.asList() ?: emptyList()).contains(DDG_DOWNLOADS_DIRECTORY)
        val appReinstallValue = if (!ddgDirectoryExists) {
            createNewDirectory(DDG_DOWNLOADS_DIRECTORY)
            // this is a new install
            false
        } else {
            true
        }
        saveAppReinstallValue(appReinstallValue)
        return appReinstallValue
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
        private const val INSTALL_SEEN_FLAG_STORED_KEY = "installSeenFlagStored"
        private const val PARAM_RESULT = "result"
        private const val DDG_DOWNLOADS_DIRECTORY = "DuckDuckGo"

        // Nothing else bounds this step: the pre-ATB time limit can't stop it, so a stuck Play services call would hold back the ATB
        private const val BLOCK_STORE_TIME_LIMIT_MS = 10_000L
        private val INSTALL_SEEN_FLAG_VALUE = byteArrayOf(1)
    }
}
