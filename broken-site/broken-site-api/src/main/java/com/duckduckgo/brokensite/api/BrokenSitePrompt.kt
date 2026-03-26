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

package com.duckduckgo.brokensite.api

import android.net.Uri

/**
 * The Broken Site Prompt interface defines methods for detecting repeated user-initiated refreshes
 * and determining when and how often to show a prompt to report site breakage.
 */
interface BrokenSitePrompt {

    /**
     * Called when the user dismisses the broken site prompt.
     */
    suspend fun userDismissedPrompt()

    /**
     * Called when the user accepts the broken site prompt.
     */
    suspend fun userAcceptedPrompt()

    /**
     * Checks if the broken site prompt feature is enabled.
     *
     * @return `true` if the feature is enabled, `false` otherwise.
     */
    suspend fun isFeatureEnabled(): Boolean

    /**
     * Records a page refresh for the given URL.
     *
     * @param url The URI of the page that was refreshed.
     */
    fun pageRefreshed(url: Uri)

    /**
     * Retrieves the set of user refresh patterns.
     *
     * @return A set of detected refresh patterns.
     */
    fun getUserRefreshPatterns(): Set<RefreshPattern>

    /**
     * Determines whether the broken site prompt should be shown for the given URL
     * and detected refresh patterns.
     *
     * @param url The URL of the page.
     * @param refreshPatterns The set of detected refresh patterns.
     * @return `true` if the prompt should be shown, `false` otherwise.
     */
    suspend fun shouldShowBrokenSitePrompt(url: String, refreshPatterns: Set<RefreshPattern>): Boolean

    /**
     * Called when the Call-to-Action (CTA), the broken site prompt, is shown.
     */
    suspend fun ctaShown()
}

enum class RefreshPattern {
    TWICE_IN_12_SECONDS,
    THRICE_IN_20_SECONDS,
}
