/*
 * Copyright (c) 2022 DuckDuckGo
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

import androidx.annotation.WorkerThread

interface AppTrackerDetector {
    /**
     * Evaluates whether the specified domain requested by the specified uid is a tracker.
     * This method should be called off the UI thread.
     *
     * @param domain the domain to evaluate
     * @param uid the uid of the app requesting the domain
     *
     * @return [AppTracker] if the request is a tracker, null otherwise
     */
    @WorkerThread
    fun evaluate(domain: String, uid: Int): AppTracker?

    data class AppTracker(
        val domain: String,
        val uid: Int,
        val trackerCompanyDisplayName: String,
        val trackingAppId: String,
        val trackingAppName: String,
    )
}
