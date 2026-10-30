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

package com.duckduckgo.app.devicemigration

import androidx.core.content.edit
import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.browser.defaultbrowsing.prompts.store.DefaultBrowserPromptsAppUsageDao
import com.duckduckgo.app.browser.pageloadpixel.PageLoadedPixelDao
import com.duckduckgo.app.browser.pageloadpixel.firstpaint.PagePaintedPixelDao
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.app.statistics.store.PendingPixelDao
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.devicemigration.api.DeviceMigration
import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.LogPriority.ERROR
import logcat.logcat
import javax.inject.Inject

/**
 * Pixel queues and default-browser prompt state describe the previous phone, so they are dropped once after a device transfer.
 * The guard preference file is excluded from transfer, otherwise a chained transfer would skip the cleanup.
 */
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<MainProcessLifecycleObserver>())
class MigratedInstallCleanup @Inject constructor(
    private val deviceMigration: DeviceMigration,
    private val pendingPixelDao: PendingPixelDao,
    private val pageLoadedPixelDao: PageLoadedPixelDao,
    private val pagePaintedPixelDao: PagePaintedPixelDao,
    private val defaultBrowserPromptsAppUsageDao: DefaultBrowserPromptsAppUsageDao,
    private val appInstallStore: AppInstallStore,
    private val sharedPreferencesProvider: SharedPreferencesProvider,
    private val dispatchers: DispatcherProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : MainProcessLifecycleObserver {

    private val preferences by lazy {
        sharedPreferencesProvider.getSharedPreferences(PREFERENCES_FILE_NAME, false, false)
    }

    override fun onCreate(owner: LifecycleOwner) {
        appCoroutineScope.launch(dispatchers.io()) {
            if (preferences.getBoolean(KEY_CLEANUP_DONE, false)) return@launch
            if (!deviceMigration.isMigratedInstall()) return@launch

            try {
                pendingPixelDao.deleteAll()
                pageLoadedPixelDao.deleteAll()
                pagePaintedPixelDao.deleteAll()
                defaultBrowserPromptsAppUsageDao.deleteAll()
                appInstallStore.newDefaultBrowserDialogCount = 0
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(ERROR) { "Migrated install cleanup failed, will retry on next start: ${e.message}" }
                return@launch
            }
            preferences.edit(commit = true) { putBoolean(KEY_CLEANUP_DONE, true) }
        }
    }

    private companion object {
        const val PREFERENCES_FILE_NAME = "com.duckduckgo.app.devicemigration.cleanup"
        const val KEY_CLEANUP_DONE = "cleanupDone"
    }
}
