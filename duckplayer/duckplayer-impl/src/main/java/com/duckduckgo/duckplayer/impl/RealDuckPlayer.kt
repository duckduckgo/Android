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

import android.net.Uri
import androidx.core.net.toUri
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.utils.UrlScheme
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckplayer.api.DuckPlayer
import com.duckduckgo.duckplayer.api.DuckPlayer.UserPreferences
import com.duckduckgo.duckplayer.api.PrivatePlayerMode.AlwaysAsk
import com.duckduckgo.duckplayer.api.PrivatePlayerMode.Disabled
import com.duckduckgo.duckplayer.api.PrivatePlayerMode.Enabled
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

private const val YOUTUBE_NO_COOKIE_HOST = "youtube-nocookie.com"

@ContributesBinding(AppScope::class)
class RealDuckPlayer @Inject constructor(
    private val duckPlayerFeatureRepository: DuckPlayerFeatureRepository,
    private val pixel: Pixel,
) : DuckPlayer {

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

    override fun sendDuckPlayerPixel(
        pixelName: String,
        pixelData: Map<String, String>,
    ) {
        val androidPixelName = "m_${pixelName.replace('.', '_')}"
        pixel.fire(androidPixelName, pixelData)
    }

    override fun createYoutubeNoCookieFromDuckPlayer(uri: Uri): String? {
        uri.pathSegments?.firstOrNull()?.let { videoID ->
            return "https://$YOUTUBE_NO_COOKIE_HOST?videoID=$videoID"
        }
        return null
    }

    override fun isDuckPlayerUri(uri: Uri): Boolean {
        if (uri.normalizeScheme()?.scheme != UrlScheme.duck) return false
        if (uri.userInfo != null) return false
        uri.host?.let { host ->
            if (!host.contains("player")) return false
            return !host.contains("!")
        }
        return false
    }

    override fun isDuckPlayerUri(uri: String): Boolean {
        return isDuckPlayerUri(uri.toUri())
    }

    override fun isYoutubeNoCookie(uri: Uri): Boolean {
        return uri.host == YOUTUBE_NO_COOKIE_HOST
    }

    override fun isYoutubeNoCookie(uri: String): Boolean {
        return isYoutubeNoCookie(uri.toUri())
    }

    override fun getDuckPlayerAssetsPath(url: Uri): String? {
        return url.path?.takeIf { it.isNotBlank() }?.removePrefix("/")?.let { "duckplayer/$it" }
    }

    override fun createDuckPlayerUriFromYoutubeNoCookie(uri: Uri): String {
        return "${UrlScheme.duck}://player/${uri.getQueryParameter("videoID")}"
    }
}
