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

package com.duckduckgo.browser.ui.omnibar

import android.view.View
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.appcompat.widget.Toolbar
import com.airbnb.lottie.LottieAnimationView
import com.duckduckgo.app.browser.omnibar.InputScreenLaunchListener
import com.duckduckgo.app.browser.omnibar.ItemPressedListener
import com.duckduckgo.app.browser.omnibar.LogoClickListener
import com.duckduckgo.app.browser.omnibar.OmnibarType
import com.duckduckgo.app.browser.omnibar.TextListener
import com.duckduckgo.app.browser.omnibar.model.Decoration
import com.duckduckgo.browser.ui.omnibar.model.StateChange
import com.duckduckgo.common.ui.view.KeyboardAwareEditText
import kotlinx.coroutines.flow.Flow

interface OmnibarView {
    val omnibarType: OmnibarType
    var isScrollingEnabled: Boolean
    var isUiLocked: Boolean
    val isEditing: Boolean
    val isEditingFlow: Flow<Boolean>
    val omnibarTextInput: KeyboardAwareEditText
    val omniBarContainer: View
    val toolbar: Toolbar
    val shieldIcon: LottieAnimationView
    val daxIcon: ImageView
    fun setOmnibarTextListener(textListener: TextListener)
    fun setOmnibarItemPressedListener(itemPressedListener: ItemPressedListener)
    fun setLogoClickListener(logoClickListener: LogoClickListener)
    fun setInputScreenLaunchListener(listener: InputScreenLaunchListener)
    fun decorate(decoration: Decoration)
    fun reduce(stateChange: StateChange)
    fun isPulseAnimationPlaying(): Boolean
    fun setDraftTextIfNtpOrSerp(query: String)
    fun setExpanded(expanded: Boolean)
    fun disableViewStateSaving()
    fun setExpanded(
        expanded: Boolean,
        animate: Boolean,
    )
    fun setMenuIcon(@DrawableRes resId: Int)
    fun show()
    fun gone()
}
