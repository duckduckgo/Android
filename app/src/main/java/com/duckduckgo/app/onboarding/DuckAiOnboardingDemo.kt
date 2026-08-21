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
import com.duckduckgo.app.cta.db.DismissedCtaDao
import com.duckduckgo.app.cta.model.CtaId
import com.duckduckgo.app.cta.model.DismissedCta
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * The in-browser Duck.ai onboarding demo (the fire-button CTA sequence) owner.
 * Every path that requires the demo routes through this interface.
 */
interface DuckAiOnboardingDemo {
    /**
     * Arm the demo: mark the flow active and silence the standard DAX onboarding CTAs so only the
     * Duck.ai demo CTAs show.
     *
     * @param isCentralToFlow records whether this onboarding flow should also apply side effects to copy and
     * redirects that focus on Duck.ai. See [wasCentralToOnboarding].
     */
    suspend fun arm(isCentralToFlow: Boolean)

    /**
     * Use at the start of an onboarding plan to ensure there's no stale demo state from a previous, aborted run.
     */
    suspend fun disarm()

    /** Whether the Duck.ai onboarding demo is active. Gates the Duck.ai demo CTAs. */
    fun isActive(): Boolean

    /**
     * Whether the current onboarding flow is focused on Duck.ai.
     * When true, callers use Duck.ai-specific CTA copy and route subscription upsells to the Duck.ai feature page.
     */
    fun wasCentralToOnboarding(): Boolean

    /** End the flow and dismiss the Duck.ai fire-button CTA. Safe to call more than once. */
    suspend fun finish()
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DuckAiOnboardingDemoImpl @Inject constructor(
    private val sharedPreferencesProvider: SharedPreferencesProvider,
    private val onboardingStore: OnboardingStore,
    private val dismissedCtaDao: DismissedCtaDao,
    private val dispatchers: DispatcherProvider,
) : DuckAiOnboardingDemo {

    private val preferences by lazy { sharedPreferencesProvider.getSharedPreferences(FILENAME) }

    override suspend fun arm(isCentralToFlow: Boolean) {
        withContext(dispatchers.io()) {
            preferences.edit {
                putBoolean(KEY_ACTIVE, true)
                putBoolean(KEY_CENTRAL_TO_FLOW, isCentralToFlow)
            }
            PRE_DISMISSED_CTAS.forEach { dismissedCtaDao.insert(DismissedCta(it)) }
        }
    }

    override suspend fun disarm() {
        withContext(dispatchers.io()) {
            preferences.edit {
                putBoolean(KEY_ACTIVE, false)
                putBoolean(KEY_CENTRAL_TO_FLOW, false)
            }
            PRE_DISMISSED_CTAS.forEach { dismissedCtaDao.delete(it) }
        }
    }

    override fun isActive(): Boolean = preferences.getBoolean(KEY_ACTIVE, legacyActive())

    override fun wasCentralToOnboarding(): Boolean = preferences.getBoolean(KEY_CENTRAL_TO_FLOW, false)

    /**
     * The state of the flow before this class owned it, so an instance that armed the demo pre-upgrade
     * stays armed — deactivating a mid-flight demo would leave the user with no CTAs at all, since [arm]
     * has already pre-dismissed the standard DAX ones. Only remove the fallback once the update has reached critical adoption.
     */
    @Suppress("DEPRECATION")
    private fun legacyActive(): Boolean = onboardingStore.isDuckAiOnboardingFlow()

    override suspend fun finish() {
        withContext(dispatchers.io()) {
            dismissedCtaDao.insert(DismissedCta(CtaId.DAX_DUCK_AI_FIRE_BUTTON))
        }
    }

    companion object {
        private const val FILENAME = "com.duckduckgo.app.onboarding.duckaidemo"
        private const val KEY_ACTIVE = "active"
        private const val KEY_CENTRAL_TO_FLOW = "centralToFlow"

        /**
         * Standard DAX onboarding CTAs the Duck.ai flow pre-dismisses when armed, so only the Duck.ai
         * demo CTAs (and the trailing Privacy Pro bubble) drive the flow.
         */
        val PRE_DISMISSED_CTAS = listOf(
            CtaId.DAX_INTRO,
            CtaId.DAX_DIALOG_SERP,
            CtaId.DAX_DIALOG_TRACKERS_FOUND,
            CtaId.DAX_FIRE_BUTTON,
            CtaId.DAX_END,
        )
    }
}
