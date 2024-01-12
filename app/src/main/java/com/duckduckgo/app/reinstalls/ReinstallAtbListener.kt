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

import android.os.Build
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.statistics.AtbInitializerListener
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import timber.log.Timber

@SingleInstanceIn(AppScope::class)
@ContributesMultibinding(AppScope::class)
class ReinstallAtbListener @Inject constructor(
    private val backupDataStore: BackupServiceDataStore,
    private val statisticsDataStore: StatisticsDataStore,
    private val appInstallStore: AppInstallStore,
    private val appBuildConfig: AppBuildConfig,
    private val downloadsDirectoryManager: DownloadsDirectoryManager,
) : AtbInitializerListener {

    override suspend fun beforeAtbInit() {
        backupDataStore.clearBackupPreferences()

        if (appBuildConfig.sdkInt >= Build.VERSION_CODES.R && !appInstallStore.returningUserChecked) {
            val downloadDirectory = downloadsDirectoryManager.getDownloadsDirectory()

            val downloadFiles = downloadDirectory.list()?.asList() ?: emptyList()
            val ddgDirectoryExists = downloadFiles.contains(DDG_DOWNLOADS_DIRECTORY)
            if (ddgDirectoryExists) {
                statisticsDataStore.variant = REINSTALL_VARIANT
                Timber.i("Variant update for returning user")
            } else {
                downloadsDirectoryManager.createNewDirectory(DDG_DOWNLOADS_DIRECTORY)
            }
            appInstallStore.returningUserChecked = true
        }
    }

    override fun beforeAtbInitTimeoutMillis(): Long = MAX_REINSTALL_WAIT_TIME_MS

    companion object {
        private const val MAX_REINSTALL_WAIT_TIME_MS = 1_500L

        const val REINSTALL_VARIANT = "ru"
        private const val DDG_DOWNLOADS_DIRECTORY = "DuckDuckGo"
    }
}
