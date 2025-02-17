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

package com.duckduckgo.app.browser.webview

import android.net.Uri
import android.webkit.WebResourceRequest
import androidx.annotation.VisibleForTesting
import androidx.core.net.toUri
import com.duckduckgo.app.browser.webview.ExemptedUrlsHolder.ExemptedUrl
import com.duckduckgo.app.browser.webview.RealMaliciousSiteBlockerWebViewIntegration.IsMaliciousViewData
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.di.IsMainProcess
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.Feed
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.IsMaliciousResult
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.IsMaliciousResult.ConfirmedResult
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.IsMaliciousResult.WaitForConfirmation
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.MaliciousStatus
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.MaliciousStatus.Malicious
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.MaliciousStatus.Safe
import com.duckduckgo.privacy.config.api.PrivacyConfigCallbackPlugin
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import java.net.URLDecoder
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import timber.log.Timber

interface MaliciousSiteBlockerWebViewIntegration {

    suspend fun shouldIntercept(
        request: WebResourceRequest,
        documentUri: Uri?,
        confirmationCallback: (maliciousStatus: MaliciousStatus) -> Unit,
    ): IsMaliciousViewData

    fun shouldOverrideUrlLoading(
        url: Uri,
        isForMainFrame: Boolean,
        confirmationCallback: (maliciousStatus: MaliciousStatus) -> Unit,
    ): IsMaliciousViewData

    fun onPageLoadStarted()

    fun onSiteExempted(
        url: Uri,
        feed: Feed,
    )
}

interface ExemptedUrlsHolder {
    data class ExemptedUrl(val url: Uri, val feed: Feed)

    fun addExemptedMaliciousUrl(url: ExemptedUrl)
    val exemptedMaliciousUrls: Set<ExemptedUrl>
}

@ContributesBinding(AppScope::class)
class RealExemptedUrlsHolder @Inject constructor() : ExemptedUrlsHolder {
    override val exemptedMaliciousUrls: Set<ExemptedUrl>
        get() = _exemptedMaliciousUrls
    private val _exemptedMaliciousUrls = mutableSetOf<ExemptedUrl>()

    override fun addExemptedMaliciousUrl(url: ExemptedUrl) {
        _exemptedMaliciousUrls.add(url)
    }
}

