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

package com.duckduckgo.feedback.api

import android.view.View
import androidx.activity.result.contract.ActivityResultContract
import com.duckduckgo.navigation.api.GlobalActivityStarter

/**
 * Activity params for the Share Feedback screen (fire-and-forget launches via
 * [GlobalActivityStarter]).
 */
data object FeedbackScreenNoParams : GlobalActivityStarter.ActivityParams

/**
 * Hooks for callers that need to launch the Share Feedback screen AND react to its result.
 * Fire-and-forget launches should keep using [FeedbackScreenNoParams] with
 * [GlobalActivityStarter].
 */
interface FeedbackLauncher {

    /**
     * ActivityResultContract that launches the Share Feedback screen. The result is:
     *  - `true` — the user submitted feedback. Note: submission is fire-and-forget, so
     *    `true` does not mean the network POST succeeded — only that the user tapped submit.
     *  - `false` — the user cancelled without submitting.
     *
     * Register from a `ComponentActivity`'s `onCreate` before it moves past `CREATED`.
     */
    fun feedbackContract(): ActivityResultContract<Void?, Boolean>

    /**
     * Shows the standard "feedback submitted" confirmation using [hostView] as the
     * context for the presentation. The specific UI primitive (Snackbar / Toast / etc.)
     * is a `:feedback-impl` concern — callers should not assume any particular primitive.
     */
    fun showFeedbackSubmittedMessage(hostView: View)
}
