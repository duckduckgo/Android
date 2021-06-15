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

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import com.duckduckgo.app.browser.SpecialUrlDetector.UrlType
import com.duckduckgo.app.settings.db.SettingsDataStore
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
        class AppLink(val appIntent: Intent? = null, val excludedComponents: List<ComponentName>? = null, val uriString: String) : UrlType()
        class NonHttpAppLink(val uriString: String, val intent: Intent, val fallbackUrl: String?) : UrlType()
        class SearchQuery(val query: String) : UrlType()
        class Unknown(val uriString: String) : UrlType()
    }
}

class SpecialUrlDetectorImpl(
    private val packageManager: PackageManager,
    private val settingsDataStore: SettingsDataStore
) : SpecialUrlDetector {

    override fun determineType(uri: Uri): UrlType {
        val uriString = uri.toString()

        return when (val scheme = uri.scheme) {
            TEL_SCHEME -> buildTelephone(uriString)
            TELPROMPT_SCHEME -> buildTelephonePrompt(uriString)
            MAILTO_SCHEME -> buildEmail(uriString)
            SMS_SCHEME -> buildSms(uriString)
            SMSTO_SCHEME -> buildSmsTo(uriString)
            HTTP_SCHEME, HTTPS_SCHEME, DATA_SCHEME -> checkForAppLink(uriString)
            ABOUT_SCHEME -> UrlType.Unknown(uriString)
            JAVASCRIPT_SCHEME -> UrlType.SearchQuery(uriString)
            null -> UrlType.SearchQuery(uriString)
            else -> checkForIntent(scheme, uriString)
        }
    }

    private fun buildTelephone(uriString: String): UrlType = UrlType.Telephone(uriString.removePrefix("$TEL_SCHEME:").truncate(PHONE_MAX_LENGTH))

    private fun buildTelephonePrompt(uriString: String): UrlType =
        UrlType.Telephone(uriString.removePrefix("$TELPROMPT_SCHEME:").truncate(PHONE_MAX_LENGTH))

    private fun buildEmail(uriString: String): UrlType = UrlType.Email(uriString.truncate(EMAIL_MAX_LENGTH))

    private fun buildSms(uriString: String): UrlType = UrlType.Sms(uriString.removePrefix("$SMS_SCHEME:").truncate(SMS_MAX_LENGTH))

    private fun buildSmsTo(uriString: String): UrlType = UrlType.Sms(uriString.removePrefix("$SMSTO_SCHEME:").truncate(SMS_MAX_LENGTH))

    private fun checkForAppLink(uriString: String): UrlType {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && settingsDataStore.appLinksEnabled) {
            try {
                val activities = queryActivities(uriString)
                val nonBrowserActivities = keepNonBrowserActivities(activities)

                if (nonBrowserActivities.isNotEmpty()) {
                    nonBrowserActivities.singleOrNull()?.let { resolveInfo ->
                        val nonBrowserIntent = buildNonBrowserIntent(resolveInfo, uriString)
                        return UrlType.AppLink(appIntent = nonBrowserIntent, uriString = uriString)
                    }
                    val excludedComponents = getExcludedComponents(activities)
                    return UrlType.AppLink(excludedComponents = excludedComponents, uriString = uriString)
                }
            } catch (e: URISyntaxException) {
                Timber.w(e, "Failed to parse uri $uriString")
            }
        }
        return UrlType.Web(uriString)
    }

    @Throws(URISyntaxException::class)
    private fun queryActivities(uriString: String): MutableList<ResolveInfo> {
        val browsableIntent = Intent.parseUri(uriString, URI_NO_FLAG)
        browsableIntent.addCategory(Intent.CATEGORY_BROWSABLE)
        return packageManager.queryIntentActivities(browsableIntent, PackageManager.GET_RESOLVED_FILTER)
    }

    private fun keepNonBrowserActivities(activities: List<ResolveInfo>): List<ResolveInfo> {
        return activities.filter { resolveInfo ->
            resolveInfo.filter != null && !(isBrowserInfo(resolveInfo))
        }
    }
    @Throws(URISyntaxException::class)
    private fun buildNonBrowserIntent(nonBrowserActivity: ResolveInfo, uriString: String): Intent {
        val intent = Intent.parseUri(uriString, URI_NO_FLAG)
        intent.component = ComponentName(nonBrowserActivity.activityInfo.packageName, nonBrowserActivity.activityInfo.name)
        return intent
    }

    private fun getExcludedComponents(activities: List<ResolveInfo>): List<ComponentName> {
        return activities.filter { resolveInfo ->
            resolveInfo.filter != null && isBrowserInfo(resolveInfo)
        }.map { ComponentName(it.activityInfo.packageName, it.activityInfo.name) }
    }

    private fun isBrowserInfo(resolveInfo: ResolveInfo) =
        resolveInfo.filter.countDataAuthorities() == 0 && resolveInfo.filter.countDataPaths() == 0

    private fun checkForIntent(scheme: String, uriString: String): UrlType {
        val validUriSchemeRegex = Regex("[a-z][a-zA-Z\\d+.-]+")
        if (scheme.matches(validUriSchemeRegex)) {
            return buildIntent(uriString)
        }

        return UrlType.SearchQuery(uriString)
    }

    private fun buildIntent(uriString: String): UrlType {
        return try {
            val intent = Intent.parseUri(uriString, URI_NO_FLAG)
            val fallbackUrl = intent.getStringExtra(EXTRA_FALLBACK_URL)
            UrlType.NonHttpAppLink(uriString = uriString, intent = intent, fallbackUrl = fallbackUrl)
        } catch (e: URISyntaxException) {
            Timber.w(e, "Failed to parse uri $uriString")
            return UrlType.Unknown(uriString)
        }
    }

    override fun determineType(uriString: String?): UrlType {
        if (uriString == null) return UrlType.Web("")

        return determineType(Uri.parse(uriString))
    }

    private fun String.truncate(maxLength: Int): String = if (this.length > maxLength) this.substring(0, maxLength) else this

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
        private const val URI_NO_FLAG = 0
        const val SMS_MAX_LENGTH = 400
        const val PHONE_MAX_LENGTH = 20
        const val EMAIL_MAX_LENGTH = 1000
    }
}
