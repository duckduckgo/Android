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

package com.duckduckgo.app.browser

import android.content.Intent
import android.net.Uri

interface SpecialUrlDetector {
    fun determineType(initiatingUrl: String?, uri: Uri): UrlType
    fun determineType(uriString: String?): UrlType
    fun processUrl(initiatingUrl: String?, uriString: String): UrlType

    sealed class UrlType {
        class Web(val webAddress: String) : UrlType()
        class Telephone(val telephoneNumber: String) : UrlType()
        class Email(val emailAddress: String) : UrlType()
        class Sms(val telephoneNumber: String) : UrlType()
        class AppLink(
            val appIntent: Intent? = null,
            val uriString: String,
        ) : UrlType()

        class NonHttpAppLink(
            val uriString: String,
            val intent: Intent,
            val fallbackUrl: String?,
            val fallbackIntent: Intent? = null,
        ) : UrlType()

        class SearchQuery(val query: String) : UrlType()
        class Unknown(val uriString: String) : UrlType()
        class ExtractedAmpLink(val extractedUrl: String) : UrlType()
        class CloakedAmpLink(val ampUrl: String) : UrlType()
        class TrackingParameterLink(val cleanedUrl: String) : UrlType()
        data object ShouldLaunchPrivacyProLink : UrlType()
        data class ShouldLaunchDuckPlayerLink(val url: Uri) : UrlType()
        class DuckScheme(val uriString: String) : UrlType()
        data object ShouldLaunchDuckChatLink : UrlType()
    }
}
