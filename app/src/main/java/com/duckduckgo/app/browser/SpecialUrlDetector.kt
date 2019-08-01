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

package com.duckduckgo.app.browser

import android.content.Intent
import android.net.Uri
import com.duckduckgo.app.browser.SpecialUrlDetector.UrlType
import timber.log.Timber
import java.net.URISyntaxException

interface SpecialUrlDetector {
    fun determineType(uri: Uri): UrlType
    fun determineType(uriString: String?): UrlType

    sealed class UrlType {
        class Web(val webAddress: String) : UrlType()
        class Telephone(val telephoneNumber: String) : UrlType()
        class Email(val emailAddress: String) : UrlType()
        class Sms(val telephoneNumber: String) : UrlType()
        class IntentType(val url: String, val intent: Intent, val fallbackUrl: String?) : UrlType()
        class SearchQuery(val query: String) : UrlType()
        class Unknown(val url: String) : UrlType()
    }

}

class SpecialUrlDetectorImpl : SpecialUrlDetector {

    override fun determineType(uri: Uri): UrlType {
        val uriString = uri.toString()

        return when (val scheme = uri.scheme) {
            TEL_SCHEME -> buildTelephone(uriString)
            TELPROMPT_SCHEME -> buildTelephonePrompt(uriString)
            MAILTO_SCHEME -> buildEmail(uriString)
            SMS_SCHEME -> buildSms(uriString)
            SMSTO_SCHEME -> buildSmsTo(uriString)
            HTTP_SCHEME, HTTPS_SCHEME, DATA_SCHEME -> UrlType.Web(uriString)
            ABOUT_SCHEME -> UrlType.Unknown(uriString)
            JAVASCRIPT_SCHEME -> UrlType.SearchQuery(uriString)
            null -> UrlType.SearchQuery(uriString)
            else -> checkForIntent(scheme, uriString)
        }
    }

    private fun buildTelephone(uriString: String) = UrlType.Telephone(uriString.removePrefix("$TEL_SCHEME:"))

    private fun buildTelephonePrompt(uriString: String): UrlType = UrlType.Telephone(uriString.removePrefix("$TELPROMPT_SCHEME:"))

    private fun buildEmail(uriString: String): UrlType = UrlType.Email(uriString)

    private fun buildSms(uriString: String) = UrlType.Sms(uriString.removePrefix("$SMS_SCHEME:"))

    private fun buildSmsTo(uriString: String) = UrlType.Sms(uriString.removePrefix("$SMSTO_SCHEME:"))

    private fun checkForIntent(scheme: String, uriString: String): UrlType {
        val validUriSchemeRegex = Regex("[a-z][a-zA-Z\\d+.-]+")
        if (scheme.matches(validUriSchemeRegex)){
            return buildIntent(uriString)
        }

        return UrlType.SearchQuery(uriString)
    }

    private fun buildIntent(uriString: String): UrlType {
        return try {
            val intent = Intent.parseUri(uriString, 0)
            val fallbackUrl = intent.getStringExtra(EXTRA_FALLBACK_URL)
            UrlType.IntentType(url = uriString, intent = intent, fallbackUrl = fallbackUrl)
        } catch (e: URISyntaxException) {
            Timber.w(e, "Failed to parse uri $uriString")
            return UrlType.Unknown(uriString)
        }
    }

    override fun determineType(uriString: String?): UrlType {
        if (uriString == null) return UrlType.Web("")

        return determineType(Uri.parse(uriString))
    }

    companion object {
        private const val TEL_SCHEME = "tel"
        private const val TELPROMPT_SCHEME = "telprompt"
        private const val MAILTO_SCHEME = "mailto"
        private const val SMS_SCHEME = "sms"
        private const val SMSTO_SCHEME = "smsto"
        private const val HTTP_SCHEME = "http"
        private const val HTTPS_SCHEME = "https"
        private const val ABOUT_SCHEME = "about"
        private const val DATA_SCHEME = "data"
        private const val JAVASCRIPT_SCHEME = "javascript"

        private const val EXTRA_FALLBACK_URL = "browser_fallback_url"
    }
}