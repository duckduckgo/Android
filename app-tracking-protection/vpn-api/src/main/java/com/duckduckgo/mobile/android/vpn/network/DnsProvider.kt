/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.network

import java.net.InetAddress

interface DnsProvider {
    /**
     * @return Returns the list of default system DNS configured in the active network
     */
    fun getSystemDns(): List<InetAddress>

    /**
     * @return comma separated domains to search when resolving host names on this link or null
     */
    fun getSearchDomains(): String? = null

    /**
     * @return Returns the list of private DNS set by the user via the Android Private DNS settings, or empty
     * if not set
     */
    fun getPrivateDns(): List<InetAddress>
}
