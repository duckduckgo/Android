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
import com.duckduckgo.duckplayer.api.PrivatePlayerMode.AlwaysAsk
import com.duckduckgo.duckplayer.api.PrivatePlayerMode.Disabled
import com.duckduckgo.duckplayer.api.PrivatePlayerMode.Enabled
import com.duckduckgo.duckplayer.impl.ui.DuckPlayerPrimeBottomSheet
import com.duckduckgo.duckplayer.impl.ui.DuckPlayerPrimeDialogFragment
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import java.io.InputStream
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private const val YOUTUBE_HOST = "youtube.com"
private const val YOUTUBE_MOBILE_HOST = "m.youtube.com"
private const val DUCK_PLAYER_VIDEO_ID_QUERY_PARAM = "videoID"
private const val DUCK_PLAYER_OPEN_IN_YOUTUBE_PATH = "openInYoutube"
private const val DUCK_PLAYER_DOMAIN = "player"
private const val DUCK_PLAYER_URL_BASE = "$duck://$DUCK_PLAYER_DOMAIN/"
private const val DUCK_PLAYER_ASSETS_PATH = "duckplayer/"
private const val DUCK_PLAYER_ASSETS_INDEX_PATH = "${DUCK_PLAYER_ASSETS_PATH}index.html"

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealDuckPlayer @Inject constructor(
    private val duckPlayerFeatureRepository: DuckPlayerFeatureRepository,
    private val duckPlayerFeature: DuckPlayerFeature,
    private val pixel: Pixel,
    private val duckPlayerLocalFilesPath: DuckPlayerLocalFilesPath,
    private val mimeTypeMap: MimeTypeMap,
    private val dispatchers: DispatcherProvider,
) : DuckPlayer {

    private var shouldForceYTNavigation = false
    private var shouldHideOverlay = false

    override suspend fun getDuckPlayerState(): DuckPlayerState {
        val isFeatureEnabled = duckPlayerFeature.self().isEnabled()
        val helpLink = duckPlayerFeatureRepository.getDuckPlayerDisabledHelpPageLink()
        return if (isFeatureEnabled) {
            ENABLED
        } else if (helpLink?.isNotBlank() == true) {
            DISABLED_WIH_HELP_LINK
        } else {
            DISABLED
        }
    }

    override fun setUserPreferences(
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

    override fun sendDuckPlayerPixel(
        pixelName: String,
        pixelData: Map<String, String>,
    ) {
        val androidPixelName = "m_${pixelName.replace('.', '_')}"
        pixel.fire(androidPixelName, pixelData)
    }

    private suspend fun createYoutubeNoCookieFromDuckPlayer(uri: Uri): String? {
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
        } ?: uri.pathSegments.firstOrNull()?.let { videoID ->
            return "$https://$youTubeHost/$youTubeWatchPath?$videoIdQueryParam=$videoID"
        }
        return null
    }

    private suspend fun youTubeRequestedFromDuckPlayer() {
        shouldForceYTNavigation = true
        if (getUserPreferences().privatePlayerMode == AlwaysAsk) {
            shouldHideOverlay = true
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
        val host = uri.host?.removePrefix("www.")
        return (host == YOUTUBE_HOST || host == YOUTUBE_MOBILE_HOST) && uri.pathSegments.firstOrNull() == youTubeWatchPath
    }

    override suspend fun createDuckPlayerUriFromYoutubeNoCookie(uri: Uri): String {
        return "$DUCK_PLAYER_URL_BASE${uri.getQueryParameter(DUCK_PLAYER_VIDEO_ID_QUERY_PARAM)}"
    }

    private suspend fun createDuckPlayerUriFromYoutube(uri: Uri): String {
        val videoIdQueryParam = duckPlayerFeatureRepository.getVideoIDQueryParam()
        return "$DUCK_PLAYER_URL_BASE${uri.getQueryParameter(videoIdQueryParam)}"
    }

    override suspend fun intercept(
        request: WebResourceRequest,
        url: Uri,
        webView: WebView,
    ): WebResourceResponse? {
        if (getDuckPlayerState() != ENABLED) return null
        if (isDuckPlayerUri(url)) {
            return processDuckPlayerUri(url, webView)
        } else if (isYoutubeWatchUrl(url)) {
            return processYouTubeWatchUri(request, url, webView)
        } else if (isSimulatedYoutubeNoCookie(url)) {
            return processSimulatedYouTubeNoCookieUri(url, webView)
        }

        return null
    }
    private fun processSimulatedYouTubeNoCookieUri(
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
            return WebResourceResponse("text/html", "UTF-8", inputStream)
        }
    }

    private suspend fun processYouTubeWatchUri(
        request: WebResourceRequest,
        url: Uri,
        webView: WebView,
    ): WebResourceResponse? {
        val referer = duckPlayerFeatureRepository.getYouTubeReferrerHeaders()
            .firstOrNull { referrer -> request.requestHeaders[referrer] != null }
            ?.let { referrer -> url.getQueryParameter(referrer) }
        val previousUrl = duckPlayerFeatureRepository.getYouTubeReferrerQueryParams()
            .firstOrNull { referrer -> url.getQueryParameter(referrer) != null }
            ?.let { referrer -> url.getQueryParameter(referrer) }
        val videoIdQueryParam = duckPlayerFeatureRepository.getVideoIDQueryParam()
        if ((referer != null && isSimulatedYoutubeNoCookie(referer.toUri())) ||
            (previousUrl != null && isSimulatedYoutubeNoCookie(previousUrl))
        ) {
            withContext(dispatchers.main()) {
                url.getQueryParameter(videoIdQueryParam)?.let {
                    webView.loadUrl("$DUCK_PLAYER_URL_BASE$DUCK_PLAYER_OPEN_IN_YOUTUBE_PATH?$videoIdQueryParam=$it")
                }
            }
            return WebResourceResponse(null, null, null)
        } else if (shouldNavigateToDuckPlayer()) {
            withContext(dispatchers.main()) {
                webView.loadUrl(createDuckPlayerUriFromYoutube(url))
            }
            return WebResourceResponse(null, null, null)
        }
        return null
    }

    private suspend fun processDuckPlayerUri(
        url: Uri,
        webView: WebView,
    ): WebResourceResponse {
        if (url.pathSegments?.firstOrNull()?.equals(DUCK_PLAYER_OPEN_IN_YOUTUBE_PATH, ignoreCase = true) == true) {
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
            }
        }
        return WebResourceResponse(null, null, null)
    }

    override fun showDuckPlayerPrimeModal(configuration: Configuration, fragmentManager: FragmentManager) {
        if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            DuckPlayerPrimeDialogFragment.newInstance().show(fragmentManager, null)
        } else {
            DuckPlayerPrimeBottomSheet.newInstance().show(fragmentManager, null)
        }
    }
}
