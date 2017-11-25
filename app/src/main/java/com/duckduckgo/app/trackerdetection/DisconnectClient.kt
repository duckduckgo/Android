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

package com.duckduckgo.app.trackerdetection

import android.net.Uri
import com.duckduckgo.app.global.withScheme
import com.duckduckgo.app.trackerdetection.model.DisconnectTracker
import com.duckduckgo.app.trackerdetection.model.ResourceType

class DisconnectClient(override val name: Client.ClientName, private val trackers: List<DisconnectTracker>) : Client {

    override fun matches(url: String, documentUrl: String, resourceType: ResourceType): Boolean {

        val host = Uri.parse(url).host ?: return false

        return trackers
                .filter { bannedCategories().contains(it.category) }
                .map { Uri.parse(it.url).withScheme() }
                .filter { host == it.host || host.endsWith(".${it.host}") }
                .isNotEmpty()
    }

    private fun bannedCategories(): List<String> {
        return listOf("Analytics", "Advertising", "Social")
    }

}
