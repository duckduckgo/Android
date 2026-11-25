package com.example.privacy

import android.net.Uri
import android.util.Patterns
import com.example.model.BlockedTracker
import com.example.model.TrackerCategory
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class TrackerRule(
    val hostSuffix: String,
    val company: String,
    val category: TrackerCategory,
)

object PrivacyEngine {

    private val trackerRules: List<TrackerRule> = listOf(
        // Advertising & Ad Networks
        TrackerRule("doubleclick.net", "Google Ads", TrackerCategory.ADVERTISING),
        TrackerRule("googlesyndication.com", "Google AdSense", TrackerCategory.ADVERTISING),
        TrackerRule("googleadservices.com", "Google Ads", TrackerCategory.ADVERTISING),
        TrackerRule("adservice.google.com", "Google Ads", TrackerCategory.ADVERTISING),
        TrackerRule("ads.pubmatic.com", "PubMatic", TrackerCategory.ADVERTISING),
        TrackerRule("pubmatic.com", "PubMatic", TrackerCategory.ADVERTISING),
        TrackerRule("criteo.com", "Criteo", TrackerCategory.ADVERTISING),
        TrackerRule("criteo.net", "Criteo", TrackerCategory.ADVERTISING),
        TrackerRule("taboola.com", "Taboola", TrackerCategory.ADVERTISING),
        TrackerRule("outbrain.com", "Outbrain", TrackerCategory.ADVERTISING),
        TrackerRule("adnxs.com", "Microsoft Advertising", TrackerCategory.ADVERTISING),
        TrackerRule("rubiconproject.com", "Magnite", TrackerCategory.ADVERTISING),
        TrackerRule("openx.net", "OpenX", TrackerCategory.ADVERTISING),
        TrackerRule("casalemedia.com", "Index Exchange", TrackerCategory.ADVERTISING),
        TrackerRule("adsrvr.org", "The Trade Desk", TrackerCategory.ADVERTISING),
        TrackerRule("amazon-adsystem.com", "Amazon Advertising", TrackerCategory.ADVERTISING),
        TrackerRule("media.net", "Media.net", TrackerCategory.ADVERTISING),
        TrackerRule("sharethrough.com", "Sharethrough", TrackerCategory.ADVERTISING),
        TrackerRule("doubleverify.com", "DoubleVerify", TrackerCategory.ADVERTISING),
        TrackerRule("moatads.com", "Oracle Moat", TrackerCategory.ADVERTISING),
        TrackerRule("ads.yahoo.com", "Yahoo Advertising", TrackerCategory.ADVERTISING),
        TrackerRule("bidswitch.net", "BidSwitch", TrackerCategory.ADVERTISING),
        TrackerRule("smartadserver.com", "Equativ", TrackerCategory.ADVERTISING),

        // Analytics & Attribution
        TrackerRule("google-analytics.com", "Google Analytics", TrackerCategory.ANALYTICS),
        TrackerRule("googletagmanager.com", "Google Tag Manager", TrackerCategory.ANALYTICS),
        TrackerRule("app.adjust.com", "Adjust", TrackerCategory.ANALYTICS),
        TrackerRule("adjust.com", "Adjust", TrackerCategory.ANALYTICS),
        TrackerRule("api2.branch.io", "Branch Metrics", TrackerCategory.ANALYTICS),
        TrackerRule("branch.io", "Branch Metrics", TrackerCategory.ANALYTICS),
        TrackerRule("app.link", "Branch Metrics", TrackerCategory.ANALYTICS),
        TrackerRule("scorecardresearch.com", "Comscore", TrackerCategory.ANALYTICS),
        TrackerRule("quantserve.com", "Quantcast", TrackerCategory.ANALYTICS),
        TrackerRule("hotjar.com", "Hotjar", TrackerCategory.ANALYTICS),
        TrackerRule("mixpanel.com", "Mixpanel", TrackerCategory.ANALYTICS),
        TrackerRule("segment.io", "Twilio Segment", TrackerCategory.ANALYTICS),
        TrackerRule("segment.com", "Twilio Segment", TrackerCategory.ANALYTICS),
        TrackerRule("amplitude.com", "Amplitude", TrackerCategory.ANALYTICS),
        TrackerRule("newrelic.com", "New Relic", TrackerCategory.ANALYTICS),
        TrackerRule("nr-data.net", "New Relic", TrackerCategory.ANALYTICS),
        TrackerRule("clarity.ms", "Microsoft Clarity", TrackerCategory.ANALYTICS),
        TrackerRule("omtrdc.net", "Adobe Analytics", TrackerCategory.ANALYTICS),
        TrackerRule("demdex.net", "Adobe Audience Manager", TrackerCategory.ANALYTICS),
        TrackerRule("chartbeat.com", "Chartbeat", TrackerCategory.ANALYTICS),
        TrackerRule("chartbeat.net", "Chartbeat", TrackerCategory.ANALYTICS),
        TrackerRule("fullstory.com", "FullStory", TrackerCategory.ANALYTICS),
        TrackerRule("mouseflow.com", "Mouseflow", TrackerCategory.ANALYTICS),
        TrackerRule("crazyegg.com", "Crazy Egg", TrackerCategory.ANALYTICS),
        TrackerRule("optimizely.com", "Optimizely", TrackerCategory.ANALYTICS),
        TrackerRule("appsflyer.com", "AppsFlyer", TrackerCategory.ANALYTICS),
        TrackerRule("sentry.io", "Sentry", TrackerCategory.ANALYTICS),
        TrackerRule("bugsnag.com", "Bugsnag", TrackerCategory.ANALYTICS),

        // Social Trackers
        TrackerRule("connect.facebook.net", "Meta", TrackerCategory.SOCIAL),
        TrackerRule("facebook.com/tr", "Meta", TrackerCategory.SOCIAL),
        TrackerRule("pixel.facebook.com", "Meta", TrackerCategory.SOCIAL),
        TrackerRule("analytics.tiktok.com", "TikTok", TrackerCategory.SOCIAL),
        TrackerRule("ads.linkedin.com", "LinkedIn", TrackerCategory.SOCIAL),
        TrackerRule("px.ads.linkedin.com", "LinkedIn", TrackerCategory.SOCIAL),
        TrackerRule("snap.licdn.com", "LinkedIn", TrackerCategory.SOCIAL),
        TrackerRule("ads-twitter.com", "X (Twitter)", TrackerCategory.SOCIAL),
        TrackerRule("analytics.twitter.com", "X (Twitter)", TrackerCategory.SOCIAL),
        TrackerRule("static.ads-twitter.com", "X (Twitter)", TrackerCategory.SOCIAL),
        TrackerRule("ct.pinterest.com", "Pinterest", TrackerCategory.SOCIAL),
        TrackerRule("tr.snapchat.com", "Snap", TrackerCategory.SOCIAL),
        TrackerRule("sc-static.net", "Snap", TrackerCategory.SOCIAL),

        // Fingerprinting & Identity Brokers
        TrackerRule("fpjs.io", "FingerprintJS", TrackerCategory.FINGERPRINTING),
        TrackerRule("fingerprintjs.com", "FingerprintJS", TrackerCategory.FINGERPRINTING),
        TrackerRule("crwdcntrl.net", "Lotame", TrackerCategory.FINGERPRINTING),
        TrackerRule("bluekai.com", "Oracle BlueKai", TrackerCategory.FINGERPRINTING),
        TrackerRule("rlcdn.com", "LiveRamp", TrackerCategory.FINGERPRINTING),
        TrackerRule("tapad.com", "Tapad", TrackerCategory.FINGERPRINTING),
        TrackerRule("uidapi.com", "Unified ID", TrackerCategory.FINGERPRINTING),
        TrackerRule("id5-sync.com", "ID5", TrackerCategory.FINGERPRINTING),
    )

