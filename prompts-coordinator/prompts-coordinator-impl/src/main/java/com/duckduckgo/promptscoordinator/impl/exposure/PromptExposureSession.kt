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

package com.duckduckgo.promptscoordinator.impl.exposure

import com.duckduckgo.di.scopes.AppScope
import dagger.SingleInstanceIn
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/** Whether a prompt was shown in the current session. In memory by design: a session cannot outlive the process. */
@SingleInstanceIn(AppScope::class)
class PromptExposureSession @Inject constructor() {

    private val promptShownThisSession = AtomicBoolean(false)

    fun markPromptShown() {
        promptShownThisSession.set(true)
    }

    /** Ends the session: returns whether it showed a prompt, and starts the next one without. */
    fun consumePromptShown(): Boolean = promptShownThisSession.getAndSet(false)
}
