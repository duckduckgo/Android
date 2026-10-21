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

import com.duckduckgo.app.statistics.AtbInitializerListener
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.codegen.annotations.PriorityKey
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import logcat.LogPriority.INFO
import logcat.logcat
import javax.inject.Inject

@SingleIn(AppScope::class)
@ContributesIntoMap(AppScope::class)
@PriorityKey(AtbInitializerListener.PRIORITY_REINSTALL_LISTENER)
class ReinstallAtbListener @Inject constructor(
    private val backupDataStore: BackupServiceDataStore,
    private val statisticsDataStore: StatisticsDataStore,
    private val appBuildConfig: AppBuildConfig,
    private val dispatcherProvider: DispatcherProvider,
    private val reinstallerVariantProtectionFeature: ReinstallerVariantProtectionFeature,
    private val pixel: Pixel,
    private val currentTimeProvider: CurrentTimeProvider,
    moshi: Moshi,
) : AtbInitializerListener {

    private val settingsAdapter by lazy {
        moshi.newBuilder().add(KotlinJsonAdapterFactory()).build()
            .adapter(ReinstallerVariantProtectionSettings::class.java)
    }

    // The ATB is only sent once this returns, so finishing past the time limit costs nothing. Being cancelled would drop
    // "ru" for a returning user: the check can still be waiting on another caller, or already be done and saved.
    override suspend fun beforeAtbInit() = withContext(dispatcherProvider.io() + NonCancellable) {
        val startedAt = currentTimeProvider.elapsedRealtime()
        backupDataStore.clearBackupPreferences()

        val isReinstall = appBuildConfig.isAppReinstall()
        if (isReinstall) {
            val currentVariant = statisticsDataStore.variant
            if (currentVariant == null || currentVariant !in getProtectedVariants()) {
                statisticsDataStore.variant = REINSTALL_VARIANT
                logcat(INFO) { "Variant update for returning user" }
            } else {
                logcat(INFO) { "Skipping variant update for returning user: current variant '$currentVariant' is protected" }
            }
        }

        val durationMillis = currentTimeProvider.elapsedRealtime() - startedAt
        if (durationMillis > MAX_REINSTALL_WAIT_TIME_MS) {
            pixel.fire(
                ReinstallPixelName.REINSTALL_CHECK_SLOW,
                parameters = mapOf(
                    PARAM_RETURNING_USER to isReinstall.toString(),
                    PARAM_DURATION to durationBucket(durationMillis),
                ),
            )
        }
    }

    private fun durationBucket(durationMillis: Long): String = when {
        durationMillis <= 3_000L -> "1.5-3"
        durationMillis <= 5_000L -> "3-5"
        durationMillis <= 10_000L -> "5-10"
        durationMillis <= 30_000L -> "10-30"
        else -> "30+"
    }

    private fun getProtectedVariants(): List<String> =
        if (reinstallerVariantProtectionFeature.self().isEnabled() && reinstallerVariantProtectionFeature.protectVariants().isEnabled()) {
            val json = reinstallerVariantProtectionFeature.protectVariants().getSettings()
            runCatching { json?.let { settingsAdapter.fromJson(it)?.variants } }.getOrNull().orEmpty()
        } else {
            emptyList()
        }

    override fun beforeAtbInitTimeoutMillis(): Long = MAX_REINSTALL_WAIT_TIME_MS

    companion object {
        private const val MAX_REINSTALL_WAIT_TIME_MS = 1_500L
        private const val PARAM_RETURNING_USER = "returning_user"
        private const val PARAM_DURATION = "duration"
    }
}

internal const val REINSTALL_VARIANT = "ru"
