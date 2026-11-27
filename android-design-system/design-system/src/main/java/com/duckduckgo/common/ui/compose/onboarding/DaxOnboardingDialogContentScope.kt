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

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Stable

/**
 * Receiver scope for arbitrary dialog content.
 *
 * The dialog gates pointer, focus and semantics for the whole slot until the intro settles. The slot is keyed by
 * content key, so its remembered state and effects reset when the content key changes.
 */
@Stable
interface DaxOnboardingDialogContentScope : ColumnScope {
    /** True once the intro animation has finished and the content can be interacted with. */
    val isIntroSettled: Boolean

    /**
     * Whether the intro animation was skipped rather than played: tapped through, or never run because animations were off.
     * Only meaningful once [isIntroSettled] is true.
     */
    val wasIntroAnimationSkipped: Boolean
}

internal class DaxOnboardingDialogContentScopeImpl(
    columnScope: ColumnScope,
    private val intro: DaxOnboardingDialogIntro,
) : DaxOnboardingDialogContentScope, ColumnScope by columnScope {
    override val isIntroSettled: Boolean get() = intro.settled
    override val wasIntroAnimationSkipped: Boolean get() = intro.animationSkipped
}
