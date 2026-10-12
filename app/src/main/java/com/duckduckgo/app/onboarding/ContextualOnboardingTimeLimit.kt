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

package com.duckduckgo.app.onboarding

import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.app.onboarding.store.AppStage
import com.duckduckgo.app.onboarding.store.UserStageStore
import com.duckduckgo.app.onboarding.store.daxOnboardingActive
import com.duckduckgo.app.onboarding.ui.page.extendedonboarding.ExtendedOnboardingFeatureToggles
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.Toggle
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import logcat.logcat
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** Ends contextual onboarding once the user is past a days-since-install or app-opens limit. */
interface ContextualOnboardingTimeLimit {
    suspend fun endOnboardingIfTimeLimitReached()

    suspend fun wasOnboardingEndedByTimeLimit(): Boolean

    /** For re-running onboarding from the internal dev settings. */
    suspend fun restartTimeLimit()
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class, ContextualOnboardingTimeLimit::class)
@ContributesMultibinding(
    scope = AppScope::class,
    boundType = MainProcessLifecycleObserver::class,
)
class RealContextualOnboardingTimeLimit @Inject constructor(
    private val userStageStore: UserStageStore,
    private val appInstallStore: AppInstallStore,
    private val extendedOnboardingFeatureToggles: ExtendedOnboardingFeatureToggles,
    private val sharedPreferencesProvider: SharedPreferencesProvider,
    private val currentTimeProvider: CurrentTimeProvider,
    private val dispatchers: DispatcherProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : ContextualOnboardingTimeLimit, MainProcessLifecycleObserver {

    private val preferences: SharedPreferences by lazy { sharedPreferencesProvider.getSharedPreferences(FILENAME) }
    private val mutex = Mutex()

    override fun onStart(owner: LifecycleOwner) {
        appCoroutineScope.launch(dispatchers.io()) {
            countAppOpen()
            endOnboardingIfTimeLimitReached()
        }
    }

    override suspend fun endOnboardingIfTimeLimitReached() {
        withContext(dispatchers.io()) {
            mutex.withLock {
                if (isTimeLimitReached()) {
                    logcat { "Contextual onboarding time limit reached, ending onboarding" }
                    preferences.edit { putBoolean(KEY_ENDED_BY_TIME_LIMIT, true) }
                    userStageStore.stageCompleted(AppStage.DAX_ONBOARDING)
                }
            }
        }
    }

    override suspend fun wasOnboardingEndedByTimeLimit(): Boolean = withContext(dispatchers.io()) {
        preferences.getBoolean(KEY_ENDED_BY_TIME_LIMIT, false)
    }

    override suspend fun restartTimeLimit() {
        withContext(dispatchers.io()) {
            mutex.withLock {
                preferences.edit {
                    putLong(KEY_TIME_LIMIT_START, currentTimeProvider.currentTimeMillis())
                    putInt(KEY_APP_OPENS, 0)
                    putBoolean(KEY_ENDED_BY_TIME_LIMIT, false)
                }
            }
        }
    }

    private suspend fun countAppOpen() {
        mutex.withLock {
            // Counted from first launch, so both limits run from install.
            if (userStageStore.getUserAppStage() != AppStage.ESTABLISHED) {
                preferences.edit { putInt(KEY_APP_OPENS, preferences.getInt(KEY_APP_OPENS, 0) + 1) }
            }
        }
    }

    private suspend fun isTimeLimitReached(): Boolean {
        if (!userStageStore.daxOnboardingActive()) return false
        val toggle = extendedOnboardingFeatureToggles.contextualOnboardingTimeLimit()
        if (!toggle.isEnabled()) return false

        val limits = toggle.limits()
        return isDaysLimitReached(limits.maxDaysSinceInstall) || preferences.getInt(KEY_APP_OPENS, 0) > limits.maxAppOpens
    }

    private fun isDaysLimitReached(maxDaysSinceInstall: Int): Boolean {
        val start = timeLimitStart() ?: return false
        return currentTimeProvider.currentTimeMillis() - start >= TimeUnit.DAYS.toMillis(maxDaysSinceInstall.toLong())
    }

    private fun timeLimitStart(): Long? = when {
        preferences.contains(KEY_TIME_LIMIT_START) -> preferences.getLong(KEY_TIME_LIMIT_START, 0L)
        appInstallStore.hasInstallTimestampRecorded() -> appInstallStore.installTimestamp
        else -> null
    }

    private fun Toggle.limits(): Limits {
        val settings = runCatching { getSettings()?.let { JSONObject(it) } }.getOrNull()
        return Limits(
            maxDaysSinceInstall = settings.positiveIntOrDefault(KEY_MAX_DAYS_SINCE_INSTALL, DEFAULT_MAX_DAYS_SINCE_INSTALL),
            maxAppOpens = settings.positiveIntOrDefault(KEY_MAX_APP_OPENS, DEFAULT_MAX_APP_OPENS),
        )
    }

    private fun JSONObject?.positiveIntOrDefault(key: String, default: Int): Int =
        this?.optInt(key, default)?.takeIf { it > 0 } ?: default

    private data class Limits(
        val maxDaysSinceInstall: Int,
        val maxAppOpens: Int,
    )

    companion object {
        private const val FILENAME = "com.duckduckgo.app.onboarding.contextual.timelimit"
        private const val KEY_APP_OPENS = "appOpens"
        private const val KEY_ENDED_BY_TIME_LIMIT = "endedByTimeLimit"
        private const val KEY_TIME_LIMIT_START = "timeLimitStart"
        private const val KEY_MAX_DAYS_SINCE_INSTALL = "maxDaysSinceInstall"
        private const val KEY_MAX_APP_OPENS = "maxAppOpens"
        private const val DEFAULT_MAX_DAYS_SINCE_INSTALL = 7
        private const val DEFAULT_MAX_APP_OPENS = 28
    }
}
