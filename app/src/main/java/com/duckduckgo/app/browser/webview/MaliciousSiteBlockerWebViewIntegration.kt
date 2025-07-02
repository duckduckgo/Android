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
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.Feed
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.IsMaliciousResult
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.IsMaliciousResult.ConfirmedResult
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.IsMaliciousResult.WaitForConfirmation
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.MaliciousStatus
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.MaliciousStatus.Ignored
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.MaliciousStatus.Malicious
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.MaliciousStatus.Safe
import com.duckduckgo.privacy.config.api.PrivacyConfigCallbackPlugin
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import logcat.logcat

const val SCAM_PROTECTION_LEARN_MORE_URL = "https://duckduckgo.com/duckduckgo-help-pages/threat-protection/scam-blocker"

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

    fun onPageLoadStarted(url: String)

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
    private val pixel: Pixel,
) : MaliciousSiteBlockerWebViewIntegration, PrivacyConfigCallbackPlugin {

    data class ProcessedUrlStatus(val status: MaliciousStatus, val clientSideHit: Boolean)

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    val processedUrls = mutableMapOf<Uri, ProcessedUrlStatus>()

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
        data class Safe(val isForMainFrame: Boolean) : IsMaliciousViewData()
        data object WaitForConfirmation : IsMaliciousViewData()
        data object Ignored : IsMaliciousViewData()

        /**
         * @param exempted true if the site was exempted from blocking by the user
         * @param clientSideHit true if the site was blocked entirely by the client side
         */
        data class MaliciousSite(val url: Uri, val feed: Feed, val exempted: Boolean, val clientSideHit: Boolean) : IsMaliciousViewData()
    }

    override suspend fun shouldIntercept(
        request: WebResourceRequest,
        documentUri: Uri?,
        confirmationCallback: (maliciousStatus: MaliciousStatus) -> Unit,
    ): IsMaliciousViewData {
        if (!isEnabled()) {
            return IsMaliciousViewData.Ignored
        }

        return withContext(dispatchers.io()) {
            val belongsToCurrentPage = documentUri?.host == request.requestHeaders["Referer"]?.toUri()?.host
            val isForIframe = (isForIframe(request) && belongsToCurrentPage)

            if (request.isForMainFrame || isForIframe) {
                val url = request.url
                val mainframeUrl = if (isForIframe) documentUri else url

                getProcessedOrExempted(url, mainframeUrl, request.isForMainFrame)?.let { return@withContext it }

                val result = checkMaliciousUrl(url) {
                    if (isForIframe && it is Malicious) {
                        firePixelForMaliciousIframe(it.feed)
                    }
                    confirmationCallback(it)
                }
                when (result) {
                    is ConfirmedResult -> {
                        processedUrls[url] = ProcessedUrlStatus(result.status, clientSideHit = true)
                        when (val status = result.status) {
                            is Malicious -> {
                                if (isForIframe) {
                                    firePixelForMaliciousIframe(status.feed)
                                }
                                return@withContext IsMaliciousViewData.MaliciousSite(url, status.feed, exempted = false, clientSideHit = true)
                            }
                            is Safe -> return@withContext IsMaliciousViewData.Safe(request.isForMainFrame)
                            is Ignored -> return@withContext IsMaliciousViewData.Ignored
                        }
                    }

                    is WaitForConfirmation -> {
                        return@withContext IsMaliciousViewData.WaitForConfirmation
                    }
                }
            }
            return@withContext IsMaliciousViewData.Ignored
        }
    }

    private fun getExemptedUrl(url: Uri): ExemptedUrl? {
        return exemptedUrlsHolder.exemptedMaliciousUrls.firstOrNull {
            it.url == url
        }
    }

    private fun getProcessedOrExempted(requestUrl: Uri, mainframeUrl: Uri?, isForMainFrame: Boolean): IsMaliciousViewData? {
        val exemptedUrl = mainframeUrl?.let { getExemptedUrl(it) }

        if (exemptedUrl != null) {
            logcat { "Previously exempted, skipping $requestUrl as ${exemptedUrl.feed}" }
            return IsMaliciousViewData.MaliciousSite(requestUrl, exemptedUrl.feed, true, clientSideHit = true)
        }

        processedUrls[requestUrl]?.let {
            processedUrls.remove(requestUrl)
            logcat { "Already intercepted, skipping $requestUrl, status: $it" }
            return when (it.status) {
                is Safe -> IsMaliciousViewData.Safe(isForMainFrame)
                is Malicious -> IsMaliciousViewData.MaliciousSite(requestUrl, it.status.feed, false, it.clientSideHit)
                is Ignored -> IsMaliciousViewData.Ignored
            }
        }
        return null
    }

    override fun shouldOverrideUrlLoading(
        url: Uri,
        isForMainFrame: Boolean,
        confirmationCallback: (maliciousStatus: MaliciousStatus) -> Unit,
    ): IsMaliciousViewData {
        return runBlocking {
            if (!isEnabled()) {
                return@runBlocking IsMaliciousViewData.Ignored
            }
            // iframes always go through the shouldIntercept method, so we only need to check the main frame here
            if (isForMainFrame) {
                getProcessedOrExempted(url, url, true)?.let {
                    return@runBlocking it
                }

                when (val result = checkMaliciousUrl(url, confirmationCallback)) {
                    is ConfirmedResult -> {
                        val status = result.status
                        processedUrls[url] = ProcessedUrlStatus(status, clientSideHit = true)
                        when (status) {
                            is Malicious -> return@runBlocking IsMaliciousViewData.MaliciousSite(
                                url,
                                status.feed,
                                exempted = false,
                                clientSideHit = true,
                            )
                            is Safe -> return@runBlocking IsMaliciousViewData.Safe(true)
                            is Ignored -> return@runBlocking IsMaliciousViewData.Ignored
                        }
                    }
                    is WaitForConfirmation -> {
                        return@runBlocking IsMaliciousViewData.WaitForConfirmation
                    }
                }
            }
            IsMaliciousViewData.Ignored
        }
    }

    private fun firePixelForMaliciousIframe(feed: Feed) {
        pixel.fire(AppPixelName.MALICIOUS_SITE_DETECTED_IN_IFRAME, mapOf("category" to feed.name.lowercase()))
    }

    private suspend fun checkMaliciousUrl(
        url: Uri,
        confirmationCallback: (maliciousStatus: MaliciousStatus) -> Unit,
    ): IsMaliciousResult {
        val checkId = currentCheckId.incrementAndGet()
        return maliciousSiteProtection.isMalicious(url) {
            // if another load has started, we should ignore the result
            val isMalicious = if (checkId == currentCheckId.get()) {
                it
            } else {
                Safe
            }
            processedUrls[url] = ProcessedUrlStatus(it, clientSideHit = false)
            confirmationCallback(isMalicious)
        }
    }

    private fun isForIframe(request: WebResourceRequest) = request.isForMainFrame.not() && (
        request.requestHeaders["Sec-Fetch-Dest"] == "iframe" ||
            request.url.path?.contains("/embed/") == true ||
            request.url.path?.contains("/iframe/") == true ||
            request.requestHeaders["Accept"]?.contains("text/html") == true
        )

    private fun isEnabled(): Boolean {
        return isFeatureEnabled && isSettingEnabled
    }

    override fun onPageLoadStarted(url: String) {
        if (!isEnabled()) return
        /* onPageLoadStarted is often called after shouldOverride/shouldIntercept, therefore, if the URL
         * is already stored, we don't clear the processedUrls map to avoid re-checking the URL for the same
         * page load.
         */
        if (!processedUrls.contains(url.toUri())) {
            processedUrls.clear()
        }
    }

    override fun onSiteExempted(
        url: Uri,
        feed: Feed,
    ) {
        exemptedUrlsHolder.addExemptedMaliciousUrl(ExemptedUrl(url, feed))
        logcat { "Added $url to exemptedUrls" }
    }
}