@ContributesMultibinding(AppScope::class, PrivacyConfigCallbackPlugin::class)
@ContributesBinding(AppScope::class, MaliciousSiteBlockerWebViewIntegration::class)
class RealMaliciousSiteBlockerWebViewIntegration @Inject constructor(
    private val maliciousSiteProtection: MaliciousSiteProtection,
    private val androidBrowserConfigFeature: AndroidBrowserConfigFeature,
    private val settingsDataStore: SettingsDataStore,
    private val dispatchers: DispatcherProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val exemptedUrlsHolder: ExemptedUrlsHolder,
    @IsMainProcess private val isMainProcess: Boolean,
) : MaliciousSiteBlockerWebViewIntegration, PrivacyConfigCallbackPlugin {

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    val processedUrls = mutableListOf<String>()

    private var isFeatureEnabled = false
    private val isSettingEnabled: Boolean
        get() = settingsDataStore.maliciousSiteProtectionEnabled
    private var currentCheckId = AtomicInteger(0)

    init {
        if (isMainProcess) {
            loadToMemory()
        }
    }

    private fun loadToMemory() {
        appCoroutineScope.launch(dispatchers.io()) {
            isFeatureEnabled = androidBrowserConfigFeature.enableMaliciousSiteProtection().isEnabled()
        }
    }

    override fun onPrivacyConfigDownloaded() {
        loadToMemory()
    }

    sealed class IsMaliciousViewData {
        data object Safe : IsMaliciousViewData()
        data object WaitForConfirmation : IsMaliciousViewData()
        data class MaliciousSite(val url: Uri, val feed: Feed, val exempted: Boolean) : IsMaliciousViewData()
    }

    override suspend fun shouldIntercept(
        request: WebResourceRequest,
        documentUri: Uri?,
        confirmationCallback: (maliciousStatus: MaliciousStatus) -> Unit,
    ): IsMaliciousViewData {
        if (!isEnabled()) {
            return IsMaliciousViewData.Safe
        }
        val url = request.url.let {
            if (it.fragment != null) {
                it.buildUpon().fragment(null).build()
            } else {
                it
            }
        }

        val decodedUrl = URLDecoder.decode(url.toString(), "UTF-8").lowercase()

        if (processedUrls.contains(decodedUrl)) {
            processedUrls.remove(decodedUrl)
            Timber.tag("PhishingAndMalwareDetector").d("Already intercepted, skipping $decodedUrl")
            return IsMaliciousViewData.Safe
        }

        val exemptedUrl = exemptedUrlsHolder.exemptedMaliciousUrls.firstOrNull { it.url.toString() == decodedUrl }

        if (exemptedUrl != null) {
            Timber.tag("MaliciousSiteDetector").d("Previously exempted, skipping $decodedUrl as ${exemptedUrl.feed}")
            return IsMaliciousViewData.MaliciousSite(url, exemptedUrl.feed, true)
        }

        val belongsToCurrentPage = documentUri?.host == request.requestHeaders["Referer"]?.toUri()?.host
        if (request.isForMainFrame || (isForIframe(request) && belongsToCurrentPage)) {
            when (val result = checkMaliciousUrl(decodedUrl, confirmationCallback)) {
                is ConfirmedResult -> {
                    when (val status = result.status) {
                        is Malicious -> {
                            return IsMaliciousViewData.MaliciousSite(url, status.feed, false)
                        }
                        is Safe -> {
                            processedUrls.add(decodedUrl)
                            return IsMaliciousViewData.Safe
                        }
                    }
                }
                is WaitForConfirmation -> {
                    processedUrls.add(decodedUrl)
                    return IsMaliciousViewData.WaitForConfirmation
                }
            }
        }
        return IsMaliciousViewData.Safe
    }

    override fun shouldOverrideUrlLoading(
        url: Uri,
        isForMainFrame: Boolean,
        confirmationCallback: (maliciousStatus: MaliciousStatus) -> Unit,
    ): IsMaliciousViewData {
        return runBlocking {
            if (!isEnabled()) {
                return@runBlocking IsMaliciousViewData.Safe
            }
            val decodedUrl = URLDecoder.decode(url.toString(), "UTF-8").lowercase()

            if (processedUrls.contains(decodedUrl)) {
                processedUrls.remove(decodedUrl)
                Timber.tag("PhishingAndMalwareDetector").d("Already intercepted, skipping $decodedUrl")
                return@runBlocking IsMaliciousViewData.Safe
            }

            val exemptedUrl = exemptedUrlsHolder.exemptedMaliciousUrls.firstOrNull { it.url.toString() == decodedUrl }

            if (exemptedUrl != null) {
                Timber.tag("MaliciousSiteDetector").d("Previously exempted, skipping $decodedUrl")
                return@runBlocking IsMaliciousViewData.MaliciousSite(url, exemptedUrl.feed, true)
            }

            // iframes always go through the shouldIntercept method, so we only need to check the main frame here
            if (isForMainFrame) {
                when (val result = checkMaliciousUrl(decodedUrl, confirmationCallback)) {
                    is ConfirmedResult -> {
                        when (val status = result.status) {
                            is Malicious -> {
                                return@runBlocking IsMaliciousViewData.MaliciousSite(url, status.feed, false)
                            }
                            is Safe -> {
                                processedUrls.add(decodedUrl)
                                return@runBlocking IsMaliciousViewData.Safe
                            }
                        }
                    }
                    is WaitForConfirmation -> {
                        processedUrls.add(decodedUrl)
                        return@runBlocking IsMaliciousViewData.WaitForConfirmation
                    }
                }
            }
            IsMaliciousViewData.Safe
        }
    }

    private suspend fun checkMaliciousUrl(
        url: String,
        confirmationCallback: (maliciousStatus: MaliciousStatus) -> Unit,
    ): IsMaliciousResult {
        val checkId = currentCheckId.incrementAndGet()
        return maliciousSiteProtection.isMalicious(url.toUri()) {
            // if another load has started, we should ignore the result
            val isMalicious = if (checkId == currentCheckId.get()) {
                it
            } else {
                Safe
            }
            processedUrls.clear()
            confirmationCallback(isMalicious)
        }
    }

    private fun isForIframe(request: WebResourceRequest) = request.requestHeaders["Sec-Fetch-Dest"] == "iframe" ||
        request.url.path?.contains("/embed/") == true ||
        request.url.path?.contains("/iframe/") == true ||
        request.requestHeaders["Accept"]?.contains("text/html") == true

    private fun isEnabled(): Boolean {
        return isFeatureEnabled && isSettingEnabled
    }

    override fun onPageLoadStarted() {
        processedUrls.clear()
    }

    override fun onSiteExempted(
        url: Uri,
        feed: Feed,
    ) {
        val convertedUrl = URLDecoder.decode(url.toString(), "UTF-8").lowercase()
        exemptedUrlsHolder.addExemptedMaliciousUrl(ExemptedUrl(convertedUrl.toUri(), feed))
        Timber.tag("MaliciousSiteDetector").d(
            "Added $url to exemptedUrls, contents: ${exemptedUrlsHolder.exemptedMaliciousUrls}",
        )
    }
}
