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

package com.duckduckgo.app.pixels

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.browser.mode.AppLauncher
import com.duckduckgo.app.browser.mode.AppShortcutBookmarks
import com.duckduckgo.app.browser.mode.AppShortcutDuckAi
import com.duckduckgo.app.browser.mode.AppShortcutNewTab
import com.duckduckgo.app.browser.mode.BrowserLaunchSource
import com.duckduckgo.app.browser.mode.DuckAiPinShortcut
import com.duckduckgo.app.browser.mode.ExternalUrl
import com.duckduckgo.app.browser.mode.FavoritesWidget
import com.duckduckgo.app.browser.mode.FireRestart
import com.duckduckgo.app.browser.mode.InAppNavigation
import com.duckduckgo.app.browser.mode.Onboarding
import com.duckduckgo.app.browser.mode.PinnedPageShortcut
import com.duckduckgo.app.browser.mode.PrivacyNotification
import com.duckduckgo.app.browser.mode.SearchWidgetDuckAi
import com.duckduckgo.app.browser.mode.SelectedTextSearch
import com.duckduckgo.app.browser.mode.SystemSearchExternal
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.fire.UnsentForgetAllPixelStore
import com.duckduckgo.app.generalsettings.showonapplaunch.IdleThresholdResolver
import com.duckduckgo.app.generalsettings.showonapplaunch.model.ShowOnAppLaunchOption
import com.duckduckgo.app.generalsettings.showonapplaunch.store.ShowOnAppLaunchOptionDataStore
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.browser.feature.toggles.AndroidBrowserConfigFeature
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.DuckAiFeatureState
import com.duckduckgo.duckchat.api.DuckChatInputModeState
import com.duckduckgo.duckchat.api.nativeinput.NativeInputState
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Provider

/**
 * Fires `m_app_return` on every foreground return, given the [LaunchSourceValues] classification
 * of whatever triggered it.
 */
interface AppReturnPixelSender {
    fun fireIfNeeded(launchSource: String)

