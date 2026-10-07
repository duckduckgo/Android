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

package com.duckduckgo.sync.impl.auth

import androidx.annotation.StringRes

/**
 * A prompt the UI has to show. It is answered at most once; later answers are ignored.
 */
sealed interface AuthPrompt {
    /**
     * Callback to dispatch when the prompt is on screen.
     */
    fun onShown()

    /**
     * Asks the user to confirm their identity with biometrics or the device credential.
     */
    interface Verify : AuthPrompt {
        @get:StringRes
        val title: Int

        @get:StringRes
        val message: Int

        /**
         * Callback to dispatch when the user authenticated successfully.
         */
        fun onVerified()

        /**
         * Callback to dispatch when the user dismissed the prompt without authenticating.
         */
        fun onCancelled()

        /**
         * Callback to dispatch when authentication failed.
         */
        fun onError(reason: String)
    }

    /**
     * Tells the user the device has no screen lock and offers to open the system settings to set one up.
     */
    interface Enroll : AuthPrompt {
        @get:StringRes
        val title: Int

        @get:StringRes
        val message: Int

        @get:StringRes
        val cta: Int

        /**
         * Callback to dispatch when the dialog is closed. Authentication does not wait for the user to set up a screen lock.
         */
        fun onClosed()
    }
}
