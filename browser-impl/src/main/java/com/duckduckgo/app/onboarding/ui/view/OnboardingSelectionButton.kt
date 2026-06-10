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

package com.duckduckgo.app.onboarding.ui.view

import android.content.Context
import android.content.res.Configuration
import android.util.AttributeSet
import android.view.ContextThemeWrapper
import com.duckduckgo.mobile.android.R
import com.google.android.material.button.MaterialButton

/**
 * Pill-shaped outlined selection button for onboarding option lists.
 *
 * Wraps the context in the onboarding theme so the `selectionButtonStyle` attr
 * always resolves — even when rendered on the NTP (outside the onboarding theme).
 * The style handles all visual properties: pill corners, zero insets, stroke,
 * accent colors, font, and ripple.
 */
class OnboardingSelectionButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.selectionButtonStyle,
) : MaterialButton(
    resolveOnboardingContext(context),
    attrs,
    defStyleAttr,
) {

    companion object {
        private fun resolveOnboardingContext(context: Context): Context {
            val nightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            val themeRes = if (nightMode == Configuration.UI_MODE_NIGHT_YES) {
                R.style.Theme_DuckDuckGo_Dark_Onboarding
            } else {
                R.style.Theme_DuckDuckGo_Light_Onboarding
            }
            return ContextThemeWrapper(context, themeRes)
        }
    }
}
