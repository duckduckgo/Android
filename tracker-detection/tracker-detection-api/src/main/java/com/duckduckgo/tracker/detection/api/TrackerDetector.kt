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

package com.duckduckgo.tracker.detection.api

import android.net.Uri
import com.duckduckgo.app.trackerdetection.model.TrackingEvent

interface TrackerDetector {
    /**
     * Evaluates whether the given [url] is a tracker in the context of [documentUrl].
     *
     * @param url the resource URL to evaluate.
     * @param documentUrl the URL of the document that initiated the request.
     * @param checkFirstParty if true, first-party requests are excluded from blocking.
     * @param requestHeaders the HTTP headers associated with the request.
     * @return a [TrackingEvent] if the URL is identified as a tracker, or null otherwise.
     */
    fun evaluate(
        url: Uri,
        documentUrl: Uri,
        checkFirstParty: Boolean = true,
        requestHeaders: Map<String, String>,
    ): TrackingEvent?

    /**
     * Evaluates whether the given [url] is a tracker in the context of [documentUrl].
     *
     * @param url the resource URL string to evaluate.
     * @param documentUrl the URL of the document that initiated the request.
     * @param checkFirstParty if true, first-party requests are excluded from blocking.
     * @param requestHeaders the HTTP headers associated with the request.
     * @return a [TrackingEvent] if the URL is identified as a tracker, or null otherwise.
     */
    fun evaluate(
        url: String,
        documentUrl: Uri,
        checkFirstParty: Boolean = true,
        requestHeaders: Map<String, String>,
    ): TrackingEvent?
}
