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

package com.duckduckgo.adblocking.impl.pixels

import com.duckduckgo.adblocking.impl.domain.AdBlockingState
import com.duckduckgo.adblocking.impl.domain.AdBlockingStatusChecker
import com.duckduckgo.common.utils.plugins.pixel.PixelInterceptorPlugin
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import logcat.LogPriority.INFO
import logcat.logcat
import okhttp3.Interceptor
import okhttp3.Interceptor.Chain
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import javax.inject.Inject

/**
 * Gates YouTube ad-blocking telemetry pixels behind explicit user consent.
 *
 * The EventHub pipeline fires `webTelemetry_youTube*` pixels for every user for whom the feature is
 * remotely active, based on detection events sent by Content-Scope-Scripts.
 * This interceptor only sends pixels for users who have explicitly opted in
 * ([AdBlockingState.Enabled.UserEnabled]) — not for users enabled by the rollout
 * default, nor users with the feature off.
 *
 * TODO: This native-side gate is a temporary workaround. Remove this interceptor once C-S-S honours
 *  WebInterferenceDetectionContentScopeConfigPlugin.preferences() and gates the detection events at
 *  the source. See TD task https://app.asana.com/1/137249556945/project/481882893211075/task/1215138970525763?focus=true.
 */
@ContributesMultibinding(
    scope = AppScope::class,
    boundType = PixelInterceptorPlugin::class,
)
class AdBlockingTelemetryPixelInterceptor @Inject constructor(
    private val statusChecker: AdBlockingStatusChecker,
) : Interceptor, PixelInterceptorPlugin {

    override fun intercept(chain: Chain): Response {
        val pixelName = chain.request().url.pathSegments.last()

        if (pixelName.startsWith(YOUTUBE_TELEMETRY_PIXEL_PREFIX, ignoreCase = true) &&
            statusChecker.currentState() !is AdBlockingState.Enabled.UserEnabled
        ) {
            logcat(INFO) { "Ad blocking telemetry pixel dropped (no explicit consent): $pixelName" }
            return dummyResponse(chain)
        }

        logcat(INFO) { "Pixel proceeding: $pixelName" }
        return chain.proceed(chain.request())
    }

    // Must return a 200, not an error. EventHub fires via Pixel.enqueueFire(), which stores the pixel
    // in RxPixelSender's pending-pixel queue and only deletes it on a successful send. An error
    // would leave the pixel queued and retried on every app start (and sent later if the user
    // consents); a synthetic 200 makes it "sent" → deleted → never retried.
    private fun dummyResponse(chain: Chain): Response {
        return Response.Builder()
            .code(200)
            .protocol(Protocol.HTTP_2)
            .body("Ad blocking telemetry pixel dropped".toResponseBody())
            .message("Dropped ad blocking telemetry pixel")
            .request(chain.request())
            .build()
    }

    override fun getInterceptor(): Interceptor = this

    companion object {
        const val YOUTUBE_TELEMETRY_PIXEL_PREFIX = "webTelemetry_youtube"
    }
}