    private val trackingQueryParams = setOf(
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
        "utm_id", "utm_source_platform", "utm_creative_format",
        "fbclid", "fb_source", "fb_action_ids", "fb_action_types",
        "gclid", "dclid", "gbraid", "wbraid", "msclkid",
        "mc_cid", "mc_eid", "igshid", "yclid", "_hsenc", "_hsmi",
        "vero_id", "oly_anon_id", "oly_enc_id", "wickedid",
        "mkt_tok", "s_cid", "__s", "rb_clickid", "twclid",
    )

    private val localNonUpgradableHosts = setOf("localhost", "127.0.0.1", "10.0.2.2")

    fun extractHost(url: String): String? {
        return try {
            Uri.parse(url).host?.lowercase()?.removePrefix("www.")
        } catch (_: Exception) {
            null
        }
    }

    fun matchTracker(requestUrl: String, pageUrl: String?): BlockedTracker? {
        val requestHost = try {
            Uri.parse(requestUrl).host?.lowercase() ?: return null
        } catch (_: Exception) {
            return null
        }

        val pageHost = pageUrl?.let { extractHost(it) }
        // Don't block first-party resources from the exact page domain
        if (pageHost != null && (requestHost == pageHost || requestHost.endsWith(".$pageHost"))) {
            return null
        }

        val rule = trackerRules.firstOrNull { rule ->
            requestHost == rule.hostSuffix || requestHost.endsWith(".${rule.hostSuffix}")
        } ?: return null

        return BlockedTracker(
            domain = requestHost,
            company = rule.company,
            category = rule.category,
        )
    }

