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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import logcat.LogPriority.INFO
import logcat.LogPriority.WARN
import logcat.logcat
import javax.inject.Inject

interface CustomAiOnboardingStore {
    /**
     * Returns whether the custom AI onboarding plan is engaged.
     *
     * The decision persisted by [CustomAiOnboardingResolver.resolve] at an onboarding plan build time.
     * Calling [isEnabled] does not re-evaluate.
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

interface CustomAiOnboardingResolver {
    /**
     * Computes the custom AI onboarding decision once and persists it as the single source of truth.
     *
     * Awaits Play Install Referrer resolution (bounded by a built-in timeout), then ANDs the referral
     * signal (`onboarding=ai`) with the feature preconditions. Timeout / non-Play-build / referrer
     * failure / preconditions not met -> `false`. The result is persisted so later [CustomAiOnboardingStore.isEnabled]
     * reads return this same value without re-evaluating.
     *
     * Should be called once at an onboarding plan build time to pick the onboarding run. Re-invoking recomputes and overwrites.
     */
    suspend fun resolve(): Boolean
}

@SingleInstanceIn(scope = AppScope::class)
@ContributesBinding(scope = AppScope::class, boundType = CustomAiOnboardingStore::class)
@ContributesBinding(scope = AppScope::class, boundType = CustomAiOnboardingResolver::class)
@ContributesMultibinding(scope = AppScope::class, boundType = ReferrerParserPlugin::class)
class CustomAiOnboardingStoreImpl @Inject constructor(
    private val sharedPreferencesProvider: SharedPreferencesProvider,
    private val referrerStateListener: Lazy<AppInstallationReferrerStateListener>,
    private val dispatcherProvider: DispatcherProvider,
    private val customAiOnboardingFeature: CustomAiOnboardingFeature,
    private val brandDesignUpdateToggles: OnboardingBrandDesignUpdateToggles,
) : CustomAiOnboardingStore, CustomAiOnboardingResolver, ReferrerParserPlugin {

    private val preferences by lazy { sharedPreferencesProvider.getSharedPreferences(PREFS_FILENAME) }

    private val resolveMutex = Mutex()

    override fun process(referrerParams: Map<String, String>) {
        runCatching {
            if (referrerParams[REFERRER_KEY] == REFERRER_VALUE_AI) {
                logcat(INFO) { "Custom AI onboarding referrer detected" }
                preferences.edit { putBoolean(PREFS_KEY_REFERRER_PARAM_PRESENT, true) }
            }
        }.onFailure { logcat(WARN) { "Failed to persist custom AI onboarding flag: ${it.message}" } }
    }

    override suspend fun resolve() = resolveMutex.withLock {
        withContext(dispatcherProvider.io()) {
            // Ensure the install referrer (and therefore the processing function) has resolved before reading.
            withTimeoutOrNull(MAX_REFERRER_WAIT_TIME_MS) { referrerStateListener.get().waitForReferrerCode() }
            val referrerExists = preferences.getBoolean(PREFS_KEY_REFERRER_PARAM_PRESENT, false)

            val customAiOnboardingEnabled = customAiOnboardingFeature.self().isEnabled()
            val brandDesignEnabled = brandDesignUpdateToggles.brandDesignUpdate().isEnabled()

            val resolution = referrerExists && customAiOnboardingEnabled && brandDesignEnabled
            preferences.edit { putBoolean(PREFS_KEY_ENABLED, resolution) }

            return@withContext resolution
        }
    }

    override suspend fun isEnabled(): Boolean = resolveMutex.withLock {
        withContext(dispatcherProvider.io()) {
            preferences.getBoolean(PREFS_KEY_ENABLED, false)
        }
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
        private const val REFERRER_KEY = "onboarding"

        // exact lowercase value as sent in the campaign referrer link (e.g. ...&onboarding=ai)
        private const val REFERRER_VALUE_AI = "ai"

        private const val PREFS_FILENAME = "com.duckduckgo.app.onboarding.customai"
        private const val PREFS_KEY_REFERRER_PARAM_PRESENT = "customAiOnboardingReferrerParamPresent"

        private const val PREFS_KEY_ENABLED = "customAiOnboardingEnabled"
    }
}
