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

package com.duckduckgo.experiments.impl.reinstalls

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.core.content.edit
import com.duckduckgo.app.statistics.AtbInitializerListener
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.anvil.annotations.ContributesTo
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn
import javax.inject.Inject
import javax.inject.Qualifier
import kotlinx.coroutines.withContext
import timber.log.Timber

@SingleInstanceIn(AppScope::class)
@ContributesMultibinding(AppScope::class)
class ReinstallAtbListener @Inject constructor(
    private val backupDataStore: BackupServiceDataStore,
    private val statisticsDataStore: StatisticsDataStore,
    private val appBuildConfig: AppBuildConfig,
    private val downloadsDirectoryManager: DownloadsDirectoryManager,
    @ReinstallSharedPrefs private val reinstallSharedPrefs: Lazy<SharedPreferences>,
    private val dispatcherProvider: DispatcherProvider,
) : AtbInitializerListener {

    override suspend fun beforeAtbInit() = withContext(dispatcherProvider.io()) {
        backupDataStore.clearBackupPreferences()

        if (appBuildConfig.sdkInt >= Build.VERSION_CODES.R && !reinstallSharedPrefs.get().isReturningUserChecked()) {
            val downloadDirectory = downloadsDirectoryManager.getDownloadsDirectory()

            val downloadFiles = downloadDirectory.list()?.asList() ?: emptyList()
            val ddgDirectoryExists = downloadFiles.contains(DDG_DOWNLOADS_DIRECTORY)
            if (ddgDirectoryExists) {
                statisticsDataStore.variant = REINSTALL_VARIANT
                Timber.i("Variant update for returning user")
            } else {
                downloadsDirectoryManager.createNewDirectory(DDG_DOWNLOADS_DIRECTORY)
            }
            reinstallSharedPrefs.get().setReturningUserChecked()
        }
    }

    override fun beforeAtbInitTimeoutMillis(): Long = MAX_REINSTALL_WAIT_TIME_MS

    private fun SharedPreferences.isReturningUserChecked(): Boolean {
        return getBoolean(RETURNING_USER_CHECKED_TAG, false)
    }

    private fun SharedPreferences.setReturningUserChecked() {
        this.edit(commit = true) { putBoolean(RETURNING_USER_CHECKED_TAG, true) }
    }

    companion object {
        private const val MAX_REINSTALL_WAIT_TIME_MS = 1_500L
        private const val DDG_DOWNLOADS_DIRECTORY = "DuckDuckGo"
        private const val RETURNING_USER_CHECKED_TAG = "RETURNING_USER_CHECKED_TAG"
    }
}

internal const val REINSTALL_VARIANT = "ru"

@Retention(AnnotationRetention.BINARY)
@Qualifier
private annotation class ReinstallSharedPrefs

@Module
@ContributesTo(AppScope::class)
class ReinstallAtbListenerModule {
    @Provides
    @ReinstallSharedPrefs
    fun provideReinstallSharedPrefs(context: Context): SharedPreferences {
        val filename = "com.duckduckgo.experiments.impl.reinstalls.store.v1"
        return context.getSharedPreferences(filename, Context.MODE_PRIVATE)
    }
}
