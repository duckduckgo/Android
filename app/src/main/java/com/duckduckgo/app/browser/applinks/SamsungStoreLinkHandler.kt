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

package com.duckduckgo.app.browser.applinks

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import com.duckduckgo.app.browser.SpecialUrlDetector.UrlType
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import timber.log.Timber

interface SamsungStoreLinkHandler {
    fun handleSamsungStoreLink(uriString: String): UrlType?
}

@ContributesBinding(AppScope::class)
class DuckDuckGoSamsungStoreLinkHandler @Inject constructor() : SamsungStoreLinkHandler {

    override fun handleSamsungStoreLink(uriString: String): UrlType? {
        extractSamsungStoreAppPackageName(uriString)?.let { packageName ->
            try {
                val intent = Intent(Intent.ACTION_VIEW)
                intent.data = Uri.parse("${SAMSUNG_STORE_APP_SCHEME}$packageName")
                return UrlType.AppLink(appIntent = intent, uriString = uriString, storePackage = SAMSUNG_STORE_PACKAGE_NAME)
            } catch (e: ActivityNotFoundException) {
                Timber.w(e, "Samsung store not found")
            }
        }
        return null
    }

    private fun extractSamsungStoreAppPackageName(uriString: String): String? {
        return if (uriString.startsWith(SAMSUNG_STORE_URL_PREFIX)) {
            try {
                val uri = Uri.parse(uriString)
                val pathSegments = uri.pathSegments
                if (pathSegments.size > 1 && pathSegments[pathSegments.size - 2] == DETAIL_SEGMENT) {
                    pathSegments.lastOrNull()?.takeIf { it.isNotEmpty() }
                } else {
                    null
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to parse Samsung store URL")
                null
            }
        } else {
            null
        }
    }

    companion object {
        const val SAMSUNG_STORE_URL_PREFIX = "https://galaxystore.samsung.com/detail/"
        const val SAMSUNG_STORE_APP_SCHEME = "samsungapps://ProductDetail/"
        const val SAMSUNG_STORE_PACKAGE_NAME = "com.sec.android.app.samsungapps"
        const val DETAIL_SEGMENT = "detail"
    }
}
