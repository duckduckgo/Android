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

package com.duckduckgo.app.browser.api

/**
 * Manages WebView profiles for data isolation between fire button uses.
 *
 * When profile switching is available (feature flag enabled + WebView MULTI_PROFILE supported),
 * a new profile is created on each fire button usage. This allows clearing browser data
 * without restarting the process - instead, new WebViews are created with the new profile.
 */
interface WebViewProfileManager {
    /**
     * Checks if WebView profile switching is available.
     * This requires both the feature flag to be enabled AND WebView MULTI_PROFILE support.
     * @return true if profile switching can be used
     */
    suspend fun isProfileSwitchingAvailable(): Boolean

    /**
     * Synchronously checks if WebView profile switching is available.
     * Returns the cached availability state computed during [initialize].
     * This is safe to call from non-suspend contexts.
     * @return true if profile switching is available
     */
    fun isProfileSwitchingAvailableSync(): Boolean

    /**
     * Gets the current active profile name.
     * This is cached in memory for synchronous access during WebView configuration.
     * @return the current profile name, or empty string if using default profile
     */
    fun getCurrentProfileName(): String

    /**
     * Creates a new profile and switches to it.
     * This includes:
     * 1. Incrementing the profile index
     * 2. Creating the new profile via ProfileStore
     * 3. Migrating fireproofed cookies from old profile to new profile
     * 4. Updating the in-memory profile name cache
     *
     * @return true if profile switch was successful, false if fallback to process restart is needed
     */
    suspend fun switchToNewProfile(): Boolean

    /**
     * Cleans up stale profiles from previous app sessions.
     * Should be called on app startup. Profiles used in the current session cannot be deleted.
     */
    suspend fun cleanupStaleProfiles()

    /**
     * Initializes the profile manager on app startup.
     * Loads the current profile index into memory cache.
     */
    suspend fun initialize()
}
