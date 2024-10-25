/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.mobile.android.app.tracking

interface AppTrackingProtection {

    /**
     * This method returns whether the user has gone through AppTP onboarding
     * @return Returns `true` if user has gone through AppTP onboarding, `false` otherwise
     */
    suspend fun isOnboarded(): Boolean

    /**
     * This is a suspend function because the operation can take time.
     * You DO NOT need to set any dispatcher to call this suspend function
     * @return `true` when NetP is enabled
     */
    suspend fun isEnabled(): Boolean

    /**
     * This is a suspend function because the operation can take time
     * You DO NOT need to set any dispatcher to call this suspend function
     * @return `true` when NetP is enabled AND the VPN is running, `false` otherwise
     */
    suspend fun isRunning(): Boolean

    /**
     * This method will restart the App Tracking Protection feature by disabling it and re-enabling back again
     */
    fun restart()

    /**
     * This method will stop the App Tracking Protection feature
     */
    fun stop()

    /**
     * This is a suspend function because the operation can take time.
     * You DO NOT need to set any dispatcher to call this suspend function
     *
     * @return a list of app packages that is excluded from App Tracking Protection
     */
    suspend fun getExcludedApps(): List<String>
}
