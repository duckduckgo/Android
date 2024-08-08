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
import com.duckduckgo.duckplayer.api.PrivatePlayerMode.AlwaysAsk
import com.duckduckgo.duckplayer.api.PrivatePlayerMode.Disabled
import com.duckduckgo.duckplayer.api.PrivatePlayerMode.Enabled
import kotlinx.coroutines.flow.Flow

/**
 * DuckPlayer interface provides a set of methods for interacting with the DuckPlayer.
 */
interface DuckPlayer {

    /**
     * Checks if the DuckPlayer is available through remote config
     *
     * @return True if the DuckPlayer is available, false otherwise.
     */
    fun isDuckPlayerAvailable(): Boolean

    /**
     * Sends a pixel with the given name and data.
     *
     * @param pixelName The name of the pixel.
     * @param pixelData The data associated with the pixel.
     */
    fun sendDuckPlayerPixel(pixelName: String, pixelData: Map<String, String>)

    /**
     * Retrieves the user preferences.
     *
     * @return The user values.
     */
    suspend fun getUserPreferences(): UserPreferences

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
    fun setUserPreferences(overlayInteracted: Boolean, privatePlayerMode: String)

    /**
     * Creates a DuckPlayer URI from a YouTube no-cookie URI.
     *
     * @param uri The YouTube no-cookie URI.
     * @return The DuckPlayer URI.
     */
    fun createDuckPlayerUriFromYoutubeNoCookie(uri: Uri): String

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
     * Checks if a URI is a simulated YouTube no-cookie URI.
     *
     * @param uri The URI to check.
     * @return True if the URI is a YouTube no-cookie URI, false otherwise.
     */
    fun isYoutubeWatchUrl(uri: Uri): Boolean

    /**
     * Checks if a string is a YouTube no-cookie URI.
     *
     * @param uri The string to check.
     * @return True if the string is a YouTube no-cookie URI, false otherwise.
     */
    fun isSimulatedYoutubeNoCookie(uri: String): Boolean

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
    fun showDuckPlayerPrimeModal(configuration: Configuration, fragmentManager: FragmentManager)

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
}
