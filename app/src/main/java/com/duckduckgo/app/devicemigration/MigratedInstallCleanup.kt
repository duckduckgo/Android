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

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.browser.defaultbrowsing.prompts.store.DefaultBrowserPromptsAppUsageDao
import com.duckduckgo.app.browser.defaultbrowsing.prompts.store.DefaultBrowserPromptsDataStore
import com.duckduckgo.app.browser.defaultbrowsing.prompts.store.DefaultBrowserPromptsDataStore.Stage.NOT_STARTED
import com.duckduckgo.app.browser.defaultbrowsing.prompts.store.DefaultBrowserPromptsDataStore.Stage.STOPPED
import com.duckduckgo.app.browser.pageloadpixel.PageLoadedPixelDao
import com.duckduckgo.app.browser.pageloadpixel.firstpaint.PagePaintedPixelDao
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.devicemigration.api.DeviceMigration
import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority.ERROR
import logcat.logcat
import javax.inject.Inject

/**
 * Page-load pixel queues and default-browser prompt state describe the previous phone, so they are dropped once after a device
 * transfer. The pending pixel queue is cleared by the pixel sender itself, before its first send.
 */
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<MainProcessLifecycleObserver>())
class MigratedInstallCleanup @Inject constructor(
    private val deviceMigration: DeviceMigration,
    private val pageLoadedPixelDao: PageLoadedPixelDao,
    private val pagePaintedPixelDao: PagePaintedPixelDao,
    private val defaultBrowserPromptsAppUsageDao: DefaultBrowserPromptsAppUsageDao,
    private val appInstallStore: AppInstallStore,
    private val defaultBrowserPromptsDataStore: DefaultBrowserPromptsDataStore,
    private val dispatchers: DispatcherProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : MainProcessLifecycleObserver {

    override fun onCreate(owner: LifecycleOwner) {
        appCoroutineScope.launch(dispatchers.io()) {
            if (deviceMigration.isDoneOnThisInstall(DONE_KEY)) return@launch
            if (!deviceMigration.isMigratedInstall()) return@launch
            if (!deviceMigration.isMigrationExperienceEnabled()) return@launch

            try {
                pageLoadedPixelDao.deleteAll()
                pagePaintedPixelDao.deleteAll()
                defaultBrowserPromptsAppUsageDao.deleteAll()
                restartDefaultBrowserPrompts()
                appInstallStore.newDefaultBrowserDialogCount = 0
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(ERROR) { "Migrated install cleanup failed, will retry on next start: ${e.message}" }
                return@launch
            }
            deviceMigration.markDoneOnThisInstall(DONE_KEY)
        }
    }

    /** True while the previous phone's state is still in place on a migrated install that will clean it up. */
    suspend fun isPending(): Boolean = withContext(dispatchers.io()) {
        !deviceMigration.isDoneOnThisInstall(DONE_KEY) &&
            deviceMigration.isMigratedInstall() &&
            deviceMigration.isMigrationExperienceEnabled()
    }

    // A stage the user stopped is kept: it may be their "do not ask again" choice.
    private suspend fun restartDefaultBrowserPrompts() {
        if (defaultBrowserPromptsDataStore.stage.firstOrNull() != STOPPED) {
            defaultBrowserPromptsDataStore.storeStage(NOT_STARTED)
        }
        defaultBrowserPromptsDataStore.storeShowSetAsDefaultPopupMenuItemState(false)
        defaultBrowserPromptsDataStore.storeHighlightPopupMenuState(false)
        defaultBrowserPromptsDataStore.storeShowSetAsDefaultMessageState(false)
    }

    private companion object {
        const val DONE_KEY = "app.migratedInstallCleanup"
    }
}
