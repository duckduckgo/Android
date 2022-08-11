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

package com.duckduckgo.adclick.api

/** Public interface for the Ad Click feature which helps to measure ad conversions only when they are required */
interface AdClickManager {

    /**
     * Sets the active tab. It takes as parameters:
     * mandatory [tabId] - The id of the current visible tab.
     * optional [url] - The url loaded in the current tab, null if no url was loaded (empty tab).
     * optional [sourceTabId] - The id of the tab from which the current tab was opened, null if a new tab option was used.
     * optional [sourceTabUrl] - The url loaded in the tab from which the current tab was opened, null if a new tab option was used.
     */
    fun setActiveTabId(tabId: String, url: String? = null, sourceTabId: String? = null, sourceTabUrl: String? = null)

    /**
     * Detects and registers the eTLD+1 if an ad link was clicked. It takes as parameters:
     * optional [url] - The requested url, null if no url was requested.
     * mandatory [isMainFrame] - True if the request is for mainframe, false otherwise.
     */
    fun detectAdClick(url: String?, isMainFrame: Boolean)

    /**
     * Detects and saves in memory the ad eTLD+1 domain from the url. It takes as parameters:
     * mandatory [url] - The requested url.
     */
    fun detectAdDomain(url: String)

    /**
     * Removes any data kept in memory for the specified tab. It takes as parameters:
     * mandatory [tabId] - The id of the active tab.
     */
    fun clearTabId(tabId: String)

    /**
     * Removes any data related to ad management that is kept in memory.
     */
    fun clearAll()

    /**
     * Removes any data related to ad management that is kept in memory. This is used asynchronously.
     */
    fun clearAllExpiredAsync()

    /**
     * Detects if there is an existing exemption based on the document url and the url requested. It takes as parameters:
     * mandatory [documentUrl] - The initially requested url, potentially leading to the advertiser page.
     * mandatory [url] - The requested url, potentially a tracker used for ad attribution.
     * @return `true` if there is an existing exemption for this combination of [documentUrl] and [url], false otherwise.
     */
    fun isExemption(documentUrl: String, url: String): Boolean
}
