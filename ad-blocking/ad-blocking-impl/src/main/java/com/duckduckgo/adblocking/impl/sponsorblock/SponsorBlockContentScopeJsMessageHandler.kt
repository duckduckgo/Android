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
import com.duckduckgo.js.messaging.api.SubscriptionEventData
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class)
class SponsorBlockContentScopeJsMessageHandler @Inject constructor(
    private val sponsorBlock: SponsorBlock,
    private val duckPlayerFeatureRepository: DuckPlayerFeatureRepository,
    private val dispatcherProvider: DispatcherProvider,
    @AppCoroutineScope private val appScope: CoroutineScope,
) : ContentScopeJsMessageHandlersPlugin {

    override fun getJsMessageHandler(): JsMessageHandler = object : JsMessageHandler {
        override fun process(jsMessage: JsMessage, jsMessaging: JsMessaging, jsMessageCallback: JsMessageCallback?) {
            if (jsMessage.method == METHOD_GET_CONFIG) subscribeToConfigChanges(jsMessaging)
            val id = jsMessage.id ?: return
            appScope.launch(dispatcherProvider.io()) {
                val result = when (jsMessage.method) {
                    METHOD_GET_SEGMENTS -> getSegments(jsMessage.params.optString(PARAM_VIDEO_ID))
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

    // Pages that asked for the config, so they can be told when it changes. Weak, because nothing tells us when a tab goes away.
    private val configSubscribers: MutableSet<JsMessaging> = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap()))
    private val observingConfig = AtomicBoolean(false)

    private fun subscribeToConfigChanges(jsMessaging: JsMessaging) {
        configSubscribers.add(jsMessaging)
        if (!observingConfig.compareAndSet(false, true)) return
        appScope.launch(dispatcherProvider.io()) {
            // The first emission is the config pages already fetched with getConfig.
            sponsorBlock.observeConfig().drop(1).collect { config ->
                val event = SubscriptionEventData(FEATURE_NAME, SUBSCRIPTION_CONFIG_CHANGED, config.toJson())
                synchronized(configSubscribers) { configSubscribers.toList() }.forEach { it.sendSubscriptionEvent(event) }
            }
        }
    }

    private val moshi: Moshi by lazy { Moshi.Builder().add(KotlinJsonAdapterFactory()).build() }
    private val segmentsAdapter: JsonAdapter<SegmentsResponse> by lazy { moshi.adapter(SegmentsResponse::class.java) }
    private val configAdapter: JsonAdapter<ConfigResponse> by lazy { moshi.adapter(ConfigResponse::class.java) }

    private suspend fun getSegments(videoId: String): JSONObject =
        JSONObject(segmentsAdapter.toJson(SegmentsResponse(sponsorBlock.getSegments(videoId))))

    private fun getConfig(): JSONObject = sponsorBlock.config().toJson()

    private fun SponsorBlockConfig.toJson(): JSONObject {
        val mode = when (skipMode) {
            SponsorBlockSkipMode.MANUAL -> SKIP_MODE_MANUAL
        }
        return JSONObject(configAdapter.toJson(ConfigResponse(enabled = enabled, skipMode = mode, version = CONTRACT_VERSION)))
    }

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
        private const val SUBSCRIPTION_CONFIG_CHANGED = "onConfigChanged"
        private const val PARAM_VIDEO_ID = "videoID"
        private const val SKIP_MODE_MANUAL = "manual"
        private const val CONTRACT_VERSION = 1
    }
}
