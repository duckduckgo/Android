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

package com.duckduckgo.app.onboarding

/**
 * A setting the user can turn on or off from the onboarding preference selector.
 */
enum class OnboardingPreference {
    SEARCH_HISTORY,
    SAFE_SEARCH,
    SEARCH_ASSIST,
    HIDE_AI_GENERATED_IMAGES,
    BLOCK_ADS,
    REJECT_OPTIONAL_COOKIES,
    ACCEPT_NON_OPT_OUT_COOKIES,
}
