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

class FakeDnsProvider : DnsProvider {
    val mutableSystemDns = mutableListOf<InetAddress>()
    val mutablePrivateDns = mutableListOf<InetAddress>()
    var searchDomain: String? = null
    override fun getSystemDns(): List<InetAddress> = mutableSystemDns

    override fun getSearchDomains(): String? = searchDomain

    override fun getPrivateDns(): List<InetAddress> = mutablePrivateDns
}
