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

package com.duckduckgo.common.ui

import android.content.Context
import android.util.TypedValue
import android.view.ContextThemeWrapper
import com.duckduckgo.mobile.android.R

/**
 * Wraps the receiver in the onboarding theme that belongs to the hierarchy it is themed with, so
 * onboarding follows whichever of the old and new themes the activity applied.
 */
fun Context.onboardingThemedContext(isDark: Boolean): Context {
    val value = TypedValue()
    val attr = if (isDark) R.attr.daxOnboardingThemeDark else R.attr.daxOnboardingThemeLight
    val themeRes = when {
        theme.resolveAttribute(attr, value, true) -> value.resourceId
        isDark -> R.style.Theme_DuckDuckGo_Dark_Onboarding
        else -> R.style.Theme_DuckDuckGo_Light_Onboarding
    }
    return ContextThemeWrapper(this, themeRes)
}
