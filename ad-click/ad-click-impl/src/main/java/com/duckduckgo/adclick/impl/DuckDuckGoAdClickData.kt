/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.adclick.impl

import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import timber.log.Timber

interface AdClickData {

    fun setAdDomainTldPlusOne(adDomainTldPlusOne: String)
    fun removeAdDomain()
    fun removeAdDomain(tabId: String)
    fun addExemption(exemption: Exemption)
    fun addExemption(tabId: String, exemption: Exemption)
    fun getExemption(): Exemption?
    fun getExemption(tabId: String): Exemption?
    fun isHostExempted(host: String): Boolean
    fun removeExemption()
    fun setActiveTab(tabId: String)
    fun getAdDomainTldPlusOne(): String?
    fun getAdDomainTldPlusOne(tabId: String): String?
    fun remove(tabId: String)
    fun removeAll()
    fun removeAllExpired()
    fun setCurrentPage(currentPageUrl: String)
    fun getCurrentPage(): String
}

@ContributesBinding(AppScope::class)
class DuckDuckGoAdClickData @Inject constructor() : AdClickData {

    private var currentPageUrl = ""
    private var activeTabId = ""
    private val tabAdDomains = mutableMapOf<String, String>() // tabId -> adDomain or empty
    private val tabExemptions = mutableMapOf<String, Exemption>() // tabId -> exemption

    override fun setAdDomainTldPlusOne(adDomainTldPlusOne: String) {
        tabAdDomains[activeTabId] = adDomainTldPlusOne
        Timber.d("Detected ad. Tab ad domains: $tabAdDomains")
    }

    override fun removeAdDomain() {
        tabAdDomains.remove(activeTabId)
        Timber.d("Removed previously detected ad. Tab ad domains: $tabAdDomains")
    }

    override fun removeAdDomain(tabId: String) {
        tabAdDomains.remove(tabId)
        Timber.d("Removed previously detected ad for $tabId. Current active tabId is $activeTabId. Tab ad domains: $tabAdDomains")
    }

    override fun setActiveTab(tabId: String) {
        this.activeTabId = tabId
    }

    override fun getAdDomainTldPlusOne(): String? {
        Timber.d("Is ad? ${tabAdDomains[activeTabId] != null} Tab ad domains: $tabAdDomains")
        return tabAdDomains[activeTabId]
    }

    override fun getAdDomainTldPlusOne(tabId: String): String? {
        Timber.d("Is ad for tabId $tabId? ${tabAdDomains[tabId] != null}. Active tab is $activeTabId. Tab ad domains: $tabAdDomains")
        return tabAdDomains[tabId]
    }

    override fun removeExemption() {
        tabExemptions.remove(activeTabId)
        Timber.d("Removed exemption for active tab $activeTabId. Tab exemptions: $tabExemptions")
    }

    override fun addExemption(exemption: Exemption) {
        tabExemptions[activeTabId] = exemption
        Timber.d("Added exemption for active tab $activeTabId. Tab exemptions: $tabExemptions")
    }

    override fun addExemption(tabId: String, exemption: Exemption) {
        tabExemptions[tabId] = exemption
        Timber.d("Added exemption for tab $tabId. Tab exemptions: $tabExemptions")
    }

    override fun getExemption(): Exemption? {
        Timber.d("Get exemption for tab $activeTabId. Tab exemptions: $tabExemptions")
        return tabExemptions[activeTabId]
    }

    override fun getExemption(tabId: String): Exemption? {
        return tabExemptions[tabId]
    }

    override fun isHostExempted(host: String): Boolean {
        val tabExemption = tabExemptions[activeTabId] ?: return false
        return tabExemption.hostTldPlusOne == host
    }

    override fun remove(tabId: String) {
        tabAdDomains.remove(tabId)
        tabExemptions.remove(tabId)
        Timber.d("Removed data for tab $tabId. Tab ad domains: $tabAdDomains. Tab exemptions: $tabExemptions")
    }

    override fun removeAll() {
        tabAdDomains.clear()
        tabExemptions.clear()
        Timber.d("Removed all data. Ad clicked map is empty ${tabAdDomains.isEmpty()}. Empty tab exemptions? ${tabExemptions.isEmpty()}")
    }

    override fun removeAllExpired() {
        val currentTime = System.currentTimeMillis()
        val iterator = tabExemptions.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value.exemptionDeadline < currentTime) {
                iterator.remove()
            }
        }
        Timber.d("Removed all expired data. Tab exemptions: $tabExemptions")
    }

    override fun setCurrentPage(currentPageUrl: String) {
        this.currentPageUrl = currentPageUrl
    }

    override fun getCurrentPage(): String {
        return currentPageUrl
    }
}
