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

import com.duckduckgo.app.cta.db.DismissedCtaDao
import com.duckduckgo.app.cta.model.CtaId
import com.duckduckgo.app.cta.model.DismissedCta
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * The in-browser Duck.ai onboarding demo (the fire-button CTA sequence). Owns turning the demo on so
 * both the legacy onboarding-done path and the linear-onboarding `duck_ai_demo` step arm it identically.
 */
interface DuckAiOnboardingDemo {
    /**
     * Arm the demo: mark the Duck.ai onboarding flow active and silence the standard DAX onboarding
     * CTAs so only the Duck.ai demo CTAs show.
     */
    suspend fun arm()

    /** Whether the Duck.ai onboarding demo is active. Gates the Duck.ai demo CTAs. */
    fun isActive(): Boolean
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealDuckAiOnboardingDemo @Inject constructor(
    private val onboardingStore: OnboardingStore,
    private val dismissedCtaDao: DismissedCtaDao,
    private val dispatchers: DispatcherProvider,
) : DuckAiOnboardingDemo {

    override suspend fun arm() {
        withContext(dispatchers.io()) {
            onboardingStore.setDuckAiOnboardingFlow()
            listOf(
                CtaId.DAX_INTRO,
                CtaId.DAX_DIALOG_SERP,
                CtaId.DAX_DIALOG_TRACKERS_FOUND,
                CtaId.DAX_FIRE_BUTTON,
                CtaId.DAX_END,
            ).forEach { dismissedCtaDao.insert(DismissedCta(it)) }
        }
    }

    override fun isActive(): Boolean = onboardingStore.isDuckAiOnboardingFlow()
}
