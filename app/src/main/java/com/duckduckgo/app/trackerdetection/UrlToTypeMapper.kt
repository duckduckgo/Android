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

package com.duckduckgo.app.trackerdetection

import android.webkit.MimeTypeMap
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface UrlToTypeMapper {
    fun map(
        url: String,
        requestHeaders: Map<String, String>,
    ): String?
}

@ContributesBinding(AppScope::class)
class RealUrlToTypeMapper @Inject constructor() : UrlToTypeMapper {
    override fun map(
        url: String,
        requestHeaders: Map<String, String>,
    ): String? {
        val acceptHeader = requestHeaders[ACCEPT_HEADER]
        val xRequestedWithHeader = requestHeaders[X_REQUESTED_WITH_HEADER]

        if (!xRequestedWithHeader.isNullOrEmpty() && xRequestedWithHeader == XML_HTTP_REQUEST) {
            return XML_HTTP_REQUEST_TYPE
        } else if (!acceptHeader.isNullOrEmpty() && acceptHeader != WILDCARD) {
            val types = acceptHeader.split(",")
            val filteredTypes = types.filter { !it.startsWith(WILDCARD) }

            if (filteredTypes.all { checkImage(it) }) return IMAGE_TYPE
            if (filteredTypes.all { checkScript(it) }) return SCRIPT_TYPE
            if (filteredTypes.all { checkStyleSheet(it) }) return STYLESHEET_TYPE
        }

        val extension = MimeTypeMap.getFileExtensionFromUrl(url)
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)

        mimeType?.let {
            if (checkImage(it)) return IMAGE_TYPE
            if (checkScript(it)) return SCRIPT_TYPE
            if (checkStyleSheet(it)) return STYLESHEET_TYPE
        }
        return null
    }

    private fun checkImage(mimeType: String): Boolean {
        return mimeType.startsWith("image/")
    }

    private fun checkScript(mimeType: String): Boolean {
        return mimeType == "application/javascript"
    }

    private fun checkStyleSheet(mimeType: String): Boolean {
        return mimeType == "text/css"
    }

    companion object {
        const val IMAGE_TYPE = "image"
        const val SCRIPT_TYPE = "script"
        const val STYLESHEET_TYPE = "stylesheet"
        const val XML_HTTP_REQUEST_TYPE = "xmlhttprequest"

        const val ACCEPT_HEADER = "Accept"
        const val X_REQUESTED_WITH_HEADER = "X-Requested-With"
        const val XML_HTTP_REQUEST = "XMLHttpRequest"
        const val WILDCARD = "*/*"
    }
}
