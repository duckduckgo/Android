/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.global.model

import com.duckduckgo.app.privacy.model.PrivacyPractices
import com.duckduckgo.app.privacy.store.PrevalenceStore
import com.duckduckgo.app.trackerdetection.model.TrackerNetworks
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class SiteFactory @Inject constructor(
    private val privacyPractices: PrivacyPractices,
    private val trackerNetworks: TrackerNetworks,
    private val prevalenceStore: PrevalenceStore
) {

    fun build(url: String, title: String? = null): Site {
        val practices = privacyPractices.privacyPracticesFor(url)
        val memberNetwork = trackerNetworks.network(url)
        val site = SiteMonitor(url, practices, memberNetwork, prevalenceStore)
        title?.let {
            site.title = it
        }
        return site
    }

}