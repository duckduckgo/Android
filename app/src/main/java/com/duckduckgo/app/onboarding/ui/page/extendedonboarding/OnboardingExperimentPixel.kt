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

package com.duckduckgo.app.onboarding.ui.page.extendedonboarding

import com.duckduckgo.app.statistics.pixels.Pixel

class OnboardingExperimentPixel {

    enum class PixelName(override val pixelName: String) : Pixel.PixelName {
        NOTIFICATION_RUNTIME_PERMISSION_SHOWN("m_notification_runtime_permission_shown"),
        PREONBOARDING_INTRO_SHOWN("m_preonboarding_intro_shown"),
        PREONBOARDING_INTRO_SHOWN_UNIQUE("m_preonboarding_intro_shown_unique"),
        PREONBOARDING_COMPARISON_CHART_SHOWN("m_preonboarding_comparison_chart_shown"),
        PREONBOARDING_COMPARISON_CHART_SHOWN_UNIQUE("m_preonboarding_comparison_chart_shown_unique"),
        PREONBOARDING_CHOOSE_BROWSER_PRESSED("m_preonboarding_choose_browser_pressed"),
        PREONBOARDING_AFFIRMATION_SHOWN("m_preonboarding_affirmation_shown"),
        PREONBOARDING_AFFIRMATION_SHOWN_UNIQUE("m_preonboarding_affirmation_shown_unique"),
        ONBOARDING_SEARCH_SAY_DUCK("m_onboarding_search_say_duck"),
        ONBOARDING_SEARCH_MIGHTY_DUCK("m_onboarding_search_mighty_duck"),
        ONBOARDING_SEARCH_WEATHER("m_onboarding_search_weather"),
        ONBOARDING_SEARCH_SURPRISE_ME("m_onboarding_search_surprise_me"),
        ONBOARDING_SEARCH_CUSTOM("m_onboarding_search_custom"),
        ONBOARDING_VISIT_SITE_ESPN("m_onboarding_visit_site_espn"),
        ONBOARDING_VISIT_SITE_YAHOO("m_onboarding_visit_site_yahoo"),
        ONBOARDING_VISIT_SITE_EBAY("m_onboarding_visit_site_ebay"),
        ONBOARDING_VISIT_SITE_SURPRISE_ME("m_onboarding_visit_site_surprise_me"),
        ONBOARDING_VISIT_SITE_CUSTOM("m_onboarding_visit_site_custom"),
        // Added new PRIVACY_DASHBOARD_FIRST_TIME_OPENED("m_privacy_dashboard_first_time_used") pixel in PrivacyDashboardPixel.kt class
    }

    object PixelParameter {
        const val FROM_ONBOARDING = "from_onboarding"
        const val DEFAULT_BROWSER = "default_browser"
        // Added new val DAX_INITIAL_VISIT_SITE_CTA = "visit_site" parameter in Pixel.kt class
    }
}
