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

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckplayer.api.DuckPlayer.UserPreferences
import com.duckduckgo.duckplayer.api.PrivatePlayerMode.AlwaysAsk
import com.duckduckgo.duckplayer.api.PrivatePlayerMode.Disabled
import com.duckduckgo.duckplayer.api.PrivatePlayerMode.Enabled
import com.squareup.anvil.annotations.ContributesBinding
import dagger.Lazy
import dagger.SingleInstanceIn
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.logcat
import okhttp3.Headers.Companion.toHeaders
import okhttp3.OkHttpClient
import okhttp3.Request

interface DuckPlayerFeatureRepository {
    fun getDuckPlayerRemoteConfigJson(): String

    fun setDuckPlayerRemoteConfigJson(jsonString: String)

    fun getUserPreferences(): UserPreferences

    fun observeUserPreferences(): Flow<UserPreferences>

    suspend fun setUserPreferences(userPreferences: UserPreferences)

    suspend fun storeDuckPlayerDisabledHelpPageLink(duckPlayerDisabledHelpPageLink: String?)

    fun getDuckPlayerDisabledHelpPageLink(): String?

    suspend fun storeYouTubePath(youtubePath: String)

    suspend fun storeYoutubeEmbedUrl(embedUrl: String)

    suspend fun storeYouTubeUrl(youtubeUrl: String)

    suspend fun storeYouTubeReferrerHeaders(youtubeReferrerHeaders: List<String>)

    suspend fun storeYouTubeReferrerQueryParams(youtubeReferrerQueryParams: List<String>)

    suspend fun storeYouTubeVideoIDQueryParam(youtubeVideoIDQueryParam: String)

    fun getVideoIDQueryParam(): String
    fun getYouTubeReferrerQueryParams(): List<String>
    fun getYouTubeReferrerHeaders(): List<String>
    fun getYouTubeWatchPath(): String
    fun getYouTubeUrl(): String
    fun getYouTubeEmbedUrl(): String
    fun isOnboarded(): Boolean
    suspend fun setUserOnboarded()
    fun setOpenInNewTab(enabled: Boolean)
    fun observeOpenInNewTab(): Flow<Boolean>
    fun shouldOpenInNewTab(): Boolean

    suspend fun wasUsedBefore(): Boolean
    suspend fun setUsed()

    suspend fun requestEmbed(
        url: String,
        headers: Map<String, String>,
    ): InputStream?
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealDuckPlayerFeatureRepository @Inject constructor(
    private val duckPlayerDataStore: DuckPlayerDataStore,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    @Named("api") private val okHttpClient: Lazy<OkHttpClient>,
) : DuckPlayerFeatureRepository {

    override fun getDuckPlayerRemoteConfigJson(): String {
        return duckPlayerDataStore.getDuckPlayerRemoteConfigJson()
    }

    override fun setDuckPlayerRemoteConfigJson(jsonString: String) {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            duckPlayerDataStore.setDuckPlayerRemoteConfigJson(jsonString)
        }
    }

    override suspend fun setUserPreferences(
        userPreferences: UserPreferences,
    ) {
        withContext(dispatcherProvider.io()) {
            duckPlayerDataStore.setOverlayInteracted(userPreferences.overlayInteracted)
            duckPlayerDataStore.setPrivatePlayerMode(userPreferences.privatePlayerMode.value)
        }
    }

    override fun observeUserPreferences(): Flow<UserPreferences> {
        return duckPlayerDataStore.observePrivatePlayerMode()
            .combine(duckPlayerDataStore.observeOverlayInteracted()) { privatePlayerMode, overlayInteracted ->
                UserPreferences(
                    overlayInteracted = overlayInteracted,
                    privatePlayerMode = when (privatePlayerMode) {
                        Enabled.value -> Enabled
                        Disabled.value -> Disabled
                        else -> AlwaysAsk
                    },
                )
            }
    }

