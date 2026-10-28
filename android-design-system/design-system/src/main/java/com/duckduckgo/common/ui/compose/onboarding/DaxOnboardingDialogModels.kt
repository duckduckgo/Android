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

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable

/** Horizontal alignment options supported by the onboarding dialog variants. */
@Stable
enum class DaxOnboardingDialogTextAlign {
    Start,
    Center,
}

/** A dialog CTA and the action it performs.
 *
 * @param text Label shown on the CTA.
 * @param enabled Whether the CTA may be activated after the dialog settles.
 * @param onClick Action invoked when the CTA is activated.
 */
@Immutable
data class DaxOnboardingDialogCta(
    val text: String,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

/** The optional dismiss action shown at the top end of the dialog.
 *
 * @param contentDescription Accessible description for the dismiss action.
 * @param onDismiss Action invoked when the dismiss affordance is activated.
 */
@Immutable
data class DaxOnboardingDialogDismiss(
    val contentDescription: String,
    val onDismiss: () -> Unit,
)

/** The layout-direction-relative positions supported by the onboarding speech-bubble tail. */
@Stable
enum class DaxOnboardingDialogTail {
    None,
    BottomStart,
    BottomStartWide,
    BottomEnd,
}
