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

import com.duckduckgo.anvil.annotations.PriorityKey
import com.duckduckgo.app.statistics.AtbInitializerListener
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.moshi.Moshi
import dagger.SingleInstanceIn
import kotlinx.coroutines.withContext
import logcat.LogPriority.INFO
import logcat.logcat
import javax.inject.Inject

@SingleInstanceIn(AppScope::class)
@ContributesMultibinding(AppScope::class)
@PriorityKey(AtbInitializerListener.PRIORITY_REINSTALL_LISTENER)
class ReinstallAtbListener @Inject constructor(
    private val backupDataStore: BackupServiceDataStore,
    private val statisticsDataStore: StatisticsDataStore,
    private val appBuildConfig: AppBuildConfig,
    private val dispatcherProvider: DispatcherProvider,
    private val reinstallerVariantProtectionFeature: ReinstallerVariantProtectionFeature,
    moshi: Moshi,
) : AtbInitializerListener {

    private val settingsAdapter by lazy { moshi.adapter(ReinstallerVariantProtectionSettings::class.java) }

    override suspend fun beforeAtbInit() = withContext(dispatcherProvider.io()) {
        backupDataStore.clearBackupPreferences()

        if (appBuildConfig.isAppReinstall()) {
            val currentVariant = statisticsDataStore.variant
            if (currentVariant == null || currentVariant !in getProtectedVariants()) {
                statisticsDataStore.variant = REINSTALL_VARIANT
                logcat(INFO) { "Variant update for returning user" }
            } else {
                logcat(INFO) { "Skipping variant update for returning user: current variant '$currentVariant' is protected" }
            }
        }
    }

    private fun getProtectedVariants(): List<String> {
        if (!reinstallerVariantProtectionFeature.self().isEnabled()) return emptyList()
        val json = reinstallerVariantProtectionFeature.self().getSettings() ?: return emptyList()
        return runCatching { settingsAdapter.fromJson(json)?.variants }.getOrNull().orEmpty()
    }

    override fun beforeAtbInitTimeoutMillis(): Long = MAX_REINSTALL_WAIT_TIME_MS

    companion object {
        private const val MAX_REINSTALL_WAIT_TIME_MS = 1_500L
    }
}

internal const val REINSTALL_VARIANT = "ru"