    override fun getUserPreferences(): UserPreferences {
        return UserPreferences(
            overlayInteracted = duckPlayerDataStore.getOverlayInteracted(),
            privatePlayerMode = when (duckPlayerDataStore.getPrivatePlayerMode()) {
                Enabled.value -> Enabled
                Disabled.value -> Disabled
                else -> AlwaysAsk
            },
        )
    }

    override suspend fun storeDuckPlayerDisabledHelpPageLink(duckPlayerDisabledHelpPageLink: String?) {
        duckPlayerDataStore.storeDuckPlayerDisabledHelpPageLink(duckPlayerDisabledHelpPageLink)
    }

    override fun getDuckPlayerDisabledHelpPageLink(): String? {
        return duckPlayerDataStore.getDuckPlayerDisabledHelpPageLink()
    }

    override suspend fun storeYouTubePath(youtubePath: String) {
        duckPlayerDataStore.storeYouTubeWatchPath(youtubePath)
    }

    override suspend fun storeYouTubeReferrerHeaders(youtubeReferrerHeaders: List<String>) {
        duckPlayerDataStore.storeYouTubeReferrerHeaders(youtubeReferrerHeaders)
    }

    override suspend fun storeYouTubeReferrerQueryParams(youtubeReferrerQueryParams: List<String>) {
        duckPlayerDataStore.storeYouTubeReferrerQueryParams(youtubeReferrerQueryParams)
    }

    override suspend fun storeYouTubeUrl(youtubeUrl: String) {
        duckPlayerDataStore.storeYouTubeUrl(youtubeUrl)
    }

    override suspend fun storeYouTubeVideoIDQueryParam(youtubeVideoIDQueryParam: String) {
        duckPlayerDataStore.storeYouTubeVideoIDQueryParam(youtubeVideoIDQueryParam)
    }

    override suspend fun storeYoutubeEmbedUrl(embedUrl: String) {
        duckPlayerDataStore.storeYoutubeEmbedUrl(embedUrl)
    }

    override fun getVideoIDQueryParam(): String {
        return duckPlayerDataStore.getYouTubeVideoIDQueryParam()
    }

    override fun getYouTubeReferrerQueryParams(): List<String> {
        return duckPlayerDataStore.getYouTubeReferrerQueryParams()
    }

    override fun getYouTubeReferrerHeaders(): List<String> {
        return duckPlayerDataStore.getYouTubeReferrerHeaders()
    }

    override fun getYouTubeWatchPath(): String {
        return duckPlayerDataStore.getYouTubeWatchPath()
    }

    override fun getYouTubeUrl(): String {
        return duckPlayerDataStore.getYouTubeUrl()
    }

    override fun getYouTubeEmbedUrl(): String {
        return duckPlayerDataStore.getYoutubeEmbedUrl()
    }

    override fun isOnboarded(): Boolean {
        return duckPlayerDataStore.getUserOnboarded()
    }

    override suspend fun setUserOnboarded() {
        duckPlayerDataStore.setUserOnboarded()
    }

    override fun setOpenInNewTab(enabled: Boolean) {
        appCoroutineScope.launch {
            duckPlayerDataStore.setOpenInNewTab(enabled)
        }
    }

    override fun observeOpenInNewTab(): Flow<Boolean> {
        return duckPlayerDataStore.observeOpenInNewTab()
    }

    override fun shouldOpenInNewTab(): Boolean {
        return duckPlayerDataStore.getOpenInNewTab()
    }

    override suspend fun wasUsedBefore(): Boolean {
        return duckPlayerDataStore.wasUsedBefore()
    }

    override suspend fun setUsed() {
        duckPlayerDataStore.setUsed()
    }

    override suspend fun requestEmbed(
        url: String,
        headers: Map<String, String>,
    ): InputStream? {
        return try {
            val okHttpRequest = Request.Builder().url(url).headers(headers.toHeaders()).build()
            withContext(dispatcherProvider.io()) { okHttpClient.get().newCall(okHttpRequest).execute().body?.byteStream() }
        } catch (e: IOException) {
            logcat { "Request failed: ${e.message}" }
            null
        }
    }
}
