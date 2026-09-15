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

import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject

/**
 * Which tab the input screen, auto-launched right after onboarding, should open on.
 */
interface OnboardingInputScreenLaunchTarget {
    /** Arms a one-shot signal that the next auto-launched input screen opens on the Duck.ai (chat) tab. */
    fun setOpenOnDuckAi()

    /**
     * Returns whether the next auto-launched input screen should open on the Duck.ai (chat) tab, clearing
     * the signal so subsequent launches behave normally. Returns `false` when not armed.
     */
    fun consumeOpenOnDuckAi(): Boolean
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class OnboardingInputScreenLaunchTargetImpl @Inject constructor() : OnboardingInputScreenLaunchTarget {

    // Deliberately not persisted: a one-shot that should only influence the input screen launched
    // immediately after onboarding finishes within this process.
    @Volatile
    private var openOnDuckAi: Boolean = false

    override fun setOpenOnDuckAi() {
        openOnDuckAi = true
    }

    override fun consumeOpenOnDuckAi(): Boolean = openOnDuckAi.also { openOnDuckAi = false }
}
