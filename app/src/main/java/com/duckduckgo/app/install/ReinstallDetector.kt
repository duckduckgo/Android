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

import android.content.Context
import android.os.Build
import android.os.Environment
import androidx.core.content.edit
import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Unique
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.utils.CurrentTimeProvider
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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import logcat.LogPriority.ERROR
import logcat.LogPriority.INFO
import logcat.LogPriority.WARN
import logcat.logcat
import java.io.File
import javax.inject.Inject
import kotlin.math.abs

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
    private val context: Context,
    private val dispatcherProvider: DispatcherProvider,
    private val sharedPreferencesProvider: SharedPreferencesProvider,
    private val persistentStorage: PersistentStorage,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val pixel: Pixel,
    private val currentTimeProvider: CurrentTimeProvider,
    private val appBuildConfig: AppBuildConfig,
) : ReinstallDetector, MainProcessLifecycleObserver {

    private val sdkInt: Int = Build.VERSION.SDK_INT

    private val preferences by lazy {
        sharedPreferencesProvider.getSharedPreferences(PREFERENCES_FILE_NAME, false, false)
    }

    // One check per process, shared by every caller. Onboarding and the pre-ATB plugins can ask at the same time on first
    // launch, and a second check could see the marker the first one just created and answer "reinstall" for a new install.
    // It runs in the app scope, so a cancelled caller only stops waiting; the check still saves its answer.
    // Not reentrant: the check must not call isAppReinstall(). Started lazily rather than built in a `by lazy` block, because
    // the flag write it launches waits on it too and could otherwise re-enter the initializer.
    private val reinstallCheck: Deferred<Boolean> = appCoroutineScope.async(dispatcherProvider.io(), start = CoroutineStart.LAZY) {
        runCatching { savedAppReinstallValue() ?: detectAndSaveAppReinstall() }.getOrDefault(false)
    }

    // Keeps the two flag writers (after a new-install check, and on app start) from calling Block Store at once
    private val installSeenFlagWriteMutex = Mutex()

    // A failed save leaves the answer readable in memory, so reading preferences again can't tell it from one that reached
    // disk. Set before the shared check completes.
    @Volatile
    private var answerSaveFailed = false

    override fun onCreate(owner: LifecycleOwner) {
        if (sdkInt >= 30) return

        // Below Android 11 a later reinstall is only detected if this install wrote the Block Store flag. Installs from before
        // the flag existed, or whose first write failed, may never ask on their own, so make sure it gets written.
        appCoroutineScope.launch(dispatcherProvider.io()) {
            isAppReinstall()
            storeInstallSeenFlagIfMissing()
        }
    }

    override suspend fun isAppReinstall(): Boolean = reinstallCheck.await()

    private suspend fun detectAndSaveAppReinstall(): Boolean =
        if (sdkInt < 30) {
            detectAndSaveWithBlockStore()
        } else {
            runCatching { detectAndSaveWithDownloadsFolder() }.getOrDefault(false)
        }

    private suspend fun detectAndSaveWithBlockStore(): Boolean {
        val readStartedAt = currentTimeProvider.elapsedRealtime()
        val installSeen = callBlockStore { persistentStorage.retrieve(InstallSeenPersistentStorageKey) }
        val readDurationMillis = currentTimeProvider.elapsedRealtime() - readStartedAt
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
        // Without it there's no way to tell "Block Store dropped the flag" from "few reinstalls" from "Play services unavailable".
        // Not sent for an update: installs from before Block Store detection run the check once after updating, would almost all
        // report not_found, and would outnumber new installs for weeks.
        if (runCatching { appBuildConfig.isNewInstall() }.getOrDefault(false)) {
            pixel.fire(
                ReinstallDetectorPixelName.BLOCK_STORE_CHECK_RESULT,
                parameters = mapOf(PARAM_RESULT to checkResult.value, PARAM_DURATION to readDurationBucket(readDurationMillis)),
                type = Unique(),
            )
        }

        // Saved even when Block Store failed, so every caller gets the same answer for this install instead of retrying a slow call
        val answerSaved = runCatching { saveAppReinstallValue(isReinstall) }.getOrDefault(false)

        if (isReinstall) {
            runCatching { preferences.edit(commit = true) { putBoolean(INSTALL_SEEN_FLAG_STORED_KEY, true) } }
        } else if (answerSaved) {
            // Written outside the check: the answer is already saved, so callers shouldn't also wait for a second Block Store call
            appCoroutineScope.launch(dispatcherProvider.io()) { storeInstallSeenFlagIfMissing() }
        }
        return isReinstall
    }

    private fun readDurationBucket(durationMillis: Long): String = when {
        durationMillis <= 500L -> "0-0.5"
        durationMillis <= 1_500L -> "0.5-1.5"
        durationMillis <= 3_000L -> "1.5-3"
        durationMillis <= 5_000L -> "3-5"
        durationMillis < BLOCK_STORE_TIME_LIMIT_MS -> "5-10"
        else -> "10+"
    }

    private suspend fun storeInstallSeenFlagIfMissing() = installSeenFlagWriteMutex.withLock {
        if (canStoreInstallSeenFlag()) {
            storeInstallSeenFlag()
        }
    }

    // Only safe once this install's answer is on disk: otherwise, after a restart, the flag alone would make this install look
    // like a reinstall. Waiting for the shared check means a check still saving its answer has finished, and recorded whether
    // the save failed.
    private suspend fun canStoreInstallSeenFlag(): Boolean {
        reinstallCheck.await()
        val answerSaved = savedAppReinstallValueOrNull() != null && !answerSaveFailed
        val flagStored = runCatching { preferences.getBoolean(INSTALL_SEEN_FLAG_STORED_KEY, false) }.getOrDefault(false)
        return answerSaved && !flagStored
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

    /**
     * @return false if the answer may not have reached disk. Android keeps it readable in memory even then.
     */
    private fun saveAppReinstallValue(isReinstall: Boolean): Boolean {
        val saved = try {
            preferences.edit().putBoolean(APP_REINSTALLED_KEY, isReinstall).commit()
        } catch (e: Exception) {
            answerSaveFailed = true
            throw e
        }
        if (!saved) answerSaveFailed = true
        return saved
    }

    // The answer is saved before the folder is created. If only the folder were written, the next launch would find it and
    // answer "reinstall" for a new install; if only the answer is saved, a later reinstall goes undetected, which is the lesser harm.
    private fun detectAndSaveWithDownloadsFolder(): Boolean {
        val downloadDirectory = getDownloadsDirectory()
        val ddgDirectoryExists = downloadDirectory.list()?.asList().orEmpty().contains(DDG_DOWNLOADS_DIRECTORY)
        val isReinstall = ddgDirectoryExists && !wasCreatedAroundThisInstall(File(downloadDirectory, DDG_DOWNLOADS_DIRECTORY))
        val saved = saveAppReinstallValue(isReinstall)
        if (!ddgDirectoryExists && saved) createNewDirectory(DDG_DOWNLOADS_DIRECTORY)
        return isReinstall
    }

    // Some stores launch the app out of sight while installing it, then wipe its private data: Samsung's Galaxy Store does
    // this before the install is committed. The folder created by that launch outlives the wipe, so a folder last changed
    // this close to the install time belongs to this install, not to a previous one.
    private fun wasCreatedAroundThisInstall(directory: File): Boolean {
        val modifiedAt = directory.lastModified()
        val installedAt = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime }.getOrNull()
        if (modifiedAt == 0L || installedAt == null) return false

        val distanceFromInstallMs = abs(modifiedAt - installedAt)
        val createdAroundThisInstall = distanceFromInstallMs <= SAME_INSTALL_WINDOW_MS
        val secondsFromInstall = (modifiedAt - installedAt) / 1000
        logcat(INFO) { "Reinstall check: folder changed ${secondsFromInstall}s from install time, same install=$createdAroundThisInstall" }
        fireRecentFolderPixel(distanceFromInstallMs)
        return createdAroundThisInstall
    }

    // Shows how often the rule above decides the answer, whether its window fits and which stores cause it. Reports up to
    // 10 minutes, wider than the window, so near misses show up too.
    private fun fireRecentFolderPixel(distanceFromInstallMs: Long) {
        val age = when {
            distanceFromInstallMs <= 10_000L -> RecentFolderAge.UP_TO_10_SECONDS
            distanceFromInstallMs <= SAME_INSTALL_WINDOW_MS -> RecentFolderAge.UP_TO_2_MINUTES
            distanceFromInstallMs <= RECENT_FOLDER_REPORT_WINDOW_MS -> RecentFolderAge.UP_TO_10_MINUTES
            else -> return
        }
        pixel.fire(
            ReinstallDetectorPixelName.RECENT_FOLDER,
            parameters = mapOf(PARAM_AGE to age.value, PARAM_INSTALLER to installer().value),
            type = Unique(),
        )
    }

    private fun installer(): RecentFolderInstaller {
        if (Build.VERSION.SDK_INT < 30) return RecentFolderInstaller.OTHER
        val installerPackage = runCatching {
            context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
        }.getOrNull()
        return when (installerPackage) {
            GALAXY_STORE_PACKAGE -> RecentFolderInstaller.GALAXY_STORE
            PLAY_STORE_PACKAGE -> RecentFolderInstaller.PLAY_STORE
            else -> RecentFolderInstaller.OTHER
        }
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
        private const val PARAM_DURATION = "duration"
        private const val PARAM_AGE = "age"
        private const val PARAM_INSTALLER = "installer"
        private const val DDG_DOWNLOADS_DIRECTORY = "DuckDuckGo"
        private const val GALAXY_STORE_PACKAGE = "com.sec.android.app.samsungapps"
        private const val PLAY_STORE_PACKAGE = "com.android.vending"

        // The out-of-sight launch happens seconds before the install is committed. A reinstall within this window counts as new.
        private const val SAME_INSTALL_WINDOW_MS = 2 * 60 * 1000L
        private const val RECENT_FOLDER_REPORT_WINDOW_MS = 10 * 60 * 1000L

        // Nothing else bounds this step: the pre-ATB time limit can't stop it, so a stuck Play services call would hold back the ATB
        private const val BLOCK_STORE_TIME_LIMIT_MS = 10_000L
        private val INSTALL_SEEN_FLAG_VALUE = byteArrayOf(1)
    }
}
