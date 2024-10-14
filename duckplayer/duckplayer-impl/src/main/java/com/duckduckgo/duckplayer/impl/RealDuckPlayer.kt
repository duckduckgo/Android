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

package com.duckduckgo.duckplayer.impl

import android.content.res.Configuration
import android.net.Uri
import android.webkit.MimeTypeMap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.core.net.toUri
import androidx.fragment.app.FragmentManager
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Daily
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.UrlScheme.Companion.duck
import com.duckduckgo.common.utils.UrlScheme.Companion.https
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckplayer.api.DuckPlayer
import com.duckduckgo.duckplayer.api.DuckPlayer.DuckPlayerState
import com.duckduckgo.duckplayer.api.DuckPlayer.DuckPlayerState.DISABLED
import com.duckduckgo.duckplayer.api.DuckPlayer.DuckPlayerState.DISABLED_WIH_HELP_LINK
import com.duckduckgo.duckplayer.api.DuckPlayer.DuckPlayerState.ENABLED
import com.duckduckgo.duckplayer.api.DuckPlayer.UserPreferences
import com.duckduckgo.duckplayer.api.ORIGIN_QUERY_PARAM
import com.duckduckgo.duckplayer.api.ORIGIN_QUERY_PARAM_AUTO
import com.duckduckgo.duckplayer.api.ORIGIN_QUERY_PARAM_OVERLAY
import com.duckduckgo.duckplayer.api.ORIGIN_QUERY_PARAM_SERP
import com.duckduckgo.duckplayer.api.ORIGIN_QUERY_PARAM_SERP_AUTO
import com.duckduckgo.duckplayer.api.PrivatePlayerMode.AlwaysAsk
import com.duckduckgo.duckplayer.api.PrivatePlayerMode.Disabled
import com.duckduckgo.duckplayer.api.PrivatePlayerMode.Enabled
import com.duckduckgo.duckplayer.api.YOUTUBE_HOST
import com.duckduckgo.duckplayer.api.YOUTUBE_MOBILE_HOST
import com.duckduckgo.duckplayer.impl.DuckPlayerPixelName.DUCK_PLAYER_DAILY_UNIQUE_VIEW
import com.duckduckgo.duckplayer.impl.DuckPlayerPixelName.DUCK_PLAYER_OVERLAY_YOUTUBE_IMPRESSIONS
import com.duckduckgo.duckplayer.impl.DuckPlayerPixelName.DUCK_PLAYER_OVERLAY_YOUTUBE_WATCH_HERE
import com.duckduckgo.duckplayer.impl.DuckPlayerPixelName.DUCK_PLAYER_VIEW_FROM_OTHER
import com.duckduckgo.duckplayer.impl.DuckPlayerPixelName.DUCK_PLAYER_VIEW_FROM_SERP
import com.duckduckgo.duckplayer.impl.DuckPlayerPixelName.DUCK_PLAYER_VIEW_FROM_YOUTUBE_AUTOMATIC
import com.duckduckgo.duckplayer.impl.DuckPlayerPixelName.DUCK_PLAYER_VIEW_FROM_YOUTUBE_MAIN_OVERLAY
import com.duckduckgo.duckplayer.impl.DuckPlayerPixelName.DUCK_PLAYER_WATCH_ON_YOUTUBE
import com.duckduckgo.duckplayer.impl.ui.DuckPlayerPrimeBottomSheet
import com.duckduckgo.duckplayer.impl.ui.DuckPlayerPrimeDialogFragment
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import java.io.InputStream
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private const val DUCK_PLAYER_VIDEO_ID_QUERY_PARAM = "videoID"
private const val DUCK_PLAYER_OPEN_IN_YOUTUBE_PATH = "openInYoutube"
private const val DUCK_PLAYER_DOMAIN = "player"
private const val DUCK_PLAYER_URL_BASE = "$duck://$DUCK_PLAYER_DOMAIN/"
private const val DUCK_PLAYER_ASSETS_PATH = "duckplayer/"
private const val DUCK_PLAYER_ASSETS_INDEX_PATH = "${DUCK_PLAYER_ASSETS_PATH}index.html"

