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

package com.duckduckgo.app.trackerdetection;

import android.net.Uri
import com.duckduckgo.app.trackerdetection.model.DisconnectTracker

class DisconnectClient(override val name: Client.ClientName, private val trackers: List<DisconnectTracker>) : Client {

    override fun matches(url: String, documentUrl: String, resourceType: ResourceType): Boolean {

        val uri = Uri.parse(url)
        val trackerUris = trackers.map { Uri.parse("http://${it.url}") }

        if (trackerUris.filter { uri.host == it.host || uri.host.endsWith(".${it.host}") }.isNotEmpty()) {
            return true
        }

        return false
    }
}
