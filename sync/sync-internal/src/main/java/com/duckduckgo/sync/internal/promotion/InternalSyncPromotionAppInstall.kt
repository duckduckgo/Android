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

package com.duckduckgo.sync.internal.promotion

import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.impl.promotion.RealSyncPromotionAppInstall
import com.duckduckgo.sync.impl.promotion.SyncPromotionAppInstall
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@SingleIn(AppScope::class)
@ContributesBinding(scope = AppScope::class, replaces = [RealSyncPromotionAppInstall::class])
class InternalSyncPromotionAppInstall @Inject constructor(
    private val real: RealSyncPromotionAppInstall,
    private val currentTimeProvider: CurrentTimeProvider,
    private val sharedPreferencesProvider: SharedPreferencesProvider,
) : SyncPromotionAppInstall {

    private val preferences: SharedPreferences by lazy { sharedPreferencesProvider.getSharedPreferences(FILENAME) }

    private val mutableInstallationTimeOverrideFlow by lazy {
        val storedMillis = preferences.getLong(KEY_INSTALLATION_TIME_OVERRIDE, NO_OVERRIDE)
        MutableStateFlow(if (storedMillis == NO_OVERRIDE) null else Instant.ofEpochMilli(storedMillis))
    }
    val installationTimeOverrideFlow: StateFlow<Instant?> get() = mutableInstallationTimeOverrideFlow.asStateFlow()

    override suspend fun getInstallAge(): Duration? {
        val installationTime = installationTimeOverrideFlow.value ?: return real.getInstallAge()
        val installAgeMillis = currentTimeProvider.currentTimeMillis() - installationTime.toEpochMilli()
        return if (installAgeMillis < 0) null else installAgeMillis.milliseconds
    }

    fun setInstallationTimeOverride(time: Instant?) {
        preferences.edit {
            if (time == null) remove(KEY_INSTALLATION_TIME_OVERRIDE) else putLong(KEY_INSTALLATION_TIME_OVERRIDE, time.toEpochMilli())
        }
        mutableInstallationTimeOverrideFlow.value = time
    }

    fun defaultInstallationTime(): Instant = real.getInstallationTime()

    private companion object {
        const val FILENAME = "com.duckduckgo.sync.internal.promotion.app.install"
        const val KEY_INSTALLATION_TIME_OVERRIDE = "installation_time_override"
        const val NO_OVERRIDE = -1L
    }
}
