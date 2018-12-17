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

package com.duckduckgo.app.surrogates

import android.net.Uri

interface ResourceSurrogates {
    fun loadSurrogates(urls: List<SurrogateResponse>)
    fun get(uri: Uri): SurrogateResponse
    fun getAll(): List<SurrogateResponse>
}

class ResourceSurrogatesImpl : ResourceSurrogates {

    private val surrogates = mutableListOf<SurrogateResponse>()

    override fun loadSurrogates(urls: List<SurrogateResponse>) {
        surrogates.clear()
        surrogates.addAll(urls)
    }

    override fun get(uri: Uri): SurrogateResponse {
        val uriString = uri.toString()

        return surrogates.find { uriString.contains(it.name) }
            ?: return SurrogateResponse(responseAvailable = false)
    }

    override fun getAll(): List<SurrogateResponse> {
        return surrogates
    }
}

data class SurrogateResponse(
    val responseAvailable: Boolean = true,
    val name: String = "",
    val jsFunction: String = "",
    val mimeType: String = ""
)
