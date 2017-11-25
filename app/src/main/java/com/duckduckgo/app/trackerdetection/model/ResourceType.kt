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

package com.duckduckgo.app.trackerdetection.model

import android.net.Uri
import android.webkit.WebResourceRequest


enum class ResourceType(val filterOption: Int) {

    UNKNOWN(0),
    SCRIPT(1),
    IMAGE(2),
    CSS(4);

    companion object {

        /**
         * A coarse approach to guessing the resource type from a request
         * to assist the tracker matcher
         */
        fun from(webResourceRequest: WebResourceRequest): ResourceType {

            var headerResult: ResourceType? = null
            var urlResult: ResourceType? = null

            val acceptHeader = webResourceRequest.requestHeaders?.get("Accept")
            if (acceptHeader != null) {
                headerResult = from(acceptHeader)
            }

            val url = webResourceRequest.url
            if (url != null) {
                urlResult = from(url)
            }

            return headerResult ?: urlResult ?: UNKNOWN
        }

        private fun from(acceptHeader: String): ResourceType? {
            if (acceptHeader.contains("image/")) {
                return IMAGE
            }
            if (acceptHeader.contains("/css")) {
                return CSS
            }
            if (acceptHeader.contains("javascript")) {
                return SCRIPT
            }
            return null
        }

        private fun from(url: Uri): ResourceType? {
            if (url.hasExtension("png", "jpg", "jpeg", "webp", "svg", "gif", "bmp", "tiff")) {
                return IMAGE
            }
            if (url.hasExtension("css")) {
                return CSS
            }
            if (url.hasExtension("js")) {
                return SCRIPT
            }
            return null
        }

        private fun Uri.hasExtension(vararg extensions: String): Boolean {
            val baseUrl = "$scheme$schemeSpecificPart$authority$path"
            return extensions.any { baseUrl.endsWith(".$it", true) }
        }
    }
}
