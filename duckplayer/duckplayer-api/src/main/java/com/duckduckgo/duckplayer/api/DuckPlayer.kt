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

import android.net.Uri
import com.duckduckgo.duckplayer.api.PrivatePlayerMode.AlwaysAsk
import com.duckduckgo.duckplayer.api.PrivatePlayerMode.Disabled
import com.duckduckgo.duckplayer.api.PrivatePlayerMode.Enabled

const val DUCK_PLAYER_ASSETS_PATH = "duckplayer/index.html"

/**
 * DuckPlayer interface provides a set of methods for interacting with the DuckPlayer.
 */
interface DuckPlayer {
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
     * Creates a YouTube no-cookie URI from a DuckPlayer URI.
     *
     * @param uri The DuckPlayer URI.
     * @return The YouTube no-cookie URI.
     */
    fun createYoutubeNoCookieFromDuckPlayer(uri: Uri): String?

    /**
     * Checks if a URI is a DuckPlayer URI.
     *
     * @param uri The URI to check.
     * @return True if the URI is a DuckPlayer URI, false otherwise.
     */
    fun isDuckPlayerUri(uri: Uri): Boolean

    /**
     * Checks if a string is a DuckPlayer URI.
     *
     * @param uri The string to check.
     * @return True if the string is a DuckPlayer URI, false otherwise.
     */
    fun isDuckPlayerUri(uri: String): Boolean

    /**
     * Checks if a URI is a YouTube no-cookie URI.
     *
     * @param uri The URI to check.
     * @return True if the URI is a YouTube no-cookie URI, false otherwise.
     */
    fun isYoutubeNoCookie(uri: Uri): Boolean

    /**
     * Checks if a string is a YouTube no-cookie URI.
     *
     * @param uri The string to check.
     * @return True if the string is a YouTube no-cookie URI, false otherwise.
     */
    fun isYoutubeNoCookie(uri: String): Boolean

    /**
     * Retrieves the duck player assets path from a URI.
     *
     * @param url The URI to retrieve the path from.
     * @return The path of the URI.
     */
    fun getDuckPlayerAssetsPath(url: Uri): String?

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
