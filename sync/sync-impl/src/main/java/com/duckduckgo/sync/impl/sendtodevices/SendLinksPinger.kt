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

package com.duckduckgo.sync.impl.sendtodevices

import androidx.annotation.WorkerThread
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.impl.crypto.SyncJweCrypto
import com.duckduckgo.sync.impl.hkdfDeriveBase64Url
import com.duckduckgo.sync.store.SyncStore
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.suspendCancellableCoroutine
import logcat.logcat
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.coroutines.resume

/**
 * Empty "go sync now" pings over ntfy.sh. The link itself still travels through Sync; ntfy only sees a topic that
 * only devices on the same Sync account can derive, and an empty body.
 */
// ponytail: public ntfy.sh, self-host it (or a Sync long-poll endpoint) before this goes beyond Hack Days
@SingleIn(AppScope::class)
class SendLinksPinger @Inject constructor(
    private val syncStore: SyncStore,
    private val syncJweCrypto: SyncJweCrypto,
) {
    // Own client: no DDG headers or interceptors go to a third party, and no read timeout on the open stream.
    private val client = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build()

    private fun topicFor(deviceId: String): String? {
        val primaryKey = syncStore.primaryKey ?: return null
        return syncJweCrypto.hkdfDeriveBase64Url(primaryKey, deviceId.toByteArray(Charsets.UTF_8), TOPIC_INFO, TOPIC_BYTES)
    }

    @WorkerThread
    fun ping(deviceId: String) {
        val topic = topicFor(deviceId) ?: return
        runCatching {
            client.newCall(Request.Builder().url("$NTFY_URL/$topic").post(ByteArray(0).toRequestBody()).build()).execute().close()
        }.onFailure { logcat { "Sync-SendLinks: ping failed ${it.message}" } }
    }

    /**
     * Stays subscribed to this device's topic, calling [onPing] (on a background thread) for every ping.
     * Returns when the stream ends or fails; cancelling the caller closes the connection.
     */
    suspend fun listen(onPing: () -> Unit) {
        val topic = syncStore.deviceId?.let { topicFor(it) } ?: return
        val call = client.newCall(Request.Builder().url("$NTFY_URL/$topic/json").build())
        suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        logcat { "Sync-SendLinks: listen failed ${e.message}" }
                        if (cont.isActive) cont.resume(Unit)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        runCatching {
                            response.use {
                                val source = it.body?.source() ?: return@use
                                while (true) {
                                    val line = source.readUtf8Line() ?: break
                                    if (line.contains("\"event\":\"message\"")) onPing()
                                }
                            }
                        }
                        if (cont.isActive) cont.resume(Unit)
                    }
                },
            )
        }
    }

    companion object {
        private const val NTFY_URL = "https://ntfy.sh"
        const val TOPIC_INFO = "Send Links Topic"
        const val TOPIC_BYTES = 24
    }
}
