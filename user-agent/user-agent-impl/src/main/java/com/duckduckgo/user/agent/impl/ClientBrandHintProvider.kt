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

package com.duckduckgo.user.agent.impl

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.WebSettings
import androidx.webkit.UserAgentMetadata
import androidx.webkit.UserAgentMetadata.BrandVersion
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.user.agent.api.ClientBrandHintProvider
import com.duckduckgo.user.agent.impl.remoteconfig.BrandingChange
import com.duckduckgo.user.agent.impl.remoteconfig.BrandingChange.Change
import com.duckduckgo.user.agent.impl.remoteconfig.BrandingChange.None
import com.duckduckgo.user.agent.impl.remoteconfig.ClientBrandHintFeature
import com.duckduckgo.user.agent.impl.remoteconfig.ClientBrandHintFeatureSettingsRepository
import com.duckduckgo.user.agent.impl.remoteconfig.ClientBrandsHints
import com.duckduckgo.user.agent.impl.remoteconfig.ClientBrandsHints.CHROME
import com.duckduckgo.user.agent.impl.remoteconfig.ClientBrandsHints.DDG
import com.duckduckgo.user.agent.impl.remoteconfig.ClientBrandsHints.WEBVIEW
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import logcat.LogPriority.INFO
import logcat.LogPriority.VERBOSE
import logcat.logcat

@ContributesBinding(AppScope::class)
class RealClientBrandHintProvider @Inject constructor(
    private val clientBrandHintFeature: ClientBrandHintFeature,
    private val repository: ClientBrandHintFeatureSettingsRepository,
) : ClientBrandHintProvider {

    private var currentDomain: String? = null
    private var currentBranding: ClientBrandsHints = DDG

    override fun setDefault(settings: WebSettings) {
        if (clientBrandHintFeature.self().isEnabled()) {
            logcat(VERBOSE) { "ClientBrandHintProvider: branding enabled, initialising metadata with DuckDuckGo branding" }
            setUserAgentMetadata(settings, DEFAULT_ENABLED_BRANDING)
        } else {
            logcat(VERBOSE) { "ClientBrandHintProvider: branding disabled, initialising metadata with Google Chrome branding" }
            setUserAgentMetadata(settings, DEFAULT_DISABLED_BRANDING)
        }
    }

    override fun shouldChangeBranding(
        documentUrl: String,
    ): Boolean {
        logcat(VERBOSE) { "ClientBrandHintProvider: should branding change for $documentUrl?" }
        val brandingChange = calculateBrandingChange(documentUrl) is Change
        return brandingChange
    }

    private fun calculateBrandingChange(documentUrl: String): BrandingChange {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) {
            val documentDomain = Uri.parse(documentUrl).host

            if (!clientBrandHintFeature.self().isEnabled()) {
                return if (currentBranding != DEFAULT_DISABLED_BRANDING) {
                    logcat(VERBOSE) { "ClientBrandHintProvider: branding is disabled but current branding is not default" }
                    Change(DEFAULT_DISABLED_BRANDING)
                } else {
                    logcat(VERBOSE) { "ClientBrandHintProvider: branding for is disabled, default branding already applied" }
                    None
                }
            }

            logcat(VERBOSE) { "ClientBrandHintProvider: check brand for request url $documentUrl with domain $documentDomain" }
            logcat(VERBOSE) { "ClientBrandHintProvider: currentDomain is $currentDomain currentBranding is $currentBranding" }

            if (currentDomain == documentDomain) {
                logcat { "ClientBrandHintProvider: Branding already applied for $currentDomain, skipping" }
                return None
            }

            val customBranding = repository.clientBrandHints.filter { it.domain == documentDomain }
            if (customBranding.isEmpty()) {
                logcat(INFO) { "ClientBrandHintProvider: $documentDomain doesn't have custom branding" }
                return if (currentBranding == DEFAULT_ENABLED_BRANDING) {
                    logcat(INFO) { "ClientBrandHintProvider: branding already active, skipping" }
                    None
                } else {
                    logcat(INFO) { "ClientBrandHintProvider: $documentUrl is not an exception, change to default braning" }
                    Change(DEFAULT_ENABLED_BRANDING)
                }
            } else {
                val branding = customBranding.first().brand
                return if (branding == currentBranding) {
                    logcat(INFO) { "ClientBrandHintProvider: branding already active, skipping" }
                    None
                } else {
                    logcat(INFO) { "ClientBrandHintProvider: $documentUrl has custom branding, change branding to $branding" }
                    Change(branding)
                }
            }
        } else {
            return None
        }
    }

    override fun setOn(
        settings: WebSettings?,
        documentUrl: String,
    ) {
        if (settings == null) {
            return
        }

        val brandingChange = calculateBrandingChange(documentUrl)
        currentDomain = Uri.parse(documentUrl).host
        if (brandingChange is Change) {
            setUserAgentMetadata(settings, brandingChange.branding)
        }
    }

    @SuppressLint("RequiresFeature")
    private fun setUserAgentMetadata(
        settings: WebSettings,
        branding: ClientBrandsHints,
    ) {
        // Check if WebView supports user agent metadata
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) {
            logcat(VERBOSE) { "ClientBrandHintProvider: USER_AGENT_METADATA not supported by WebView, skipping" }
            return
        }

        currentBranding = branding
        val metadata = WebSettingsCompat.getUserAgentMetadata(settings)
        val finalBrandList = metadata.brandVersionList.map {
            when (currentBranding) {
                CHROME -> {
                    if (it.brand.contains(DDG.getBrand()) || it.brand.contains(WEBVIEW.getBrand())) {
                        BrandVersion.Builder()
                            .setBrand(currentBranding.getBrand())
                            .setFullVersion(it.fullVersion)
                            .setMajorVersion(it.majorVersion)
                            .build()
                    } else {
                        it
                    }
                }

                DDG -> {
                    if (it.brand.contains(CHROME.getBrand()) || it.brand.contains(WEBVIEW.getBrand())) {
                        BrandVersion.Builder()
                            .setBrand(currentBranding.getBrand())
                            .setFullVersion(it.fullVersion)
                            .setMajorVersion(it.majorVersion)
                            .build()
                    } else {
                        it
                    }
                }

                WEBVIEW -> {
                    if (it.brand.contains(CHROME.getBrand()) || it.brand.contains(DDG.getBrand())) {
                        BrandVersion.Builder()
                            .setBrand(currentBranding.getBrand())
                            .setFullVersion(it.fullVersion)
                            .setMajorVersion(it.majorVersion)
                            .build()
                    } else {
                        it
                    }
                }
            }
        }
        logcat(INFO) { "ClientBrandHintProvider: original Brand List ${metadata.brandVersionList}" }
        logcat(INFO) { "ClientBrandHintProvider: updated Brand List $finalBrandList" }

        val ua = UserAgentMetadata.Builder()
            .setPlatform(metadata.platform)
            .setPlatformVersion(metadata.platformVersion)
            .setFullVersion(metadata.fullVersion)
            .setModel(metadata.model)
            .setBitness(metadata.bitness)
            .setArchitecture(metadata.architecture)
            .setWow64(metadata.isWow64)
            .setMobile(metadata.isMobile)
            .setBrandVersionList(finalBrandList)
            .build()
        WebSettingsCompat.setUserAgentMetadata(settings, ua)
    }

    companion object {
        private val DEFAULT_ENABLED_BRANDING: ClientBrandsHints = DDG
        private val DEFAULT_DISABLED_BRANDING: ClientBrandsHints = CHROME
    }
}
