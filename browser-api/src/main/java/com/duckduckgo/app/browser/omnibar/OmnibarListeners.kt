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

import android.view.MotionEvent

interface ItemPressedListener {
    fun onTabsButtonPressed()
    fun onTabsButtonLongPressed()
    fun onFireButtonPressed()
    fun onBrowserMenuPressed()
    fun onPrivacyShieldPressed()
    fun onCustomTabClosePressed()
    fun onCustomTabPrivacyDashboardPressed()
    fun onVoiceSearchPressed()
    fun onDuckChatButtonPressed()
    fun onBackButtonPressed()
    fun onDuckAISidebarButtonPressed()
}

interface FindInPageListener {
    fun onFocusChanged(
        hasFocus: Boolean,
        query: String,
    )
    fun onPreviousSearchItemPressed()
    fun onNextSearchItemPressed()
    fun onClosePressed()
    fun onFindInPageTextChanged(query: String)
}

interface TextListener {
    fun onFocusChanged(
        hasFocus: Boolean,
        query: String,
    )
    fun onBackKeyPressed()
    fun onEnterPressed()
    fun onTouchEvent(event: MotionEvent)
    fun onOmnibarTextChanged(state: OmnibarTextState)
    fun onShowSuggestions(state: OmnibarTextState)
    fun onTrackersCountFinished()
}

fun interface InputScreenLaunchListener {
    fun launchInputScreen(query: String)
}

interface LogoClickListener {
    fun onClick(url: String)
}

data class OmnibarTextState(
    val text: String,
    val hasFocus: Boolean,
)
