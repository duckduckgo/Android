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

package com.duckduckgo.duckchat.impl.responseready

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.browser.api.BrowserLifecycleObserver
import com.duckduckgo.browsermode.api.RegularMode
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.store.impl.DuckAiChatStore
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import logcat.logcat
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Named
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

// POC: throwaway spike that proves the leave -> poll Sync -> notify -> tap pipeline. Not production code.
interface DuckAiResponseMonitor {
    fun onLeft(tabId: String, chatId: String)
    fun onReturned(chatId: String)
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<DuckAiResponseMonitor>())
@ContributesIntoSet(AppScope::class, binding = binding<BrowserLifecycleObserver>())
class DuckAiResponseMonitorImpl @Inject constructor(
    private val context: Context,
    @RegularMode private val chatStore: DuckAiChatStore,
    @Named("api") private val okHttpClient: Lazy<OkHttpClient>,
    private val dispatchers: DispatcherProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : DuckAiResponseMonitor, BrowserLifecycleObserver {

    // All state is touched on main only.
    private val watches = mutableMapOf<String, Job>()
    private var appOpen = true

    override fun onLeft(tabId: String, chatId: String) {
        appCoroutineScope.launch(dispatchers.main()) {
            if (chatId in watches) return@launch
            logcat(tag = TAG) { "POC: watching chat=$chatId tab=$tabId appOpen=$appOpen" }
            cancelNotification(chatId)
            // Runs on main after this block returns, so the map entry exists before the watch can end.
            watches[chatId] = appCoroutineScope.launch(dispatchers.main()) {
                try {
                    watch(tabId, chatId)
                } finally {
                    if (watches[chatId] === coroutineContext[Job]) watches.remove(chatId)
                    if (watches.isEmpty()) DuckAiRespondingService.stop(context)
                }
            }
            if (!appOpen) startService()
        }
    }

    override fun onReturned(chatId: String) {
        appCoroutineScope.launch(dispatchers.main()) {
            watches[chatId]?.let {
                logcat(tag = TAG) { "POC: user returned to chat=$chatId, stop watching" }
                it.cancel()
            }
            cancelNotification(chatId)
        }
    }

    override fun onOpen(isFreshLaunch: Boolean) {
        appOpen = true
        DuckAiRespondingService.stop(context)
    }

    override fun onClose() {
        appOpen = false
        if (watches.isNotEmpty()) startService()
    }

    private suspend fun watch(tabId: String, chatId: String) {
        val responseId = withContext(dispatchers.io()) { readResponseId(chatId) }
        if (responseId == null) {
            logcat(tag = TAG) { "POC: no response ID for chat=$chatId" }
            return
        }
        val finalCode = withTimeoutOrNull(TIMEOUT) {
            var code: Int
            do {
                code = withContext(dispatchers.io()) { fetchStatus(responseId) }
                logcat(tag = TAG) { "POC: poll response=$responseId -> $code" }
                val pending = code != 200 && code != 410
                if (pending) delay(POLL_INTERVAL)
            } while (pending)
            code
        }
        if (finalCode == 200) notifyReady(tabId, chatId) else logcat(tag = TAG) { "POC: gave up on chat=$chatId code=$finalCode" }
    }

    private fun startService() {
        logcat(tag = TAG) { "POC: starting FGS for ${watches.size} watch(es)" }
        DuckAiRespondingService.start(context)
    }

    private suspend fun readResponseId(chatId: String): String? {
        val content = chatStore.getChatContent(chatId) ?: return null
        return runCatching {
            val messages = JSONObject(content).getJSONArray("messages")
            (messages.length() - 1 downTo 0)
                .map { messages.getJSONObject(it) }
                .firstOrNull { it.optString("role") == "user" }
                ?.optString("messageId")
                ?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun fetchStatus(responseId: String): Int {
        val request = Request.Builder()
            .url("$STREAMS_URL/$responseId")
            .cacheControl(CacheControl.FORCE_NETWORK)
            .build()
        return runCatching { okHttpClient.value.newCall(request).execute().use { it.code } }.getOrDefault(-1)
    }

    @SuppressLint("MissingPermission")
    private fun notifyReady(tabId: String, chatId: String) {
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(
            NotificationChannel(READY_CHANNEL_ID, "Duck.ai responses", NotificationManager.IMPORTANCE_HIGH),
        )
        val tapIntent = PendingIntent.getActivity(
            context,
            chatId.hashCode(),
            ResponseReadyTapActivity.intent(context, tabId, chatId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, READY_CHANNEL_ID)
            .setSmallIcon(com.duckduckgo.mobile.android.R.drawable.notification_logo)
            .setContentTitle("Duck.ai responded")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(tapIntent)
            .build()
        logcat(tag = TAG) { "POC: posting ready notification for chat=$chatId enabled=${manager.areNotificationsEnabled()}" }
        if (manager.areNotificationsEnabled()) manager.notify(chatId.notificationTag(), READY_NOTIFICATION_ID, notification)
    }

    private fun cancelNotification(chatId: String) {
        NotificationManagerCompat.from(context).cancel(chatId.notificationTag(), READY_NOTIFICATION_ID)
    }

    private fun String.notificationTag() = hashCode().toString()

    private companion object {
        const val TAG = "DuckAiResponseMonitor"
        const val STREAMS_URL = "https://sync.duckduckgo.com/sync/ai_chat_streams"
        const val READY_CHANNEL_ID = "duck_ai_response_ready"
        const val READY_NOTIFICATION_ID = 9201
        val POLL_INTERVAL = 4.seconds
        val TIMEOUT = 3.minutes
    }
}
