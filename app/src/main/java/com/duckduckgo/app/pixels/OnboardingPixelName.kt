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

package com.duckduckgo.app.pixels

import com.duckduckgo.app.statistics.pixels.Pixel

enum class OnboardingPixelName(override val pixelName: String) : Pixel.PixelName {
    ONBOARDING_QUICK_SETUP("onboarding_quick-setup"),
    ONBOARDING_WELCOME("onboarding_welcome"),
    ONBOARDING_SET_DEFAULT("onboarding_set-default"),
    ONBOARDING_ADD_TO_DOCK("onboarding_add-to-dock"),
    ONBOARDING_WIDGET_PROMPT("onboarding_widget-prompt"),
    ONBOARDING_ADDRESS_BAR_POSITION("onboarding_address-bar-position"),
    ONBOARDING_SEARCH_EXPERIENCE("onboarding_search-experience"),
    ONBOARDING_NOTIFICATIONS("onboarding_notifications"),
    ONBOARDING_SEARCH_CHAT_TOGGLE("onboarding_search-chat-toggle"),
    ONBOARDING_AI_INTRO("onboarding_ai-intro"),
    ONBOARDING_FIRE_BUTTON("onboarding_fire-button"),
    ONBOARDING_SEARCH("onboarding_search"),
    ONBOARDING_SEARCH_RESULTS("onboarding_search-results"),
    ONBOARDING_VISIT_SITE("onboarding_visit-site"),
    ONBOARDING_TRACKERS_BLOCKED("onboarding_trackers-blocked"),
    ONBOARDING_END("onboarding_end"),
    ONBOARDING_SUBSCRIPTION_PROMO("onboarding_subscription-promo"),
}
