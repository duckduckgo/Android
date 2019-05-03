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

import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import com.duckduckgo.app.privacy.model.PrivacyPractices
import com.duckduckgo.app.privacy.store.PrevalenceStore
import com.duckduckgo.app.trackerdetection.model.TrackerNetwork
import com.duckduckgo.app.trackerdetection.model.TrackerNetworks
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class SiteFactory @Inject constructor(
    private val privacyPractices: PrivacyPractices,
    private val trackerNetworks: TrackerNetworks,
    private val prevalenceStore: PrevalenceStore
) {

    /**
     * Builds a Site with minimal details; this is quick to build but won't contain the full details needed for all functionality
     *
     * @see [loadFullSiteDetails] to ensure full privacy details are loaded
     */
    @AnyThread
    fun buildSite(url: String, title: String? = null): Site {
        return SiteMonitor(url, title, prevalenceStore)
    }

    /**
     * Updates the given Site with the full details
     *
     * This can be expensive to execute.
     */
    @WorkerThread
    fun loadFullSiteDetails(site: Site) {
        val practices = privacyPractices.privacyPracticesFor(site.url)
        val memberNetwork = trackerNetworks.network(site.url)
        val prevalence = determinePrevalence(memberNetwork)
        val siteDetails = SitePrivacyData(site.url, practices, memberNetwork, prevalence)
        site.updatePrivacyData(siteDetails)
    }

    private fun determinePrevalence(memberNetwork: TrackerNetwork?): Double? {
        if (memberNetwork == null) {
            return null
        }
        return prevalenceStore.findPrevalenceOf(memberNetwork.name)
    }

    data class SitePrivacyData(
        val url: String, val practices: PrivacyPractices.Practices,
        val memberNetwork: TrackerNetwork?,
        val prevalence: Double?
    )
}