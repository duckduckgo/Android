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

import com.duckduckgo.app.global.performance.measureExecution
import com.duckduckgo.app.privacy.model.PrivacyPractices
import com.duckduckgo.app.privacy.store.PrevalenceStore
import com.duckduckgo.app.trackerdetection.model.TrackerNetworks
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton


@Singleton
class SiteFactory @Inject constructor(
    private val privacyPractices: PrivacyPractices,
    @Named("newTrackerNetworks") private val trackerNetworks: TrackerNetworks,
    private val prevalenceStore: PrevalenceStore
) {

//    fun build(url: String, title: String? = null): Site {
//        return measureExecution("siteFactory.build") {
//            val practices = measureExecution("privacyPractices") { privacyPractices.privacyPracticesFor(url) }
//            val memberNetwork = measureExecution("trackerNetworks") { trackerNetworks.network(url) }
//            val site = measureExecution("Build SiteMonitor") { SiteMonitor(url, practices, memberNetwork, prevalenceStore) }
//            val site2 = measureExecution("Build SiteMonitor2") { UpdateableSiteMonitor(url) }
//            title?.let {
//                site.title = it
//            }
//            return@measureExecution site
//        }
//    }

    fun buildSiteMonitor(url: String): SiteMonitor {
        return measureExecution("Built site monitor for $url") {

            val practices = privacyPractices.privacyPracticesFor(url)
            val memberNetwork = trackerNetworks.network(url)
            return@measureExecution SiteMonitor(url, practices, memberNetwork, prevalenceStore)
        }
    }


    fun build(url: String): Site {
        val site = Site(url)

        //site.siteMonitor = SiteMonitor(url, UNKNOWN, null, prevalenceStore)

        return site
    }

}
