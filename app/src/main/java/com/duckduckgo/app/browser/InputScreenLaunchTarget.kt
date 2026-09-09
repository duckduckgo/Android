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

package com.duckduckgo.app.browser

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.InputMode
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject

/**
 * A one-shot signal for which mode the next auto-launched input screen should open in. Armed by
 * onboarding (to land on Duck.ai) and by the "New Search" menu action (to land on Search), and
 * cleared the moment the input screen reads it.
 */
interface InputScreenLaunchTarget {
    /** Arms a one-shot signal that the next auto-launched input screen opens in [mode]. */
    fun setInitialInputMode(mode: InputMode)

    /** The armed mode without clearing it, or `null` when not armed. */
    fun peekInitialInputMode(): InputMode?

    /**
     * Returns the armed mode and clears the signal so subsequent launches behave normally. Returns
     * `null` when not armed.
     */
    fun consumeInitialInputMode(): InputMode?
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class InputScreenLaunchTargetImpl @Inject constructor() : InputScreenLaunchTarget {

    // Deliberately not persisted: a one-shot that should only influence the very next input screen
    // launched within this process.
    @Volatile
    private var initialInputMode: InputMode? = null

    override fun setInitialInputMode(mode: InputMode) {
        initialInputMode = mode
    }

    override fun peekInitialInputMode(): InputMode? = initialInputMode

    override fun consumeInitialInputMode(): InputMode? = initialInputMode.also { initialInputMode = null }
}
