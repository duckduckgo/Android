/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.browser.omnibar

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.appcompat.widget.Toolbar
import com.airbnb.lottie.LottieAnimationView
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.TabSwitcherButton
import com.duckduckgo.app.browser.databinding.IncludeCustomTabToolbarBinding
import com.duckduckgo.app.browser.databinding.IncludeFindInPageBinding
import com.duckduckgo.common.ui.view.KeyboardAwareEditText
import com.duckduckgo.common.ui.view.shape.DaxBubbleCardView.EdgePosition
import com.google.android.material.appbar.AppBarLayout

class LegacyOmnibarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : AppBarLayout(context, attrs, defStyle) {

    init {
        val attr = context.theme.obtainStyledAttributes(attrs, R.styleable.LegacyOmnibarView, defStyle, 0)
        val omnibarPosition = EdgePosition.from(attr.getInt(R.styleable.LegacyOmnibarView_omnibarPosition, 0))

        val layout = when (omnibarPosition) {
            EdgePosition.TOP -> R.layout.view_legacy_omnibar
            EdgePosition.LEFT -> R.layout.view_legacy_omnibar_bottom
        }
        inflate(context, layout, this)
    }

    val findInPage by lazy { IncludeFindInPageBinding.bind(findViewById(R.id.findInPage)) }
    val omnibarTextInput: KeyboardAwareEditText by lazy { findViewById(R.id.omnibarTextInput) }
    val tabsMenu: TabSwitcherButton by lazy { findViewById(R.id.tabsMenu) }
    val fireIconMenu: FrameLayout by lazy { findViewById(R.id.fireIconMenu) }
    val browserMenu: FrameLayout by lazy { findViewById(R.id.browserMenu) }
    val cookieDummyView: View by lazy { findViewById(R.id.cookieDummyView) }
    val cookieAnimation: LottieAnimationView by lazy { findViewById(R.id.cookieAnimation) }
    val sceneRoot: ViewGroup by lazy { findViewById(R.id.sceneRoot) }
    val omniBarContainer: View by lazy { findViewById(R.id.omniBarContainer) }
    val toolbar: Toolbar by lazy { findViewById(R.id.toolbar) }
    val toolbarContainer: View by lazy { findViewById(R.id.toolbarContainer) }
    val customTabToolbarContainer by lazy { IncludeCustomTabToolbarBinding.bind(findViewById(R.id.customTabToolbarContainer)) }
    val browserMenuImageView: ImageView by lazy { findViewById(R.id.browserMenuImageView) }
    val shieldIcon: LottieAnimationView by lazy { findViewById(R.id.shieldIcon) }
    val pageLoadingIndicator: ProgressBar by lazy { findViewById(R.id.pageLoadingIndicator) }
    val searchIcon: ImageView by lazy { findViewById(R.id.searchIcon) }
    val daxIcon: ImageView by lazy { findViewById(R.id.daxIcon) }
    val clearTextButton: ImageView by lazy { findViewById(R.id.clearTextButton) }
    val fireIconImageView: ImageView by lazy { findViewById(R.id.fireIconImageView) }
    val placeholder: View by lazy { findViewById(R.id.placeholder) }
    val voiceSearchButton: ImageView by lazy { findViewById(R.id.voiceSearchButton) }
    val spacer: View by lazy { findViewById(R.id.spacer) }
    val trackersAnimation: LottieAnimationView by lazy { findViewById(R.id.trackersAnimation) }
    val duckPlayerIcon: ImageView by lazy { findViewById(R.id.duckPlayerIcon) }
}