    fun isUrlLike(input: String): Boolean {
        val trimmed = input.trim()
        if (trimmed.isEmpty() || trimmed.contains(" ")) return false
        if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true) ||
            trimmed.startsWith("about:", ignoreCase = true)
        ) {
            return true
        }
        if (trimmed.startsWith("localhost", ignoreCase = true)) return true
        return Patterns.WEB_URL.matcher(trimmed).matches() ||
            trimmed.matches(Regex("^[a-zA-Z0-9-]+(\\.[a-zA-Z0-9-]+)+(:\\d+)?(/.*)?$"))
    }

    fun buildTargetUrl(
        rawInput: String,
        httpsUpgradesEnabled: Boolean,
        stripTrackingParamsEnabled: Boolean,
    ): Pair<String, Boolean> {
        val trimmed = rawInput.trim()
        if (!isUrlLike(trimmed)) {
            val encoded = URLEncoder.encode(trimmed, StandardCharsets.UTF_8.toString())
            return "https://duckduckgo.com/?q=$encoded" to false
        }

        var upgraded = false
        var normalized = when {
            trimmed.startsWith("http://", ignoreCase = true) -> {
                val host = extractHost(trimmed)
                if (httpsUpgradesEnabled && host != null && !localNonUpgradableHosts.contains(host) && !isIpAddress(host)) {
                    upgraded = true
                    "https://" + trimmed.substring(7)
                } else {
                    trimmed
                }
            }
            trimmed.startsWith("https://", ignoreCase = true) || trimmed.startsWith("about:", ignoreCase = true) -> trimmed
            else -> {
                val lower = trimmed.lowercase()
                if (lower.startsWith("localhost") || isIpAddress(lower.substringBefore(":").substringBefore("/"))) {
                    "http://$trimmed"
                } else {
                    if (httpsUpgradesEnabled) upgraded = true
                    "https://$trimmed"
                }
            }
        }

        if (stripTrackingParamsEnabled) {
            normalized = stripTrackingParameters(normalized)
        }

        return normalized to upgraded
    }

    fun stripTrackingParameters(url: String): String {
        return try {
            val uri = Uri.parse(url)
            val paramNames = uri.queryParameterNames
            if (paramNames.isEmpty() || paramNames.none { trackingQueryParams.contains(it.lowercase()) }) {
                return url
            }
            val builder = uri.buildUpon().clearQuery()
            for (name in paramNames) {
                if (!trackingQueryParams.contains(name.lowercase())) {
                    for (value in uri.getQueryParameters(name)) {
                        builder.appendQueryParameter(name, value)
                    }
                }
            }
            builder.build().toString()
        } catch (_: Exception) {
            url
        }
    }

    private fun isIpAddress(host: String): Boolean {
        return host.matches(Regex("^\\d{1,3}(\\.\\d{1,3}){3}$"))
    }
}
