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

const val DUCK_PLAYER_ASSETS_PATH = "duckplayer/index.html"

interface DuckPlayer {
    fun sendDuckPlayerPixel(pixelName: String, pixelData: Map<String, String>)

    suspend fun getUserValues(): UserValues

    fun setUserValues(overlayInteracted: Boolean, privatePlayerMode: String)

    fun createDuckPlayerUriFromYoutubeNoCookie(uri: Uri): String

    fun createYoutubeNoCookieFromDuckPlayer(uri: Uri?): String?

    fun isDuckPlayerUri(uri: Uri): Boolean

    fun isDuckPlayerUri(uri: String): Boolean

    fun isYoutubeNoCookie(uri: Uri): Boolean

    fun isYoutubeNoCookie(uri: String): Boolean
    fun getPath(url: Uri?): String?

    data class UserValues(
        val overlayInteracted: Boolean,
        val privatePlayerMode: String,
    )
}
