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

package com.duckduckgo.testseeder.api

/**
 * Registry of every supported test-seeder key. Plugins MUST only claim keys
 * declared here. Adding a new entry requires review — this is the contract
 * E2E tests rely on.
 */
enum class TestSeederKey(val key: String, val description: String) {
    IS_MAESTRO("isMaestro", "Required gate. Must be \"true\" for any seeding to run."),
    OMNIBAR_POSITION("omnibarPosition", "Sets omnibar position. Values: top | bottom | split"),
    NATIVE_INPUT_TOGGLE("nativeInputToggle", "Enables unified native input. Values: true | false"),
    INPUT_WITH_AI_TOGGLE("inputWithAiToggle", "Enables AI in input screen. Values: true | false"),
    ADD_FAVORITES(
        "addFavorites",
        "Semicolon-separated list of URLs to seed as favorites. " +
            "URLs without a scheme are normalised to https://. Example: \"reddit.com;eff.org;cnn.com\"",
    ),
    ADD_BOOKMARKS(
        "addBookmarks",
        "Semicolon-separated list of URLs to seed as bookmarks. " +
            "URLs without a scheme are normalised to https://. Example: \"reddit.com;eff.org;cnn.com\"",
    ),
    REFERRER(
        key = "referrer",
        description = """
            |Simulates a Play Store install that came from a link with a referrer param.
            |Pass the decoded referrer value, e.g. 'origin=funnel_appnosupport_website&onboarding=ai'.
            |This is the referrer part of a Play Store link such as:
            |https://play.google.com/store/apps/details?id=com.duckduckgo.mobile.android&referrer=origin%3Dfunnel_appnosupport_website%26onboarding%3Dai
        """.trimMargin(),
    ),
}
