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

import com.duckduckgo.app.trackerdetection.Client.ClientName
import com.duckduckgo.app.trackerdetection.model.ResourceType
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackerDetector @Inject constructor() {

    private val clients = CopyOnWriteArrayList<Client>()

    fun addClient(client: Client) {
        clients.add(client)
    }

    fun hasClient(name: ClientName): Boolean {
        return clients.any { it.name == name }
    }

    fun shouldBlock(url: String, documentUrl: String, resourceType: ResourceType): Boolean {
        for (client: Client in clients) {
            if (client.matches(url, documentUrl, resourceType)) {
                return true
            }
        }
        return false
    }
}