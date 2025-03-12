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

package com.duckduckgo.duckplayer.api

import android.content.res.Configuration
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.fragment.app.FragmentManager
import com.duckduckgo.duckplayer.api.DuckPlayer.DuckPlayerOrigin.AUTO
import com.duckduckgo.duckplayer.api.DuckPlayer.DuckPlayerOrigin.OVERLAY
import com.duckduckgo.duckplayer.api.DuckPlayer.DuckPlayerOrigin.SERP
import com.duckduckgo.duckplayer.api.DuckPlayer.DuckPlayerOrigin.SERP_AUTO
import com.duckduckgo.duckplayer.api.PrivatePlayerMode.AlwaysAsk
import com.duckduckgo.duckplayer.api.PrivatePlayerMode.Disabled
import com.duckduckgo.duckplayer.api.PrivatePlayerMode.Enabled
import kotlinx.coroutines.flow.Flow

const val YOUTUBE_HOST = "youtube.com"
const val YOUTUBE_MOBILE_HOST = "m.youtube.com"
const val ORIGIN_QUERY_PARAM = "origin"
const val ORIGIN_QUERY_PARAM_SERP = "serp"

/**
 * DuckPlayer interface provides a set of methods for interacting with the DuckPlayer.
 */
interface DuckPlayer {

    /**
     * Retrieves the current state of the DuckPlayer.
     *
     * This method is used to check the current state of the DuckPlayer. The state can be one of the following:
     * - ENABLED: The DuckPlayer is enabled and can be used.
     * - DISABLED: The DuckPlayer is disabled and cannot be used.
     * - DISABLED_WIH_HELP_LINK: The DuckPlayer is disabled and cannot be used, but a help link is provided for troubleshooting.
     *
     * @return The current state of the DuckPlayer as a DuckPlayerState enum.
     */
    fun getDuckPlayerState(): DuckPlayerState

    /**
     * Sends a pixel with the given name and data.
     *
     * @param pixelName The name of the pixel.
     * @param pixelData The data associated with the pixel.
     */
    suspend fun sendDuckPlayerPixel(pixelName: String, pixelData: Map<String, String>)

    /**
     * Retrieves the user preferences.
     *
     * @return The user values.
     */
    fun getUserPreferences(): UserPreferences

    /**
     * Checks if the DuckPlayer overlay should be hidden after navigating back from Duck Player
     *
     * @return True if the overlay should be hidden, false otherwise.
     */
    fun shouldHideDuckPlayerOverlay(): Boolean

    /**
     * Notifies the DuckPlayer that the overlay was hidden after navigating back from Duck Player
     */
    fun duckPlayerOverlayHidden()

    /**
     * Notifies the DuckPlayer that the user navigated to YouTube successfully, so subsequent requests would redirect to Duck Player
     */
    fun duckPlayerNavigatedToYoutube()

    /**
     * Retrieves a flow of user preferences.
     *
     * @return The flow user preferences.
     */
    fun observeUserPreferences(): Flow<UserPreferences>

    /**
     * Sets the user preferences.
     *
     * @param overlayInteracted A boolean indicating whether the overlay was interacted with.
     * @param privatePlayerMode The mode of the private player.
     */
    suspend fun setUserPreferences(overlayInteracted: Boolean, privatePlayerMode: String)

    /**
     * Creates a DuckPlayer URI from a YouTube no-cookie URI.
     *
     * @param uri The YouTube no-cookie URI.
     * @return The DuckPlayer URI.
     */
    fun createDuckPlayerUriFromYoutubeNoCookie(uri: Uri): String?

    /**
     * Checks if a string is a DuckPlayer URI.
     *
     * @param uri The string to check.
     * @return True if the string is a DuckPlayer URI, false otherwise.
     */
    fun isDuckPlayerUri(uri: String): Boolean

    /**
     * Creates a YouTube URI from a DuckPlayer URI.
     * @param uri The DuckPlayer URI.
     * @return The YouTube URI.
     */
    fun createYoutubeWatchUrlFromDuckPlayer(uri: Uri): String?

    /**
     * Checks if a URI is a simulated YouTube no-cookie URI.
     *
     * @param uri The URI to check.
     * @return True if the URI is a YouTube no-cookie URI, false otherwise.
     */
    fun isSimulatedYoutubeNoCookie(uri: Uri): Boolean

    /**
     * Checks if a URI is a YouTube watch URL.
     *
     * @param uri The URI to check.
     * @return True if the URI is a YouTube no-cookie URI, false otherwise.
     */
    fun isYoutubeWatchUrl(uri: Uri): Boolean

    /**
     * Checks if a URI is a YouTube URL.
     *
     * @param uri The URI to check.
     * @return True if the URI is a YouTube no-cookie URI, false otherwise.
     */
    fun isYouTubeUrl(uri: Uri): Boolean

    /**
     * Notify Duck Player of a resource request and allow Duck Player to return the data.
     *
     * If the return value is null, it means Duck Player won't add any response data.
     * Otherwise, the return response and data will be used.
     */
    suspend fun intercept(
        request: WebResourceRequest,
        url: Uri,
        webView: WebView,
    ): WebResourceResponse?

    /**
     * Shows the Duck Player Prime modal.
     *
     * @param configuration The configuration of the device.
     * @param fragmentManager The fragment manager.
     */
    fun showDuckPlayerPrimeModal(configuration: Configuration, fragmentManager: FragmentManager, fromDuckPlayerPage: Boolean)

    /**
     * Checks whether a URL will trigger Duck Player loading based on URL and user settings
     *
     * @param destinationUrl The destination URL.
     * @return True if the URL should launch Duck Player, false otherwise.
     */
    fun willNavigateToDuckPlayer(
        destinationUrl: Uri,
    ): Boolean

    /**
     * Checks whether a duck Player will be opened in a new tab based on RC flag and user settings
     *
     * @return True if should open Duck Player in a new tab, false otherwise.
     */
    fun shouldOpenDuckPlayerInNewTab(): OpenDuckPlayerInNewTab

    /**
     * Observes whether a duck Player will be opened in a new tab based on RC flag and user settings
     *
     * @return Flow. True if should open Duck Player in a new tab, false otherwise.
     */
    fun observeShouldOpenInNewTab(): Flow<OpenDuckPlayerInNewTab>

    /**
     * Sets the DuckPlayer origin.
     *
     * @param origin The DuckPlayer origin. [SERP], [SERP_AUTO], [AUTO], or [OVERLAY]
     */
    fun setDuckPlayerOrigin(origin: DuckPlayerOrigin)

    /**
     * Returns `true` if Duck Player was ever used before.
     */
    suspend fun wasUsedBefore(): Boolean

    /**
     * Data class representing user preferences for Duck Player.
     *
     * @property overlayInteracted A boolean indicating whether the overlay was interacted with.
     * @property privatePlayerMode The mode of the private player. [Enabled], [AlwaysAsk], or [Disabled]
     */
    data class UserPreferences(
        val overlayInteracted: Boolean,
        val privatePlayerMode: PrivatePlayerMode,
    )

    enum class DuckPlayerState {
        ENABLED,
        DISABLED,
        DISABLED_WIH_HELP_LINK,
    }

    sealed interface OpenDuckPlayerInNewTab {
        data object On : OpenDuckPlayerInNewTab
        data object Off : OpenDuckPlayerInNewTab
        data object Unavailable : OpenDuckPlayerInNewTab
    }

    enum class DuckPlayerOrigin {
        SERP,
        SERP_AUTO,
        AUTO,
        OVERLAY,
    }
}
