/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.app.trackerdetection

import com.duckduckgo.app.global.UriString
import com.duckduckgo.app.trackerdetection.model.ResourceType
import com.duckduckgo.app.trackerdetection.model.TdsTracker
import com.duckduckgo.app.trackerdetection.model.TdsTracker.Action.BLOCK

class TdsClient(override val name: Client.ClientName, private val trackers: List<TdsTracker>) : Client {

    override fun matches(url: String, documentUrl: String, resourceType: ResourceType): Boolean {
        val domain = UriString.host(url) ?: return false
        var tracker = trackers.firstOrNull { it.domain == domain } ?: return false

        if (tracker.defaultAction == BLOCK) {
            return true
        }

        //TODO rule checks
        //TODO what about subdomains?
        return false
    }
}
