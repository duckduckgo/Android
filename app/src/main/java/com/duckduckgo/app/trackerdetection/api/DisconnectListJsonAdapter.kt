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

package com.duckduckgo.app.trackerdetection.api

import com.duckduckgo.app.trackerdetection.model.DisconnectTracker
import com.squareup.moshi.FromJson


class DisconnectListJsonAdapter {

    @FromJson
    fun fromJson(json: Map<String, List<Map<String, Map<String, Any>>>>): List<DisconnectTracker> {
        val trackers = ArrayList<DisconnectTracker>()
        for ((category, list) in json) {
            for (trackerGroup in list) {
                trackers.addAll(convertGroup(category, trackerGroup))
            }
        }
        return trackers
    }

    private fun convertGroup(category: String, json: Map<String, Map<String, Any>>): List<DisconnectTracker> {
        val networkName = json.keys.first()
        val networkGroup = json.values.first().filter { it.value as? List<*> != null }
        val networkUrl = networkGroup.keys.first()
        val trackerUrls = networkGroup.values.firstOrNull() as? List<*>
        if (trackerUrls != null) {
            return trackerUrls.map { DisconnectTracker(it as String, category, networkName, networkUrl) }
        }
        return ArrayList()
    }
}
