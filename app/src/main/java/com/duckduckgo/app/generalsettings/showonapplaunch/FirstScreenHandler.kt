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

package com.duckduckgo.app.generalsettings.showonapplaunch

import androidx.core.net.toUri
import com.duckduckgo.app.browser.autofill.SystemAutofillEngagement
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.generalsettings.showonapplaunch.model.ShowOnAppLaunchOption.NewTabPage
import com.duckduckgo.app.generalsettings.showonapplaunch.store.ShowOnAppLaunchOptionDataStore
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.browser.api.BrowserLifecycleObserver
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.customtabs.api.CustomTabDetector
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.newtabpage.api.NtpAfterIdleManager
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.logcat
import org.json.JSONObject
import javax.inject.Inject

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = BrowserLifecycleObserver::class,
)
@SingleInstanceIn(AppScope::class)
class FirstScreenHandlerImpl @Inject constructor(
    private val androidBrowserConfigFeature: AndroidBrowserConfigFeature,
    private val showOnAppLaunchFeature: ShowOnAppLaunchFeature,
    private val settingsDataStore: SettingsDataStore,
    private val showOnAppLaunchOptionHandler: ShowOnAppLaunchOptionHandler,
    private val showOnAppLaunchOptionDataStore: ShowOnAppLaunchOptionDataStore,
    private val appBuildConfig: AppBuildConfig,
    private val dispatcherProvider: DispatcherProvider,
    private val duckChat: DuckChat,
    private val tabRepository: TabRepository,
    private val ntpAfterIdleManager: NtpAfterIdleManager,
    private val systemAutofillEngagement: SystemAutofillEngagement,
    private val customTabDetector: CustomTabDetector,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : BrowserLifecycleObserver {

    override fun onOpen(isFreshLaunch: Boolean) {
        // Notify the NtpAfterIdleManager synchronously when the currently selected tab is already
        // an NTP: BrowserViewModel's flowSelectedTab subscription can fire onNtpShown immediately
        // on activity recreation, and the async handler path below doesn't run in time to classify
        // it. Gated on "already on NTP" so LastOpenedTab/SpecificPage users on a URL tab don't
        // leave a stale pendingAfterIdle flag behind for a later user-initiated NTP.
        if (androidBrowserConfigFeature.showNTPAfterIdleReturn().isEnabled() &&
            computeWasIdle() &&
            isCurrentSelectedTabNtp()
        ) {
            ntpAfterIdleManager.onIdleReturnTriggered()
        }
        appCoroutineScope.launch {
            logcat { "FirstScreen: onOpen isFreshLaunch $isFreshLaunch" }
            // Persist the new-user default eagerly so screens that read optionFlow
            // (e.g. GeneralSettings) don't fall back to LastOpenedTab before the
            // after-inactivity flow has had a chance to run.
            ensureNewUserDefault()
            handleFirstScreen(isFreshLaunch)
        }
    }

    private suspend fun ensureNewUserDefault() {
        val ntpAfterIdleEnabled = androidBrowserConfigFeature.showNTPAfterIdleReturn().isEnabled()
        if (ntpAfterIdleEnabled && appBuildConfig.isNewInstall() && !showOnAppLaunchOptionDataStore.hasOptionSelected()) {
            logcat { "FirstScreen: setting New Tab for new users" }
            showOnAppLaunchOptionDataStore.setShowOnAppLaunchOption(NewTabPage)
        }
    }

    private fun isCurrentSelectedTabNtp(): Boolean {
        return tabRepository.liveSelectedTab.value?.url.isNullOrBlank()
    }

    private fun computeWasIdle(): Boolean {
        val timeoutMs = getTimeoutSeconds() * 1000
        val lastBackgrounded = settingsDataStore.lastSessionBackgroundTimestamp
        val elapsed = System.currentTimeMillis() - lastBackgrounded
        return lastBackgrounded != 0L && elapsed >= timeoutMs
    }

    private suspend fun handleFirstScreen(isFreshLaunch: Boolean) {
        if (androidBrowserConfigFeature.showNTPAfterIdleReturn().isEnabled()) {
            val lastBackgrounded = settingsDataStore.lastSessionBackgroundTimestamp
            val wasIdle = computeWasIdle()
            if (lastBackgrounded == 0L || wasIdle) {
                if (!isVoiceSessionActiveOnCurrentTab() && !isActiveTabCustomTab()) {
                    showOnAppLaunchOptionHandler.handleAfterInactivityOption(wasIdle = wasIdle)
                }
                return
            }
        } else if (isFreshLaunch && showOnAppLaunchFeature.self().isEnabled()) {
            if (!isVoiceSessionActiveOnCurrentTab()) {
                showOnAppLaunchOptionHandler.handleAppLaunchOption()
            }
        }
    }

    private suspend fun isVoiceSessionActiveOnCurrentTab(): Boolean = withContext(dispatcherProvider.io()) {
        if (!duckChat.isVoiceSessionActive()) return@withContext false
        val selectedTab = tabRepository.getSelectedTab()
        return@withContext selectedTab?.url?.toUri()?.let {
            duckChat.isDuckChatUrl(it)
        } == true
    }

    private suspend fun isActiveTabCustomTab(): Boolean = withContext(dispatcherProvider.io()) {
        return@withContext customTabDetector.isCustomTab()
    }

    override fun onClose() {
        systemAutofillEngagement.clearIdleReturnTriggered()
        appCoroutineScope.launch(dispatcherProvider.io()) {
            settingsDataStore.lastSessionBackgroundTimestamp = System.currentTimeMillis()
        }
    }

    private fun getTimeoutSeconds(): Long {
        val userPref = settingsDataStore.userSelectedIdleThresholdSeconds
        if (userPref != null) return userPref

        return parseDefaultIdleThresholdSeconds(androidBrowserConfigFeature.showNTPAfterIdleReturn().getSettings())
            ?: DEFAULT_IDLE_THRESHOLD_SECONDS
    }

    companion object {
        const val DEFAULT_IDLE_THRESHOLD_SECONDS = 300L
        val DEFAULT_IDLE_THRESHOLD_OPTIONS = listOf(0L, 60L, 300L, 600L, 1800L, 3600L, 43200L, 86400L)

        fun parseDefaultIdleThresholdSeconds(settingsJson: String?): Long? {
            if (settingsJson == null) return null
            return try {
                val json = JSONObject(settingsJson)
                if (json.has("defaultIdleThresholdSeconds")) json.getLong("defaultIdleThresholdSeconds") else null
            } catch (e: Exception) {
                null
            }
        }
    }
}