interface DuckPlayerInternal : DuckPlayer {
    /**
     * Retrieves the YouTube embed URL.
     *
     * @return The YouTube embed URL.
     */
    suspend fun getYouTubeEmbedUrl(): String
}

@SingleInstanceIn(AppScope::class)

@ContributesBinding(AppScope::class, boundType = DuckPlayer::class)
@ContributesBinding(AppScope::class, boundType = DuckPlayerInternal::class)
class RealDuckPlayer @Inject constructor(
    private val duckPlayerFeatureRepository: DuckPlayerFeatureRepository,
    private val duckPlayerFeature: DuckPlayerFeature,
    private val pixel: Pixel,
    private val duckPlayerLocalFilesPath: DuckPlayerLocalFilesPath,
    private val mimeTypeMap: MimeTypeMap,
    private val dispatchers: DispatcherProvider,
) : DuckPlayerInternal {

    private var shouldForceYTNavigation = false
    private var shouldHideOverlay = false
    private val isFeatureEnabled: Boolean by lazy {
        duckPlayerFeature.self().isEnabled() && duckPlayerFeature.enableDuckPlayer().isEnabled()
    }

    private lateinit var duckPlayerDisabledHelpLink: String

    override suspend fun getDuckPlayerState(): DuckPlayerState {
        if (!::duckPlayerDisabledHelpLink.isInitialized) {
            duckPlayerDisabledHelpLink = duckPlayerFeatureRepository.getDuckPlayerDisabledHelpPageLink() ?: ""
        }
        return if (isFeatureEnabled) {
            ENABLED
        } else if (duckPlayerDisabledHelpLink.isNotBlank()) {
            DISABLED_WIH_HELP_LINK
        } else {
            DISABLED
        }
    }

    override suspend fun setUserPreferences(
        overlayInteracted: Boolean,
        privatePlayerMode: String,
    ) {
        val playerMode = when {
            privatePlayerMode.contains("disabled") -> Disabled
            privatePlayerMode.contains("enabled") -> Enabled
            else -> AlwaysAsk
        }
        duckPlayerFeatureRepository.setUserPreferences(UserPreferences(overlayInteracted, playerMode))
    }

    override suspend fun getUserPreferences(): UserPreferences {
        return duckPlayerFeatureRepository.getUserPreferences().let {
            UserPreferences(it.overlayInteracted, it.privatePlayerMode)
        }
    }

    override fun shouldHideDuckPlayerOverlay(): Boolean {
        return shouldHideOverlay
    }

    override fun duckPlayerOverlayHidden() {
        shouldHideOverlay = false
    }

    private suspend fun shouldNavigateToDuckPlayer(): Boolean {
        if (!isFeatureEnabled) return false
        val result = getUserPreferences().privatePlayerMode == Enabled && !shouldForceYTNavigation
        return result
    }

    override fun duckPlayerNavigatedToYoutube() {
        shouldForceYTNavigation = false
    }

    override fun observeUserPreferences(): Flow<UserPreferences> {
        return duckPlayerFeatureRepository.observeUserPreferences().map {
            UserPreferences(it.overlayInteracted, it.privatePlayerMode)
        }
    }

    override suspend fun sendDuckPlayerPixel(
        pixelName: String,
        pixelData: Map<String, String>,
    ) {
        if (!isFeatureEnabled) return
        val duckPlayerPixelName = when (pixelName) {
            "overlay" -> DUCK_PLAYER_OVERLAY_YOUTUBE_IMPRESSIONS
            "play.use" -> DUCK_PLAYER_VIEW_FROM_YOUTUBE_MAIN_OVERLAY
            "play.do_not_use" -> DUCK_PLAYER_OVERLAY_YOUTUBE_WATCH_HERE
            else -> { null }
        }

        duckPlayerPixelName?.let {
            pixel.fire(duckPlayerPixelName, parameters = pixelData)
            if (duckPlayerPixelName == DUCK_PLAYER_OVERLAY_YOUTUBE_IMPRESSIONS) {
                duckPlayerFeatureRepository.setUserOnboarded()
            }
        }
    }

    private suspend fun createYoutubeNoCookieFromDuckPlayer(uri: Uri): String? {
        if (!isFeatureEnabled) return null
        val embedUrl = duckPlayerFeatureRepository.getYouTubeEmbedUrl()
        uri.pathSegments?.firstOrNull()?.let { videoID ->
            return "$https://www.$embedUrl?$DUCK_PLAYER_VIDEO_ID_QUERY_PARAM=$videoID"
        }
        return null
    }

    override suspend fun createYoutubeWatchUrlFromDuckPlayer(uri: Uri): String? {
        val videoIdQueryParam = duckPlayerFeatureRepository.getVideoIDQueryParam()
        val youTubeWatchPath = duckPlayerFeatureRepository.getYouTubeWatchPath()
        val youTubeHost = duckPlayerFeatureRepository.getYouTubeUrl()
        uri.getQueryParameter(videoIdQueryParam)?.let { videoID ->
            return "$https://$youTubeHost/$youTubeWatchPath?$videoIdQueryParam=$videoID"
        } ?: uri.pathSegments.firstOrNull { it != youTubeWatchPath }?.let { videoID ->
            return "$https://$youTubeHost/$youTubeWatchPath?$videoIdQueryParam=$videoID"
        }
        return null
    }

    private suspend fun youTubeRequestedFromDuckPlayer() {
        shouldForceYTNavigation = true
        if (getUserPreferences().privatePlayerMode == AlwaysAsk) {
            shouldHideOverlay = true
        }
        if (isFeatureEnabled &&
            getUserPreferences().privatePlayerMode != Disabled
        ) {
            pixel.fire(DUCK_PLAYER_WATCH_ON_YOUTUBE)
        }
    }
    private fun isDuckPlayerUri(uri: Uri): Boolean {
        if (uri.normalizeScheme()?.scheme != duck) return false
        if (uri.userInfo != null) return false
        uri.host?.let { host ->
            if (!host.contains(DUCK_PLAYER_DOMAIN)) return false
            return !host.contains("!")
        }
        return false
    }

    override fun isDuckPlayerUri(uri: String): Boolean {
        return isDuckPlayerUri(uri.toUri())
    }

    override suspend fun isSimulatedYoutubeNoCookie(uri: Uri): Boolean {
        val validPaths = duckPlayerLocalFilesPath.assetsPath
        val embedUrl = duckPlayerFeatureRepository.getYouTubeEmbedUrl()
        return (
            uri.host?.removePrefix("www.") ==
                embedUrl && (
                uri.pathSegments.firstOrNull() == null ||
                    validPaths.any { uri.path?.contains(it) == true } ||
                    (uri.pathSegments.firstOrNull() != "embed" && uri.getQueryParameter(DUCK_PLAYER_VIDEO_ID_QUERY_PARAM) != null)
                )
            )
    }

    override suspend fun isSimulatedYoutubeNoCookie(uri: String): Boolean {
        return isSimulatedYoutubeNoCookie(uri.toUri())
    }

    private fun getDuckPlayerAssetsPath(url: Uri): String? {
        return url.path?.takeIf { it.isNotBlank() }?.removePrefix("/")?.let { "$DUCK_PLAYER_ASSETS_PATH$it" }
    }

    override suspend fun isYoutubeWatchUrl(uri: Uri): Boolean {
        val youTubeWatchPath = duckPlayerFeatureRepository.getYouTubeWatchPath()
        return isYouTubeUrl(uri) && uri.pathSegments.firstOrNull() == youTubeWatchPath
    }

    override fun isYouTubeUrl(uri: Uri): Boolean {
        val host = uri.host?.removePrefix("www.")
        return host == YOUTUBE_HOST || host == YOUTUBE_MOBILE_HOST
    }

    override suspend fun createDuckPlayerUriFromYoutubeNoCookie(uri: Uri): String? {
        if (!isFeatureEnabled) return null
        return uri.getQueryParameter(DUCK_PLAYER_VIDEO_ID_QUERY_PARAM)?.let {
            "$DUCK_PLAYER_URL_BASE$it"
        }
    }

    private suspend fun createDuckPlayerUriFromYoutube(uri: Uri): String {
        val videoIdQueryParam = duckPlayerFeatureRepository.getVideoIDQueryParam()
        val origin = uri.getQueryParameter(ORIGIN_QUERY_PARAM)?.let { it } ?: ORIGIN_QUERY_PARAM_AUTO
        return "$DUCK_PLAYER_URL_BASE${uri.getQueryParameter(videoIdQueryParam)}?$ORIGIN_QUERY_PARAM=$origin"
    }

    override suspend fun intercept(
        request: WebResourceRequest,
        url: Uri,
        webView: WebView,
    ): WebResourceResponse? {
        if (isDuckPlayerUri(url)) {
            return processDuckPlayerUri(url, webView)
        } else {
            if (!isFeatureEnabled) return null
            if (isYoutubeWatchUrl(url)) {
                return processYouTubeWatchUri(request, url, webView)
            } else if (isSimulatedYoutubeNoCookie(url)) {
                return processSimulatedYouTubeNoCookieUri(url, webView)
            }
        }
        return null
    }
    private suspend fun processSimulatedYouTubeNoCookieUri(
        url: Uri,
        webView: WebView,
    ): WebResourceResponse {
        val path = getDuckPlayerAssetsPath(url)
        val mimeType = mimeTypeMap.getMimeTypeFromExtension(path?.substringAfterLast("."))

        if (path != null && mimeType != null) {
            try {
                val inputStream: InputStream = webView.context.assets.open(path)
                return WebResourceResponse(mimeType, "UTF-8", inputStream)
            } catch (e: Exception) {
                return WebResourceResponse(null, null, null)
            }
        } else {
            val inputStream: InputStream = webView.context.assets.open(DUCK_PLAYER_ASSETS_INDEX_PATH)
            return WebResourceResponse("text/html", "UTF-8", inputStream).also {
                when (getUserPreferences().privatePlayerMode) {
                    Enabled -> "always"
                    AlwaysAsk -> "default"
                    else -> null
                }?.let { setting ->
                    pixel.fire(DUCK_PLAYER_DAILY_UNIQUE_VIEW, type = Daily(), parameters = mapOf("setting" to setting))
                }
            }
        }
    }

    private suspend fun processYouTubeWatchUri(
        request: WebResourceRequest,
        url: Uri,
        webView: WebView,
    ): WebResourceResponse? {
        val referer = request.requestHeaders.keys.firstOrNull { it in duckPlayerFeatureRepository.getYouTubeReferrerHeaders() }
            ?.let { url.getQueryParameter(it) }
        val previousUrl = duckPlayerFeatureRepository.getYouTubeReferrerQueryParams()
            .firstOrNull { url.getQueryParameter(it) != null }
            ?.let { url.getQueryParameter(it) }
        val currentUrl = withContext(dispatchers.main()) { webView.url }

        val videoIdQueryParam = duckPlayerFeatureRepository.getVideoIDQueryParam()
        val requestedVideoId = url.getQueryParameter(videoIdQueryParam)

        val isSimulated: suspend (String?) -> Boolean = { uri ->
            uri?.let { isSimulatedYoutubeNoCookie(it.toUri()) } == true
        }

        val isMatchingVideoId: (String?) -> Boolean = { uri ->
            uri?.toUri()?.getQueryParameter(DUCK_PLAYER_VIDEO_ID_QUERY_PARAM) == requestedVideoId
        }

        if (isSimulated(referer) && isMatchingVideoId(referer) ||
            isSimulated(previousUrl) && isMatchingVideoId(previousUrl)
        ) {
            withContext(dispatchers.main()) {
                webView.loadUrl("$DUCK_PLAYER_URL_BASE$DUCK_PLAYER_OPEN_IN_YOUTUBE_PATH?$videoIdQueryParam=$requestedVideoId")
            }
            return WebResourceResponse(null, null, null)
        } else if (isSimulated(currentUrl) && isMatchingVideoId(currentUrl)) {
            return null
        } else if (shouldNavigateToDuckPlayer()) {
            withContext(dispatchers.main()) {
                webView.loadUrl(createDuckPlayerUriFromYoutube(url))
            }
            return WebResourceResponse(null, null, null)
        }
        return null
    }

    private suspend fun doesYoutubeUrlComeFromDuckPlayer(url: Uri, request: WebResourceRequest? = null): Boolean {
        val referer = request?.requestHeaders?.keys?.firstOrNull { it in duckPlayerFeatureRepository.getYouTubeReferrerHeaders() }
            ?.let { url.getQueryParameter(it) }
        val previousUrl = duckPlayerFeatureRepository.getYouTubeReferrerQueryParams()
            .firstOrNull { url.getQueryParameter(it) != null }
            ?.let { url.getQueryParameter(it) }

        val videoIdQueryParam = duckPlayerFeatureRepository.getVideoIDQueryParam()
        val requestedVideoId = url.getQueryParameter(videoIdQueryParam)

        val isSimulated: suspend (String?) -> Boolean = { uri ->
            uri?.let { isSimulatedYoutubeNoCookie(it.toUri()) } == true
        }

        val isMatchingVideoId: (String?) -> Boolean = { uri ->
            uri?.toUri()?.getQueryParameter(DUCK_PLAYER_VIDEO_ID_QUERY_PARAM) == requestedVideoId
        }

        return isSimulated(referer) && isMatchingVideoId(referer) ||
            isSimulated(previousUrl) && isMatchingVideoId(previousUrl)
    }

    private suspend fun processDuckPlayerUri(
        url: Uri,
        webView: WebView,
    ): WebResourceResponse {
        if (url.pathSegments?.firstOrNull()?.equals(DUCK_PLAYER_OPEN_IN_YOUTUBE_PATH, ignoreCase = true) == true ||
            !isFeatureEnabled ||
            getUserPreferences().privatePlayerMode == Disabled
        ) {
            createYoutubeWatchUrlFromDuckPlayer(url)?.let { youtubeUrl ->
                youTubeRequestedFromDuckPlayer()
                withContext(dispatchers.main()) {
                    webView.loadUrl(youtubeUrl)
                }
            }
        } else {
            createYoutubeNoCookieFromDuckPlayer(url)?.let { youtubeUrl ->
                withContext(dispatchers.main()) {
                    webView.loadUrl(youtubeUrl)
                }
                val origin = url.getQueryParameter(ORIGIN_QUERY_PARAM)
                if (origin == ORIGIN_QUERY_PARAM_SERP || origin == ORIGIN_QUERY_PARAM_SERP_AUTO) {
                    pixel.fire(DUCK_PLAYER_VIEW_FROM_SERP)
                } else if (origin == ORIGIN_QUERY_PARAM_AUTO) {
                    pixel.fire(DUCK_PLAYER_VIEW_FROM_YOUTUBE_AUTOMATIC)
                } else if (origin != ORIGIN_QUERY_PARAM_OVERLAY) {
                    pixel.fire(DUCK_PLAYER_VIEW_FROM_OTHER)
                }
            }
        }
        return WebResourceResponse(null, null, null)
    }

    override fun showDuckPlayerPrimeModal(configuration: Configuration, fragmentManager: FragmentManager, fromDuckPlayerPage: Boolean) {
        if (!isFeatureEnabled) return
        if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            DuckPlayerPrimeDialogFragment.newInstance(fromDuckPlayerPage).show(fragmentManager, null)
        } else {
            DuckPlayerPrimeBottomSheet.newInstance(fromDuckPlayerPage).show(fragmentManager, null)
        }
    }

    override suspend fun getYouTubeEmbedUrl(): String {
        return duckPlayerFeatureRepository.getYouTubeEmbedUrl()
    }

    override suspend fun willNavigateToDuckPlayer(
        destinationUrl: Uri,
    ): Boolean {
        return (
            isFeatureEnabled &&
                isYoutubeWatchUrl(destinationUrl) &&
                getUserPreferences().privatePlayerMode == Enabled &&
                !(shouldForceYTNavigation || doesYoutubeUrlComeFromDuckPlayer(destinationUrl))
            )
    }
}
