/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.generalsettings.showonapplaunch

import android.net.Uri
import com.duckduckgo.app.generalsettings.showonapplaunch.store.ShowOnAppLaunchOptionDataStore

class ShowOnAppLaunchUrlConverterImpl : UrlConverter {

    override fun convertUrl(url: String?): String {
        if (url.isNullOrBlank()) return ShowOnAppLaunchOptionDataStore.DEFAULT_SPECIFIC_PAGE_URL

        val uri = Uri.parse(url.trim())

        val uriWithScheme = if (uri.scheme == null) {
            Uri.Builder()
                .scheme("http")
                .authority(uri.path?.lowercase())
        } else {
            uri.buildUpon()
                .scheme(uri.scheme?.lowercase())
                .authority(uri.authority?.lowercase())
        }
            .apply {
                query(uri.query)
                fragment(uri.fragment)
            }

        val uriWithPath = if (uri.path.isNullOrBlank()) {
            uriWithScheme.path("/")
        } else {
            uriWithScheme
        }

        val processedUrl = uriWithPath.build().toString()

        return Uri.decode(processedUrl)
    }
}
