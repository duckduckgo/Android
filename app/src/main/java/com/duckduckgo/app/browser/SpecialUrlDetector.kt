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
import android.content.Intent.URI_ANDROID_APP_SCHEME
import android.content.Intent.URI_INTENT_SCHEME
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import androidx.annotation.VisibleForTesting
import androidx.core.net.toUri
import com.duckduckgo.app.browser.SpecialUrlDetector.UrlType
import com.duckduckgo.app.browser.applinks.ExternalAppIntentFlagsFeature
import com.duckduckgo.app.browser.duckchat.AIChatQueryDetectionFeature
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckplayer.api.DuckPlayer
import com.duckduckgo.privacy.config.api.AmpLinkType
import com.duckduckgo.privacy.config.api.AmpLinks
import com.duckduckgo.privacy.config.api.TrackingParameters
import com.duckduckgo.subscriptions.api.Subscriptions
import java.net.URISyntaxException
import logcat.LogPriority.WARN
import logcat.asLog
import logcat.logcat

class SpecialUrlDetectorImpl(
    private val packageManager: PackageManager,
    private val ampLinks: AmpLinks,
    private val trackingParameters: TrackingParameters,
    private val subscriptions: Subscriptions,
    private val externalAppIntentFlagsFeature: ExternalAppIntentFlagsFeature,
    private val duckPlayer: DuckPlayer,
    private val duckChat: DuckChat,
    private val aiChatQueryDetectionFeature: AIChatQueryDetectionFeature,
    private val androidBrowserConfigFeature: AndroidBrowserConfigFeature,
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
            JAVASCRIPT_SCHEME, ABOUT_SCHEME, FILE_SCHEME, SITE_SCHEME, BLOB_SCHEME -> UrlType.SearchQuery(uriString)
            FILETYPE_SCHEME, IN_TITLE_SCHEME, IN_URL_SCHEME -> UrlType.SearchQuery(uriString)
            DUCK_SCHEME -> UrlType.DuckScheme(uriString)
            null -> {
                if (subscriptions.shouldLaunchPrivacyProForUrl("https://$uriString")) {
                    UrlType.ShouldLaunchPrivacyProLink
                } else if (aiChatQueryDetectionFeature.self().isEnabled() && duckChat.isDuckChatUrl(uri)) {
                    UrlType.ShouldLaunchDuckChatLink
                } else {
                    UrlType.SearchQuery(uriString)
                }
            }
            else -> {
                val intentFlags = if (scheme == INTENT_SCHEME && androidBrowserConfigFeature.handleIntentScheme().isEnabled()) {
                    URI_INTENT_SCHEME
                } else {
                    URI_ANDROID_APP_SCHEME
                }
                checkForIntent(scheme, uriString, intentFlags)
            }
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

        val uri = uriString.toUri()

        if (duckChat.isDuckChatUrl(uri)) {
            return UrlType.ShouldLaunchDuckChatLink
        }

        if (duckPlayer.willNavigateToDuckPlayer(uri)) {
            return UrlType.ShouldLaunchDuckPlayerLink(url = uri)
        } else {
            try {
                val browsableIntent = Intent.parseUri(uriString, URI_ANDROID_APP_SCHEME).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                }
                val activities = queryActivities(browsableIntent)
                val activity = getDefaultActivity(browsableIntent) ?: activities.firstOrNull()

                val nonBrowserActivities = keepNonBrowserActivities(activities)
                    .filter { it.activityInfo.packageName == activity?.activityInfo?.packageName }

                nonBrowserActivities.singleOrNull()?.let { resolveInfo ->
                    val nonBrowserIntent = buildNonBrowserIntent(resolveInfo, uriString)
                    return UrlType.AppLink(appIntent = nonBrowserIntent, uriString = uriString)
                }
            } catch (e: URISyntaxException) {
                logcat(WARN) { "Failed to parse uri $uriString: ${e.asLog()}" }
            }
        }

        ampLinks.extractCanonicalFromAmpLink(uriString)?.let { ampLinkType ->
            if (ampLinkType is AmpLinkType.ExtractedAmpLink) {
                return UrlType.ExtractedAmpLink(extractedUrl = ampLinkType.extractedUrl)
            } else if (ampLinkType is AmpLinkType.CloakedAmpLink) {
                return UrlType.CloakedAmpLink(ampUrl = ampLinkType.ampUrl)
            }
        }

        if (subscriptions.shouldLaunchPrivacyProForUrl(uriString)) {
            return UrlType.ShouldLaunchPrivacyProLink
        }

        return UrlType.Web(uriString)
    }

    @Throws(URISyntaxException::class)
    private fun queryActivities(intent: Intent): List<ResolveInfo> {
        return try {
            packageManager.queryIntentActivities(intent, PackageManager.GET_RESOLVED_FILTER)
        } catch (t: Throwable) {
            emptyList()
        }
    }

    private fun keepNonBrowserActivities(activities: List<ResolveInfo>): List<ResolveInfo> {
        return activities.filter { resolveInfo ->
            resolveInfo.filter != null && !(isBrowserFilter(resolveInfo.filter))
        }
    }

    private fun getDefaultActivity(intent: Intent): ResolveInfo? {
        return packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
    }

    @Throws(URISyntaxException::class)
    private fun buildNonBrowserIntent(
        nonBrowserActivity: ResolveInfo,
        uriString: String,
    ): Intent {
        val intent = Intent.parseUri(uriString, URI_ANDROID_APP_SCHEME)
        intent.component = ComponentName(nonBrowserActivity.activityInfo.packageName, nonBrowserActivity.activityInfo.name)
        return intent
    }

    private fun isBrowserFilter(filter: IntentFilter) =
        filter.countDataAuthorities() == 0 && filter.countDataPaths() == 0

    @VisibleForTesting
    internal fun checkForIntent(
        scheme: String,
        uriString: String,
        intentFlags: Int,
    ): UrlType {
        val validUriSchemeRegex = Regex("[a-z][a-zA-Z\\d+.-]+")
        if (scheme.matches(validUriSchemeRegex)) {
            return buildIntent(uriString, intentFlags)
        }

        return UrlType.SearchQuery(uriString)
    }

    private fun buildIntent(uriString: String, intentFlags: Int): UrlType {
        return try {
            val intent = Intent.parseUri(uriString, intentFlags)

            if (externalAppIntentFlagsFeature.self().isEnabled()) {
                intent.addCategory(Intent.CATEGORY_BROWSABLE)
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }

            val fallbackUrl = intent.getStringExtra(EXTRA_FALLBACK_URL)
            val fallbackIntent = buildFallbackIntent(fallbackUrl)
            UrlType.NonHttpAppLink(uriString = uriString, intent = intent, fallbackUrl = fallbackUrl, fallbackIntent = fallbackIntent)
        } catch (e: URISyntaxException) {
            logcat(WARN) { "Failed to parse uri $uriString: ${e.asLog()}" }
            return UrlType.Unknown(uriString)
        }
    }

    private fun buildFallbackIntent(fallbackUrl: String?): Intent? {
        if (determineType(fallbackUrl) is UrlType.NonHttpAppLink) {
            return Intent.parseUri(fallbackUrl, URI_ANDROID_APP_SCHEME)
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
        private const val BLOB_SCHEME = "blob"
        private const val EXTRA_FALLBACK_URL = "browser_fallback_url"
        private const val FILETYPE_SCHEME = "filetype"
        private const val IN_TITLE_SCHEME = "intitle"
        private const val IN_URL_SCHEME = "inurl"
        private const val DUCK_SCHEME = "duck"
        private const val INTENT_SCHEME = "intent"
        const val SMS_MAX_LENGTH = 400
        const val PHONE_MAX_LENGTH = 20
        const val EMAIL_MAX_LENGTH = 1000
    }
}
