/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.duckchat.impl.inputscreen.ui

import android.os.Build.VERSION
import com.duckduckgo.common.ui.DuckDuckGoTheme
import com.duckduckgo.common.ui.store.AppTheme
import com.duckduckgo.common.ui.store.ThemingDataStore
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.R
import com.duckduckgo.duckchat.api.inputscreen.BrowserAndInputScreenTransitionProvider
import com.duckduckgo.duckchat.impl.DuckChatInternal
import com.duckduckgo.duckchat.impl.inputscreen.ui.InputScreenConfigResolverImpl.Companion.useTopBar
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

/**
 * Provides animation resources for activity transitions between browser and input screen.
 *
 * ### API Level Behavior
 * - **API < 33**: Uses simple fade animations as slide animations don't seem to be supported (on tested devices).
 * - **API ≥ 33**: Uses slide animations with fade effects.
 *
 * ### Backdrop Color Handling
 * Alpha transitions on activities fade the entire window, revealing the black system background.
 * To prevent this visual artifact (especially noticeable in light mode), browser animations use
 * a backdrop color that matches the activity background.
 * Only one animation component needs backdrop color to prevent system background from showing through,
 * so we're only applying it to browser animations.
 *
 * **Limitations:**
 * - Backdrop color API is only available from API 33+, so black still shows through on lower APIs,
 *   but it's less dramatic because there's no slide animation.
 * - Cannot use themeable attributes (`?attr`) as they cause crashes of the whole launcher (on tested devices).
 *   Instead, we use fixed color values and filter resources by current theme state.
 *
 * ### Omnibar Position Handling
 * If the omnibar is at the top of the screen, slide animations are used (on API ≥ 33) to reveal the Search/Duck.ai toggle.
 *
 * If the omnibar is at the bottom of the screen, fade animations are used as the input box positioned
 * is anchored to the same screen coordinates in both the browser screen and input screen.
 */
@ContributesBinding(scope = AppScope::class)
class BrowserAndInputScreenTransitionProviderImpl @Inject constructor(
    private val appTheme: AppTheme,
    private val themingDataStore: ThemingDataStore,
    private val duckChatInternal: DuckChatInternal,
) : BrowserAndInputScreenTransitionProvider {
    override fun getBrowserEnterAnimation(isTopOmnibar: Boolean): Int =
        if (VERSION.SDK_INT >= 33) {
            if (appTheme.isLightModeEnabled()) {
                if (useTopBar(isTopOmnibar, duckChatInternal)) {
                    R.anim.slide_in_from_bottom_fade_in_light
                } else {
                    R.anim.fade_in_light
                }
            } else {
                if (useTopBar(isTopOmnibar, duckChatInternal)) {
                    if (themingDataStore.theme == DuckDuckGoTheme.DARK) {
                        R.anim.slide_in_from_bottom_fade_in_dark
                    } else {
                        R.anim.slide_in_from_bottom_fade_in_black
                    }
                } else {
                    if (themingDataStore.theme == DuckDuckGoTheme.DARK) {
                        R.anim.fade_in_dark
                    } else {
                        R.anim.fade_in_black
                    }
                }
            }
        } else {
            R.anim.fade_in
        }

    override fun getBrowserExitAnimation(isTopOmnibar: Boolean): Int =
        if (VERSION.SDK_INT >= 33) {
            if (appTheme.isLightModeEnabled()) {
                if (useTopBar(isTopOmnibar, duckChatInternal)) {
                    R.anim.slide_out_to_bottom_fade_out_light
                } else {
                    R.anim.fade_out_light
                }
            } else {
                if (useTopBar(isTopOmnibar, duckChatInternal)) {
                    if (themingDataStore.theme == DuckDuckGoTheme.DARK) {
                        R.anim.slide_out_to_bottom_fade_out_dark
                    } else {
                        R.anim.slide_out_to_bottom_fade_out_black
                    }
                } else {
                    if (themingDataStore.theme == DuckDuckGoTheme.DARK) {
                        R.anim.fade_out_dark
                    } else {
                        R.anim.fade_out_black
                    }
                }
            }
        } else {
            R.anim.fade_out
        }

    override fun getInputScreenEnterAnimation(isTopOmnibar: Boolean): Int =
        if (VERSION.SDK_INT >= 33 && useTopBar(isTopOmnibar, duckChatInternal)) {
            R.anim.slide_in_from_top_fade_in
        } else {
            R.anim.fade_in
        }

    override fun getInputScreenExitAnimation(isTopOmnibar: Boolean): Int =
        if (VERSION.SDK_INT >= 33 && useTopBar(isTopOmnibar, duckChatInternal)) {
            R.anim.slide_out_to_top_fade_out
        } else {
            R.anim.fade_out
        }
}
