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

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import com.duckduckgo.app.browser.SpecialUrlDetector.UrlType
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.privacy.config.api.AmpLinks
import com.duckduckgo.privacy.config.api.AmpLinkType
import com.duckduckgo.privacy.config.api.TrackingParameters
import timber.log.Timber
import java.net.URISyntaxException

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
            val excludedComponents: List<ComponentName>? = null,
            val uriString: String
        ) : UrlType()

        class NonHttpAppLink(
            val uriString: String,
            val intent: Intent,
            val fallbackUrl: String?,
            val fallbackIntent: Intent? = null
        ) : UrlType()

        class SearchQuery(val query: String) : UrlType()
        class Unknown(val uriString: String) : UrlType()
        class ExtractedAmpLink(val extractedUrl: String) : UrlType()
        class CloakedAmpLink(val ampUrl: String) : UrlType()
        class TrackingParameterLink(val cleanedUrl: String) : UrlType()
    }
}

class SpecialUrlDetectorImpl(
    private val packageManager: PackageManager,
    private val ampLinks: AmpLinks,
    private val trackingParameters: TrackingParameters,
    private val appBuildConfig: AppBuildConfig
) : SpecialUrlDetector {

    override fun determineType(initiatingUrl: String?, uri: Uri): UrlType {
        val uriString = uri.toString()

        return when (val scheme = uri.scheme) {
            TEL_SCHEME -> buildTelephone(uriString)
            TELPROMPT_SCHEME -> buildTelephonePrompt(uriString)
            MAILTO_SCHEME -> buildEmail(uriString)
            SMS_SCHEME -> buildSms(uriString)
            SMSTO_SCHEME -> buildSmsTo(uriString)
            HTTP_SCHEME, HTTPS_SCHEME, DATA_SCHEME -> processUrl(initiatingUrl, uriString)
            JAVASCRIPT_SCHEME, ABOUT_SCHEME, FILE_SCHEME, SITE_SCHEME -> UrlType.SearchQuery(uriString)
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

    @Suppress("NewApi") // we use appBuildConfig
    override fun processUrl(initiatingUrl: String?, uriString: String): UrlType {
        trackingParameters.cleanTrackingParameters(initiatingUrl = initiatingUrl, url = uriString)?.let { cleanedUrl ->
            return UrlType.TrackingParameterLink(cleanedUrl = cleanedUrl)
        }

        if (appBuildConfig.sdkInt >= Build.VERSION_CODES.N) {
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

        ampLinks.extractCanonicalFromAmpLink(uriString)?.let { ampLinkType ->
            if (ampLinkType is AmpLinkType.ExtractedAmpLink) {
                return UrlType.ExtractedAmpLink(extractedUrl = ampLinkType.extractedUrl)
            } else if (ampLinkType is AmpLinkType.CloakedAmpLink) {
                return UrlType.CloakedAmpLink(ampUrl = ampLinkType.ampUrl)
            }
        }
        return UrlType.Web(uriString)
    }

    @SuppressLint("WrongConstant")
    @Throws(URISyntaxException::class)
    private fun queryActivities(uriString: String): MutableList<ResolveInfo> {
        val browsableIntent = Intent.parseUri(uriString, URI_NO_FLAG)
        browsableIntent.addCategory(Intent.CATEGORY_BROWSABLE)
        return packageManager.queryIntentActivities(browsableIntent, PackageManager.GET_RESOLVED_FILTER)
    }

    private fun keepNonBrowserActivities(activities: List<ResolveInfo>): List<ResolveInfo> {
        return activities.filter { resolveInfo ->
            resolveInfo.filter != null && !(isBrowserFilter(resolveInfo.filter))
        }
    }

    @SuppressLint("WrongConstant")
    @Throws(URISyntaxException::class)
    private fun buildNonBrowserIntent(
        nonBrowserActivity: ResolveInfo,
        uriString: String
    ): Intent {
        val intent = Intent.parseUri(uriString, URI_NO_FLAG)
        intent.component = ComponentName(nonBrowserActivity.activityInfo.packageName, nonBrowserActivity.activityInfo.name)
        return intent
    }

    private fun getExcludedComponents(activities: List<ResolveInfo>): List<ComponentName> {
        return activities.filter { resolveInfo ->
            resolveInfo.filter != null && isBrowserFilter(resolveInfo.filter)
        }.map { ComponentName(it.activityInfo.packageName, it.activityInfo.name) }
    }

    private fun isBrowserFilter(filter: IntentFilter) =
        filter.countDataAuthorities() == 0 && filter.countDataPaths() == 0

    private fun checkForIntent(
        scheme: String,
        uriString: String
    ): UrlType {
        val validUriSchemeRegex = Regex("[a-z][a-zA-Z\\d+.-]+")
        if (scheme.matches(validUriSchemeRegex)) {
            return buildIntent(uriString)
        }

        return UrlType.SearchQuery(uriString)
    }

    @SuppressLint("WrongConstant")
    private fun buildIntent(uriString: String): UrlType {
        return try {
            val intent = Intent.parseUri(uriString, URI_NO_FLAG)
            val fallbackUrl = intent.getStringExtra(EXTRA_FALLBACK_URL)
            val fallbackIntent = buildFallbackIntent(fallbackUrl)
            UrlType.NonHttpAppLink(uriString = uriString, intent = intent, fallbackUrl = fallbackUrl, fallbackIntent = fallbackIntent)
        } catch (e: URISyntaxException) {
            Timber.w(e, "Failed to parse uri $uriString")
            return UrlType.Unknown(uriString)
        }
    }

    @SuppressLint("WrongConstant")
    private fun buildFallbackIntent(fallbackUrl: String?): Intent? {
        if (determineType(fallbackUrl) is UrlType.NonHttpAppLink) {
            return Intent.parseUri(fallbackUrl, URI_NO_FLAG)
        }
        return null
    }

    override fun determineType(uriString: String?): UrlType {
        if (uriString == null) return UrlType.Web("")

        return determineType(initiatingUrl = null, uri = Uri.parse(uriString))
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
        private const val FILE_SCHEME = "file"
        private const val SITE_SCHEME = "site"
        private const val EXTRA_FALLBACK_URL = "browser_fallback_url"
        private const val URI_NO_FLAG = 0
        const val SMS_MAX_LENGTH = 400
        const val PHONE_MAX_LENGTH = 20
        const val EMAIL_MAX_LENGTH = 1000
    }
}