    /** Whether the current process launch was caused by the Fire Button's automatic restart. */
    fun isLaunchByFireAction(): Boolean
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = AppReturnPixelSender::class)
@ContributesMultibinding(AppScope::class, boundType = MainProcessLifecycleObserver::class)
class RealAppReturnPixelSender @Inject constructor(
    private val pixel: Provider<Pixel>,
    private val settingsDataStore: SettingsDataStore,
    private val idleThresholdResolver: IdleThresholdResolver,
    private val showOnAppLaunchOptionDataStore: ShowOnAppLaunchOptionDataStore,
    private val duckChatInputModeState: DuckChatInputModeState,
    private val duckAiFeatureState: DuckAiFeatureState,
    private val androidBrowserConfigFeature: AndroidBrowserConfigFeature,
    private val unsentForgetAllPixelStore: UnsentForgetAllPixelStore,
    private val dispatchers: DispatcherProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : AppReturnPixelSender, MainProcessLifecycleObserver {

    private var fired = false
    private var fireButtonRestartHandled = false

    override fun onStop(owner: LifecycleOwner) {
        fired = false
    }

    override fun fireIfNeeded(launchSource: String) {
        if (!fireButtonRestartHandled && isLaunchByFireAction()) {
            // Only the very first resume after a fire-triggered process restart is skipped.
            // Once handled, later resumes must be evaluated as real returns even if still inside the grace window.
            fireButtonRestartHandled = true
            fired = true
            return
        }
        if (fired) return
        fired = true

        appCoroutineScope.launch(dispatchers.io()) {
            val lastBackgrounded = settingsDataStore.lastSessionBackgroundTimestamp
            val elapsedMs = System.currentTimeMillis() - lastBackgrounded
            val idleThresholdSeconds = idleThresholdResolver.effectiveThresholdSeconds(settingsDataStore.userSelectedIdleThresholdSeconds)
            val exceededIdleThreshold = lastBackgrounded != 0L && elapsedMs >= idleThresholdSeconds * 1000
            val afterInactivityOption = showOnAppLaunchOptionDataStore.optionFlow.firstOrNull()
                ?.let { ShowOnAppLaunchOption.getDailyPixelValue(it) }
            val toggleVisible = duckChatInputModeState.inputModeCapability.value == NativeInputState.InputMode.SEARCH_AND_DUCK_AI

            val params = buildMap {
                put(AppReturnPixelParameters.TIME_AWAY_BUCKET, timeAwayBucket(lastBackgrounded, elapsedMs))
                put(AppReturnPixelParameters.EXCEEDED_IDLE_THRESHOLD, exceededIdleThreshold.toString())
                put(AppReturnPixelParameters.IDLE_THRESHOLD_SECONDS, idleThresholdSeconds.toString())
                afterInactivityOption?.let { put(AppReturnPixelParameters.AFTER_INACTIVITY_OPTION, it) }
                put(AppReturnPixelParameters.FEATURE_ELIGIBLE, androidBrowserConfigFeature.showNTPAfterIdleReturn().isEnabled().toString())
                put(AppReturnPixelParameters.UNIFIED_INPUT_AVAILABLE, duckAiFeatureState.nativeInputFieldEnabled.value.toString())
                put(AppReturnPixelParameters.TOGGLE_VISIBLE, toggleVisible.toString())
                put(AppReturnPixelParameters.LAUNCH_SOURCE, launchSource)
                put(AppReturnPixelParameters.PETAL, PetalValues.RANDOMIZE)
            }

            pixel.get().fire(pixel = AppPixelName.APP_RETURN_COUNT, parameters = params)
            pixel.get().fire(pixel = AppPixelName.APP_RETURN_DAILY, parameters = params, type = Pixel.PixelType.Daily())
        }
    }

    private fun timeAwayBucket(lastBackgrounded: Long, elapsedMs: Long): String = when {
        lastBackgrounded == 0L -> "cold_start"
        elapsedMs < 60_000L -> "lt_1m"
        elapsedMs < 300_000L -> "1_5m"
        elapsedMs < 900_000L -> "5_15m"
        elapsedMs < 1_800_000L -> "15_30m"
        elapsedMs < 3_600_000L -> "30_60m"
        else -> "gt_60m"
    }

    override fun isLaunchByFireAction(): Boolean {
        val timeDifferenceMillis = System.currentTimeMillis() - unsentForgetAllPixelStore.lastClearTimestamp
        return timeDifferenceMillis <= APP_RESTART_CAUSED_BY_FIRE_GRACE_PERIOD
    }

    private companion object {
        const val APP_RESTART_CAUSED_BY_FIRE_GRACE_PERIOD: Long = 10_000L
    }
}

object AppReturnPixelParameters {
    const val TIME_AWAY_BUCKET = "time_away_bucket"
    const val EXCEEDED_IDLE_THRESHOLD = "exceeded_idle_threshold"
    const val IDLE_THRESHOLD_SECONDS = "idle_threshold_seconds"
    const val AFTER_INACTIVITY_OPTION = "after_inactivity_option"
    const val FEATURE_ELIGIBLE = "feature_eligible"
    const val UNIFIED_INPUT_AVAILABLE = "unified_input_available"
    const val TOGGLE_VISIBLE = "toggle_visible"
    const val LAUNCH_SOURCE = "launch_source"
    const val PETAL = "petal"
}

object PetalValues {
    const val RANDOMIZE = "randomize"
    const val KANON = "kanon"
}

object LaunchSourceValues {
    const val STANDARD = "standard"
    const val URL = "url"
    const val SHORTCUT = "shortcut"
    const val WIDGET = "widget"
    const val OTHER = "other"
}

fun BrowserLaunchSource.toPixelLaunchSourceValue(): String = when (this) {
    AppLauncher -> LaunchSourceValues.STANDARD
    ExternalUrl -> LaunchSourceValues.URL
    AppShortcutNewTab, AppShortcutBookmarks, AppShortcutDuckAi, PinnedPageShortcut, DuckAiPinShortcut -> LaunchSourceValues.SHORTCUT
    FavoritesWidget, SearchWidgetDuckAi -> LaunchSourceValues.WIDGET
    SelectedTextSearch, PrivacyNotification, SystemSearchExternal, Onboarding, FireRestart, InAppNavigation -> LaunchSourceValues.OTHER
}
