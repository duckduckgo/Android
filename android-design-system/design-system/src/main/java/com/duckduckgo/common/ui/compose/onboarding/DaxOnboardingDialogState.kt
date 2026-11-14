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

/** Lets the caller observe and skip the attached dialog's intro animation. */
@Stable
sealed interface DaxOnboardingDialogState {
    /** Whether the attached dialog is running its intro animation. */
    val isAnimating: Boolean

    /** Finishes the attached intro animation. Calls before attachment or after completion do nothing. */
    @MainThread
    fun skip()
}

/** State for a View host that bounds the dialog's height itself. Compose callers don't need it. */
@Stable
sealed interface DaxOnboardingDialogHostState : DaxOnboardingDialogState {
    /** Available height of the complete dialog in pixels. */
    var availableHeightPx: Int?

    /** Unclipped content height in pixels, before the card is bounded by its host. */
    val contentHeightPx: Int
}

internal class DaxOnboardingDialogStateImpl : DaxOnboardingDialogHostState {
    override var availableHeightPx: Int? by mutableStateOf(null)

    override var contentHeightPx: Int by mutableStateOf(0)
        private set

    override var isAnimating: Boolean by mutableStateOf(false)
        private set

    private var skipCurrent: (() -> Unit)? = null

    override fun skip() {
        if (isAnimating) skipCurrent?.invoke()
    }

    fun updateContentHeight(skip: () -> Unit, height: Int) {
        if (skipCurrent === skip) contentHeightPx = height
    }

    fun attach(skip: () -> Unit, animating: Boolean) {
        skipCurrent = skip
        isAnimating = animating
    }

    fun updateAnimating(skip: () -> Unit, animating: Boolean) {
        if (skipCurrent === skip) isAnimating = animating
    }

    fun detach(skip: () -> Unit) {
        if (skipCurrent === skip) {
            skipCurrent = null
            isAnimating = false
        }
    }
}

// Both interfaces are sealed with this as their only implementation, so the cast can't fail.
internal val DaxOnboardingDialogState.impl: DaxOnboardingDialogStateImpl
    get() = this as DaxOnboardingDialogStateImpl

/** Remembers the state for a Compose caller of the dialog. */
@Composable
fun rememberDaxOnboardingDialogState(): DaxOnboardingDialogState = remember { DaxOnboardingDialogStateImpl() }

/** Creates the state for a View host that sets [DaxOnboardingDialogHostState.availableHeightPx] itself. */
fun DaxOnboardingDialogHostState(): DaxOnboardingDialogHostState = DaxOnboardingDialogStateImpl()
