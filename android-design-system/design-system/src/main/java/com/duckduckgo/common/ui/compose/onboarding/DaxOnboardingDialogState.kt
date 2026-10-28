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

package com.duckduckgo.common.ui.compose.onboarding

import androidx.annotation.MainThread
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/** Hoisted controls for the currently attached onboarding dialog introduction. */
@Stable
class DaxOnboardingDialogState {
    private var skipCurrent: (() -> Unit)? = null

    /** Whether the attached dialog is running its introduction. */
    var isAnimating: Boolean by mutableStateOf(false)
        private set

    /** Finishes the attached introduction. Calls before attachment or after completion do nothing. */
    @MainThread
    fun skip() {
        if (isAnimating) skipCurrent?.invoke()
    }

    internal fun attach(skip: () -> Unit, animating: Boolean) {
        skipCurrent = skip
        isAnimating = animating
    }

    internal fun updateAnimating(skip: () -> Unit, animating: Boolean) {
        if (skipCurrent === skip) isAnimating = animating
    }

    internal fun detach(skip: () -> Unit) {
        if (skipCurrent === skip) {
            skipCurrent = null
            isAnimating = false
        }
    }
}

/** Remembers controls for the current dialog introduction across recompositions. */
@Composable
fun rememberDaxOnboardingDialogState(): DaxOnboardingDialogState = remember { DaxOnboardingDialogState() }
