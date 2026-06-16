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

import androidx.core.content.edit
import com.duckduckgo.app.onboardingbranddesignupdate.OnboardingBrandDesignUpdateToggles
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.referral.api.AppInstallationReferrerStateListener
import com.duckduckgo.referral.api.AppInstallationReferrerStateListener.Companion.MAX_REFERRER_WAIT_TIME_MS
import com.duckduckgo.referral.api.ReferrerParserPlugin
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.Lazy
import dagger.SingleInstanceIn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import logcat.LogPriority.INFO
import logcat.LogPriority.WARN
import logcat.logcat
import javax.inject.Inject

interface CustomAiOnboardingStore {
    /**
     * Awaits Play Install Referrer resolution (bounded by a built-in timeout).
     * If the app was installed via the custom AI onboarding referral link (`onboarding=ai`),
     * and other associated preconditions are met, returns whether custom AI onboarding plan should be used.
     *
     * Timeout / non-Play-build / referrer failure / preconditions not met -> `false`.
     */
    suspend fun isEnabled(): Boolean

    /**
     * Arms a one-shot signal that the next auto-launched input screen should open on the Duck.ai (chat) tab.
     * Set when the custom-AI onboarding finishes (completed or skipped); consumed once by
     * [consumeOpenInputOnDuckAiTab].
     */
    fun setOpenInputOnDuckAiTab()

    /**
     * Returns whether the next auto-launched input screen should open on the Duck.ai (chat) tab, clearing the
     * signal so subsequent launches behave normally. Returns `false` when not armed.
     */
    fun consumeOpenInputOnDuckAiTab(): Boolean
}

/**
 * Writer AND readiness-gated reader for the custom AI onboarding signal. [process] runs during
 * referrer parsing (first launch) and persists a flag when the Play Install Referrer carries
 * `onboarding=ai`; [isEnabled] reads it back and checks alongside preconditions.
 */
@SingleInstanceIn(scope = AppScope::class)
@ContributesBinding(scope = AppScope::class, boundType = CustomAiOnboardingStore::class)
@ContributesMultibinding(scope = AppScope::class, boundType = ReferrerParserPlugin::class)
class CustomAiOnboardingStoreImpl @Inject constructor(
    private val sharedPreferencesProvider: SharedPreferencesProvider,
    private val referrerStateListener: Lazy<AppInstallationReferrerStateListener>,
    private val dispatcherProvider: DispatcherProvider,
    private val customDuckAiOnboardingFeature: CustomDuckAiOnboardingFeature,
    private val orchestratorFeature: LinearOnboardingOrchestratorFeature,
    private val brandDesignUpdateToggles: OnboardingBrandDesignUpdateToggles,
) : CustomAiOnboardingStore, ReferrerParserPlugin {

    private val preferences by lazy { sharedPreferencesProvider.getSharedPreferences(PREFS_FILENAME) }

    override fun process(referrerParams: Map<String, String>) {
        runCatching {
            if (referrerParams[REFERRAL_KEY] == REFERRAL_VALUE_AI) {
                logcat(INFO) { "Custom AI onboarding referral detected" }
                preferences.edit { putBoolean(PREFS_KEY, true) }
            }
        }.onFailure { logcat(WARN) { "Failed to persist custom AI onboarding flag: ${it.message}" } }
    }

    override suspend fun isEnabled(): Boolean = withContext(dispatcherProvider.io()) {
        // Ensure the install referrer (and therefore the processing function) has resolved before reading.
        withTimeoutOrNull(MAX_REFERRER_WAIT_TIME_MS) { referrerStateListener.get().waitForReferrerCode() }
        val referrerExists = preferences.getBoolean(PREFS_KEY, false)
        val customAiOnboardingEnabled = customDuckAiOnboardingFeature.self().isEnabled()
        val orchestratorEnabled = orchestratorFeature.self().isEnabled()
        val brandDesignEnabled = brandDesignUpdateToggles.brandDesignUpdate().isEnabled()
        return@withContext referrerExists && customAiOnboardingEnabled && orchestratorEnabled && brandDesignEnabled
    }

    // Deliberately not persisted: a one-shot that should only influence the input screen launched
    // immediately after onboarding finishes within this process.
    @Volatile
    private var openInputOnDuckAiTab: Boolean = false

    override fun setOpenInputOnDuckAiTab() {
        openInputOnDuckAiTab = true
    }

    override fun consumeOpenInputOnDuckAiTab(): Boolean {
        return openInputOnDuckAiTab.also { openInputOnDuckAiTab = false }
    }

    companion object {
        private const val REFERRAL_KEY = "onboarding"

        // exact lowercase value as sent in the campaign referrer link (e.g. ...&onboarding=ai)
        private const val REFERRAL_VALUE_AI = "ai"

        private const val PREFS_FILENAME = "com.duckduckgo.app.onboarding.customai"
        private const val PREFS_KEY = "customAiOnboardingFlow"
    }
}
