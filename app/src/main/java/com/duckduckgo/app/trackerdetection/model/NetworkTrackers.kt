/*
 * Copyright (c) 2017 DuckDuckGo
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

package com.duckduckgo.app.trackerdetection.model

import com.duckduckgo.app.global.UriString.Companion.sameOrSubdomain
import java.io.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkTrackers @Inject constructor() : Serializable {

    private var trackers: List<NetworkTracker> = ArrayList()

    fun updateData(trackers: List<NetworkTracker>) {
        this.trackers = trackers
    }

    fun network(url: String): String? {
        return trackers
                .filter { sameOrSubdomain(url, it.url) || sameOrSubdomain(url, it.networkUrl) }
                .map { it.networkName }
                .firstOrNull()
    }
}
