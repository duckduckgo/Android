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

import android.net.Uri
import javax.inject.Inject


class SpecialUrlDetector @Inject constructor() {

    sealed class UrlType {
        class Web(val webAddress: String) : UrlType()
        class Telephone(val telephoneNumber: String) : UrlType()
        class Email(val emailAddress: String) : UrlType()
        class Sms(val telephoneNumber: String) : UrlType()
    }

    fun determineType(uri: Uri): UrlType {
        val uriString = uri.toString()

        return when (uri.scheme) {
            TEL_SCHEME -> buildTelephone(uriString)
            TELPROMPT_SCHEME -> buildTelephonePrompt(uriString)
            MAILTO_SCHEME -> buildEmail(uriString)
            SMS_SCHEME -> buildSms(uriString)
            SMSTO_SCHEME -> buildSmsTo(uriString)
            else -> UrlType.Web(uriString)
        }
    }

    private fun buildTelephone(uriString: String) =
            UrlType.Telephone(uriString.removePrefix("$TEL_SCHEME:"))

    private fun buildTelephonePrompt(uriString: String): UrlType =
            UrlType.Telephone(uriString.removePrefix("$TELPROMPT_SCHEME:"))

    private fun buildEmail(uriString: String): UrlType {
        return UrlType.Email(uriString)
    }

    private fun buildSms(uriString: String) =
            UrlType.Sms(uriString.removePrefix("$SMS_SCHEME:"))

    private fun buildSmsTo(uriString: String) =
            UrlType.Sms(uriString.removePrefix("$SMSTO_SCHEME:"))

    fun determineType(uriString: String?): UrlType {
        if (uriString == null) return UrlType.Web("")

        return determineType(Uri.parse(uriString))
    }

    companion object {
        private const val TEL_SCHEME = "tel"
        private const val TELPROMPT_SCHEME = "telprompt"
        private const val MAILTO_SCHEME = "mailto"
        private const val SMS_SCHEME = "sms"
        private const val SMSTO_SCHEME = "smsto"
    }
}