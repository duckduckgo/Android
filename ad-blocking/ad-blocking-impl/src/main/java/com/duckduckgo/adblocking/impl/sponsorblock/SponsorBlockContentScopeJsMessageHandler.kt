/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.adblocking.impl.sponsorblock

import com.duckduckgo.adblocking.api.duckplayer.YOUTUBE_HOST
import com.duckduckgo.adblocking.api.duckplayer.YOUTUBE_MOBILE_HOST
import com.duckduckgo.adblocking.impl.duckplayer.DuckPlayerFeatureRepository
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.contentscopescripts.api.ContentScopeJsMessageHandlersPlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.js.messaging.api.JsCallbackData
import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.duckduckgo.js.messaging.api.JsMessageHandler
import com.duckduckgo.js.messaging.api.JsMessaging
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dev.zacsweers.metro.ContributesIntoSet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.LogPriority.WARN
import logcat.logcat
import org.json.JSONObject
import javax.inject.Inject

@ContributesIntoSet(AppScope::class)
class SponsorBlockContentScopeJsMessageHandler @Inject constructor(
    private val sponsorBlockFeature: SponsorBlockFeature,
    private val segmentsProvider: SponsorBlockSegmentsProvider,
    private val duckPlayerFeatureRepository: DuckPlayerFeatureRepository,
    private val dispatcherProvider: DispatcherProvider,
    @AppCoroutineScope private val appScope: CoroutineScope,
) : ContentScopeJsMessageHandlersPlugin {

    override fun getJsMessageHandler(): JsMessageHandler = object : JsMessageHandler {
        override fun process(jsMessage: JsMessage, jsMessaging: JsMessaging, jsMessageCallback: JsMessageCallback?) {
            val id = jsMessage.id ?: return
            appScope.launch(dispatcherProvider.io()) {
                val result = when (jsMessage.method) {
                    METHOD_GET_SEGMENTS -> getSegments(jsMessage.params)
                    METHOD_GET_CONFIG -> getConfig()
                    else -> return@launch
                }
                jsMessaging.onResponse(
                    JsCallbackData(params = result, featureName = jsMessage.featureName, method = jsMessage.method, id = id),
                )
            }
        }

        // The messaging layer matches these against the main frame's host, so YouTube embeds on other sites never get an answer.
        // It can't tell a Duck Player page from any other youtube-nocookie.com main frame, because handlers only see the host.
        override val allowedDomains: List<String>
            get() = listOf(YOUTUBE_HOST, YOUTUBE_MOBILE_HOST, duckPlayerFeatureRepository.getYouTubeEmbedUrl())

        override val featureName: String = FEATURE_NAME
        override val methods: List<String> = listOf(METHOD_GET_SEGMENTS, METHOD_GET_CONFIG)
    }

    private val moshi: Moshi by lazy { Moshi.Builder().add(KotlinJsonAdapterFactory()).build() }
    private val segmentsAdapter: JsonAdapter<SegmentsResponse> by lazy { moshi.adapter(SegmentsResponse::class.java) }
    private val configAdapter: JsonAdapter<ConfigResponse> by lazy { moshi.adapter(ConfigResponse::class.java) }

    private suspend fun getSegments(params: JSONObject): JSONObject {
        val videoId = params.optString(PARAM_VIDEO_ID).takeIf { VIDEO_ID_REGEX.matches(it) }
        val segments = if (videoId == null || !isEnabled()) {
            emptyList()
        } else {
            try {
                segmentsProvider.getSegments(videoId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(WARN) { "SponsorBlock: segments lookup failed: ${e.javaClass.simpleName}" }
                emptyList()
            }
        }
        return JSONObject(segmentsAdapter.toJson(SegmentsResponse(segments.sortedBy { it.start })))
    }

    // TODO: send the user's SponsorBlock settings (on/off, skip mode) once they exist; skipMode is fixed to manual for now.
    private fun getConfig(): JSONObject =
        JSONObject(configAdapter.toJson(ConfigResponse(enabled = isEnabled(), skipMode = SKIP_MODE_MANUAL, version = CONTRACT_VERSION)))

    private fun isEnabled(): Boolean = sponsorBlockFeature.mvpEnabled().isEnabled()

    private data class SegmentsResponse(
        val segments: List<SponsorBlockSegment>,
    )

    private data class ConfigResponse(
        val enabled: Boolean,
        val skipMode: String,
        val version: Int,
    )

    companion object {
        private const val FEATURE_NAME = "sponsorBlock"
        private const val METHOD_GET_SEGMENTS = "getSegments"
        private const val METHOD_GET_CONFIG = "getConfig"
        private const val PARAM_VIDEO_ID = "videoID"
        private const val SKIP_MODE_MANUAL = "manual"
        private const val CONTRACT_VERSION = 1
        private val VIDEO_ID_REGEX = Regex("^[A-Za-z0-9_-]{11}$")
    }
}
