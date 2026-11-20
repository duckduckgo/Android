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

import androidx.core.net.toUri
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.browsermode.api.RegularMode
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.api.toChatIdOrNull
import com.duckduckgo.duckchat.store.impl.DuckAiChatStore
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withTimeoutOrNull
import logcat.logcat
import javax.inject.Inject
import kotlin.math.pow
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@ContributesBinding(AppScope::class, binding = binding<DuckAiResponseReadyMonitor>())
class DuckAiResponseReadyMonitorImpl @Inject constructor(
    @RegularMode private val chatStore: DuckAiChatStore,
    @RegularMode private val tabRepository: TabRepository,
    private val storedResponseClient: DuckAiStoredResponseClient,
    private val duckChat: DuckChat,
) : DuckAiResponseReadyMonitor {

    override suspend fun await(
        tabId: String,
        chatId: String?,
        settings: ResponseReadyNotificationsSettings,
        onChatIdFound: suspend (String) -> Unit,
    ): WaitResult =
        try {
            val result = withTimeoutOrNull(settings.timeout) {
                awaitReady(tabId, chatId, settings, onChatIdFound)
            }
            when (result) {
                true -> WaitResult.Ready
                false -> WaitResult.Stopped
                null -> WaitResult.TimedOut
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat { "Duck.ai response wait failed: ${e::class.simpleName}" }
            WaitResult.Stopped
        }

    private suspend fun awaitReady(
        tabId: String,
        knownChatId: String?,
        settings: ResponseReadyNotificationsSettings,
        onChatIdFound: suspend (String) -> Unit,
    ): Boolean {
        val chatId = knownChatId ?: awaitChatIdInTabUrl(tabId).also { onChatIdFound(it) }
        val storedResponseId = awaitPendingResponseId(chatId, settings.pollInterval)
        return awaitStoredResponse(storedResponseId, settings.pollInterval)
    }

    private suspend fun awaitChatIdInTabUrl(tabId: String): String =
        tabRepository.flowTabs
            .mapNotNull { tabs -> tabs.firstOrNull { it.tabId == tabId && !it.deletable }?.url?.toUri()?.toChatIdOrNull(duckChat) }
            .first()

    /** The page can save the chat after the user left, so the saved chat is read again until it shows the pending response ID. */
    private suspend fun awaitPendingResponseId(chatId: String, pollInterval: Duration): String {
        while (true) {
            findPendingResponseId(chatStore.getChatContent(chatId))?.let { return it }
            delay(pollInterval)
        }
    }

    private suspend fun awaitStoredResponse(responseId: String, pollInterval: Duration): Boolean {
        var rateLimits = 0
        while (true) {
            val code = try {
                storedResponseClient.getStoredResponse(responseId).code()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            when (code) {
                200 -> return true
                418, 429 -> {
                    delay(rateLimitBackoff(rateLimits++))
                    continue
                }
                null, 404, 408, in 500..599 -> Unit
                else -> {
                    logcat { "Duck.ai stored response check stopped on HTTP $code" }
                    return false
                }
            }
            rateLimits = 0
            delay(pollInterval)
        }
    }
}

internal fun rateLimitBackoff(previousRateLimits: Int): Duration =
    30.seconds * 2.0.pow(previousRateLimits) * (1 + Random.nextDouble())
