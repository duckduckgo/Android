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

package com.duckduckgo.duckchat.api

/** The user-visible surface that initiated an entry into Duck.ai. */
enum class DuckChatEntryPoint {
    ADDRESS_BAR_PROMPT,
    ADDRESS_BAR_ICON,
    ADDRESS_BAR_SHORTCUT_CHIP,
    ADDRESS_BAR_EDITING_STATE,
    SUGGESTION_ASK_AI,
    BROWSING_MENU_NTP,
    BROWSING_MENU_WEBPAGE,
    TAB_SWITCHER,
    CHAT_HISTORY_NEW_CHAT,
    CHAT_HISTORY_OPEN_CHAT,
    VOICE,
    ONBOARDING,
    DIRECT_URL,
    SERP,
    ICON_SHORTCUT,
    CONTEXTUAL_CHAT,
    WIDGET_QUICK_ACTIONS,
    WIDGET_FAVORITE,
    SYSTEM_SEARCH,
    DIGITAL_ASSISTANT,
    DEEP_LINK_OTHER,
    PAID_SETTINGS,
}
