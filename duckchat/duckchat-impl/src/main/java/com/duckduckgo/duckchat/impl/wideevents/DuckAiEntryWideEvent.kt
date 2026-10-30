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

package com.duckduckgo.duckchat.impl.wideevents

import android.annotation.SuppressLint
import androidx.core.net.toUri
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.statistics.wideevents.CleanupPolicy
import com.duckduckgo.app.statistics.wideevents.FlowStatus
import com.duckduckgo.app.statistics.wideevents.WideEventClient
import com.duckduckgo.browser.api.BrowserLifecycleObserver
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.DuckAiSessionCallback
import com.duckduckgo.duckchat.api.DuckAiSessionExitTrigger
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.api.DuckChatEntryPoint
import com.duckduckgo.duckchat.api.toChatIdOrNull
import com.duckduckgo.duckchat.impl.pixel.toPixelValue
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject

@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<BrowserLifecycleObserver>())
class DuckAiEntryWideEvent @Inject constructor(
    private val wideEventClient: WideEventClient,
    private val duckChat: Lazy<DuckChat>,
    private val feature: Lazy<DuckAiEntryWideEventFeature>,
    private val dispatchers: DispatcherProvider,
    @AppCoroutineScope appCoroutineScope: CoroutineScope,
) : DuckAiSessionCallback, BrowserLifecycleObserver {

    @SuppressLint("AvoidComputationUsage")
    private val coroutineScope = CoroutineScope(appCoroutineScope.coroutineContext + dispatchers.computation().limitedParallelism(1))
    private val mutex = Mutex()
    private var activeEntry: Entry? = null

    fun onEntry(entryPoint: DuckChatEntryPoint, hasPrompt: Boolean) = mutate {
        start(entryPoint.toPixelValue(), hasPrompt, Surface.DUCK_AI_TAB)
    }

    fun onContextualSheetOpened(tabId: String, hasPrompt: Boolean, url: String?) = mutate {
        start(DuckChatEntryPoint.CONTEXTUAL_CHAT.toPixelValue(), hasPrompt, Surface.CONTEXTUAL_SHEET, tabId, url)
    }

    fun onContextualSheetUrlResolved(tabId: String, url: String?) = mutate {
        val entry = activeEntry ?: return@mutate
        if (entry.surface == Surface.CONTEXTUAL_SHEET && entry.tabId == tabId) resolveEntry(entry, tabId, url)
    }

    fun onContextualSheetClosed(tabId: String) = mutate {
        val entry = activeEntry ?: return@mutate
        if (entry.surface == Surface.CONTEXTUAL_SHEET && entry.tabId == tabId) {
            finish("left_duckai", DuckAiSessionExitTrigger.BACK_OR_CLOSE)
        }
    }

    override fun onLaunchLandingResolved(tabId: String?, url: String?) = mutate {
        if (tabId != null && isDuckAi(url)) {
            start("app_open", false, Surface.DUCK_AI_TAB, tabId, url)
        }
    }

    override fun onDuckAiPageVisible(tabId: String, url: String) = mutate {
        val entry = activeEntry ?: return@mutate
        if (entry.surface == Surface.DUCK_AI_TAB && !entry.resolved && (entry.tabId == null || entry.tabId == tabId) && isDuckAi(url)) {
            resolveEntry(entry, tabId, url)
        }
    }

    override fun onSelectedTabChanged(tabId: String?, url: String?) = mutate {
        val entry = activeEntry ?: return@mutate
        if (entry.tabId == null) {
            if (entry.surface == Surface.DUCK_AI_TAB && tabId != null && isDuckAi(url)) resolveEntry(entry, tabId, url)
            return@mutate
        }
        if (entry.tabId != tabId) {
            finish("left_duckai", entry.exitTrigger ?: DuckAiSessionExitTrigger.TAB_SWITCHED)
        } else if (entry.surface == Surface.DUCK_AI_TAB && !isDuckAi(url)) {
            finish("left_duckai", entry.exitTrigger ?: DuckAiSessionExitTrigger.OTHER_NAVIGATION)
        } else if (entry.surface == Surface.DUCK_AI_TAB && !entry.resolved) {
            resolveEntry(entry, entry.tabId ?: return@mutate, url)
        } else if (entry.url != url) {
            entry.url = url
            entry.exitTrigger = null
        }
    }

    override fun onExitIntent(tabId: String, trigger: DuckAiSessionExitTrigger) = mutate {
        activeEntry?.takeIf { it.tabId == tabId && it.exitTrigger == null }?.exitTrigger = trigger
    }

    override fun onPromptSubmitted(tabId: String) = mutate {
        val entry = activeEntry ?: return@mutate
        if (entry.tabId != null && entry.tabId != tabId || entry.promptSubmitted) return@mutate
        entry.tabId = tabId
        entry.promptSubmitted = true
        wideEventClient.intervalEnd(entry.flowId, FIRST_PROMPT)
        wideEventClient.flowStep(entry.flowId, "prompt_submitted", metadata = mapOf("prompt_submitted" to "true"))
    }

    override fun onNewChatCreated(tabId: String) = mutate {}

    override fun onClose() = mutate {
        val trigger = activeEntry?.exitTrigger
        if (trigger == DuckAiSessionExitTrigger.BACK_OR_CLOSE) {
            finish("left_duckai", trigger)
        } else {
            finish("app_backgrounded")
        }
    }

    private suspend fun start(source: String, hasPrompt: Boolean, surface: Surface, tabId: String? = null, url: String? = null) {
        finish("new_entry")
        val flowId = wideEventClient.flowStart(
            name = "duckai_entry",
            cleanupPolicy = CleanupPolicy.OnProcessStart(ignoreIfIntervalTimeoutPresent = false, flowStatus = FlowStatus.Unknown),
            metadata = mapOf(
                "source" to source,
                "has_prompt" to hasPrompt.toString(),
                "chat_state" to chatState(url),
                "surface" to surface.pixelValue,
                "prompt_submitted" to "false",
                "status_reason" to "app_terminated",
            ),
        ).getOrNull() ?: return
        activeEntry = Entry(flowId, surface, tabId, url)
        wideEventClient.intervalStart(flowId, DURATION, buckets = WideEventClient.DEFAULT_INTERVAL_BUCKETS)
        wideEventClient.intervalStart(flowId, FIRST_PROMPT, buckets = WideEventClient.DEFAULT_INTERVAL_BUCKETS)
    }

    private suspend fun resolveEntry(entry: Entry, tabId: String, url: String?) {
        entry.tabId = tabId
        entry.url = url
        entry.resolved = true
        wideEventClient.flowStep(entry.flowId, "entry_resolved", metadata = mapOf("chat_state" to chatState(url)))
    }

    private suspend fun finish(reason: String, trigger: DuckAiSessionExitTrigger? = null) {
        val entry = activeEntry ?: return
        activeEntry = null
        wideEventClient.intervalEnd(entry.flowId, DURATION)
        wideEventClient.flowFinish(
            wideEventId = entry.flowId,
            status = if (entry.promptSubmitted) FlowStatus.Success else FlowStatus.Cancelled,
            metadata = buildMap {
                put("status_reason", reason)
                trigger?.let { put("exit_trigger", it.name.lowercase()) }
            },
        )
    }

    private fun mutate(block: suspend () -> Unit) {
        coroutineScope.launch {
            mutex.withLock {
                if (!withContext(dispatchers.io()) { feature.value.self().isEnabled() }) {
                    activeEntry?.let { wideEventClient.flowAbort(it.flowId) }
                    activeEntry = null
                    return@launch
                }
                block()
            }
        }
    }

    private fun isDuckAi(url: String?): Boolean = !url.isNullOrBlank() && duckChat.value.isDuckChatUrl(url.toUri())

    private fun chatState(url: String?): String =
        if (url?.toUri()?.toChatIdOrNull(duckChat.value).isNullOrBlank()) "new" else "existing"

    private enum class Surface(val pixelValue: String) {
        DUCK_AI_TAB("duck_ai_tab"),
        CONTEXTUAL_SHEET("contextual_sheet"),
    }

    private class Entry(
        val flowId: Long,
        val surface: Surface,
        var tabId: String?,
        var url: String?,
        var resolved: Boolean = tabId != null,
        var promptSubmitted: Boolean = false,
        var exitTrigger: DuckAiSessionExitTrigger? = null,
    )

    private companion object {
        const val FIRST_PROMPT = "time_to_first_prompt_ms_bucketed"
        const val DURATION = "time_in_duckai_ms_bucketed"
    }
}
