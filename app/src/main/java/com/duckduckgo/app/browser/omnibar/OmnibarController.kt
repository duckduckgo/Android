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

package com.duckduckgo.app.browser.omnibar

import android.view.View
import androidx.appcompat.widget.Toolbar
import com.airbnb.lottie.LottieAnimationView
import com.duckduckgo.app.browser.databinding.IncludeFindInPageBinding
import com.duckduckgo.app.browser.omnibar.Omnibar.Decoration
import com.duckduckgo.app.browser.omnibar.Omnibar.StateChange
import com.duckduckgo.common.ui.view.KeyboardAwareEditText
import kotlinx.coroutines.flow.Flow

interface OmnibarController {

    fun decorate(decoration: Decoration)

    fun reduce(stateChange: StateChange)

    fun setOmnibarTextListener(textListener: Omnibar.TextListener)

    fun setOmnibarItemPressedListener(itemPressedListener: Omnibar.ItemPressedListener)

    var isScrollingEnabled: Boolean

    val isEditingFlow: Flow<Boolean>

    val isEditing: Boolean

    fun isPulseAnimationPlaying(): Boolean

    fun setVisible(visible: Boolean)

    val findInPage: IncludeFindInPageBinding

    val omnibarTextInput: KeyboardAwareEditText

    val omniBarContainer: View

    val toolbar: Toolbar

    val shieldIcon: LottieAnimationView
}
