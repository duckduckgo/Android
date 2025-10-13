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

package com.duckduckgo.privacy.dashboard.impl.ui

/**
 * Based on https://duckduckgo.github.io/privacy-dashboard/types/Generated_Schema_Definitions.ScreenKind.html
 */
enum class ScreenKind(val value: String) {
    PRIMARY_SCREEN("primaryScreen"),
    BREAKAGE_FORM("breakageForm"),
    PROMPT_BREAKAGE_FORM("promptBreakageForm"),
    TOGGLE_REPORT("toggleReport"),
    CATEGORY_TYPE_SELECTION("categoryTypeSelection"),
    CATEGORY_SELECTION("categorySelection"),
    CHOICE_TOGGLE("choiceToggle"),
    CHOICE_BREAKAGE_FORM("choiceBreakageForm"),
    CONNECTION("connection"),
    TRACKERS("trackers"),
    NON_TRACKERS("nonTrackers"),
    CONSENT_MANAGED("consentManaged"),
    COOKIE_HIDDEN("cookieHidden"),
}
