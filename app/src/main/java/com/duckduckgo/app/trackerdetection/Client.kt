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

interface Client {

    enum class ClientType {
        BLOCKING,
        WHITELIST
    }

    enum class ClientName(val type: ClientType) {
        // current clients
        TDS(ClientType.BLOCKING),

        // legacy clients
        EASYLIST(ClientType.BLOCKING),
        EASYPRIVACY(ClientType.BLOCKING),
        TRACKERSWHITELIST(ClientType.WHITELIST)
    }

    data class Result(
        val matches: Boolean,
        val entityName: String? = null,
        val categories: List<String>? = null,
        val surrogate: String? = null
    )

    val name: ClientName

    fun matches(
        url: String,
        documentUrl: String
    ): Result
}
