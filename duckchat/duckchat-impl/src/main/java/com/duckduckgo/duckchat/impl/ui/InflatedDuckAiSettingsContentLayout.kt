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

package com.duckduckgo.duckchat.impl.ui

import android.view.View
import com.duckduckgo.common.ui.view.listitem.DaxListItem
import com.duckduckgo.common.ui.view.text.DaxTextView
import com.duckduckgo.duckchat.impl.R

class InflatedDuckAiSettingsContentLayout(
    val root: View,
) {
    val duckChatSettingsText: DaxTextView = root.findViewById(R.id.duckChatSettingsSubtitle)
    val userEnabledDuckChatToggle: DaxListItem = root.findViewById(R.id.userEnabledDuckChatToggle)
    val duckAiInputScreenEnabledToggle: DaxListItem = root.findViewById(R.id.duckAiInputScreenEnabledToggle)
    val duckChatToggleSettingsTitle: View = root.findViewById(R.id.duckChatToggleSettingsTitle)
    val showDuckChatInMenuToggle: DaxListItem = root.findViewById(R.id.showDuckChatInMenuToggle)
    val showDuckChatInAddressBarToggle: DaxListItem = root.findViewById(R.id.showDuckChatInAddressBarToggle)
    val showDuckChatSearchSettingsLink: DaxListItem = root.findViewById(R.id.showDuckChatSearchSettingsLink)
}
