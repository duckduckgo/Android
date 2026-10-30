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
import com.duckduckgo.app.statistics.wideevents.WideEventDefinition
import com.duckduckgo.app.tabs.model.DuckAiTabSessionRepository
import com.duckduckgo.browser.api.BrowserLifecycleObserver
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.DuckAiEntryCallback
import com.duckduckgo.duckchat.api.DuckAiSessionCallback
import com.duckduckgo.duckchat.api.DuckAiSessionExitTrigger
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.api.DuckChatEntryPoint
import com.duckduckgo.duckchat.api.DuckChatInputModeState
import com.duckduckgo.duckchat.api.nativeinput.NativeInputState
import com.duckduckgo.duckchat.api.toChatIdOrNull
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixels
import com.duckduckgo.duckchat.impl.pixel.toPixelValue
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<DuckAiEntryCallback>())
@ContributesIntoSet(AppScope::class, binding = binding<BrowserLifecycleObserver>())
class DuckAiEntryWideEvent @Inject constructor(
    private val wideEventClient: WideEventClient,
    private val duckChat: Lazy<DuckChat>,
    private val feature: Lazy<DuckAiEntryWideEventFeature>,
    private val dispatchers: DispatcherProvider,
    private val pixels: Lazy<DuckChatPixels>,
    private val inputMode: Lazy<DuckChatInputModeState>,
    private val tabSessions: DuckAiTabSessionRepository,
    @AppCoroutineScope appCoroutineScope: CoroutineScope,
) : DuckAiSessionCallback, DuckAiEntryCallback, BrowserLifecycleObserver {

    @SuppressLint("AvoidComputationUsage")
    private val coroutineScope = CoroutineScope(appCoroutineScope.coroutineContext + dispatchers.computation().limitedParallelism(1))
    private val mutex = Mutex()
    private val backgroundVoiceMonitor = ConflatedJob()
    private var activeEntry: Entry? = null
    private var selectedTabId: String? = null
    private var pendingLanding: Landing? = null
    private var foreground = false
    private var launchIntentPending = false
    private var launchOpensDuckAi = false
    private var explicitEntryReported = false

    override fun onLaunchIntentReceived(opensDuckAi: Boolean) = mutate {
        launchIntentPending = true
        launchOpensDuckAi = opensDuckAi
    }

    override fun onLaunchIntentHandled() = mutate {
        launchIntentPending = false
        startPendingLanding()
    }

    override fun onForeground() = mutate {
        foreground = true
        backgroundVoiceMonitor.cancel()
        startPendingLanding()
    }

    fun onEntry(
        entryPoint: DuckChatEntryPoint,
        hasPrompt: Boolean,
        opensNewTab: Boolean = false,
        voice: Boolean = false,
    ) = mutate {
        explicitEntryReported = true
        pendingLanding = null
        start(
            entryPoint.toPixelValue(),
            hasPrompt,
            Surface.DUCK_AI_TAB,
            excludedTabId = selectedTabId.takeIf { opensNewTab },
            voice = voice,
        )
    }

    fun onContextualSheetOpened(tabId: String, hasPrompt: Boolean, url: String?) = mutate {
        explicitEntryReported = true
        pendingLanding = null
        start(DuckChatEntryPoint.CONTEXTUAL_CHAT.toPixelValue(), hasPrompt, Surface.CONTEXTUAL_SHEET, tabId, url)
    }

    fun onContextualSheetUrlResolved(tabId: String, url: String?) = mutate {
        val entry = activeEntry ?: return@mutate
        if (entry.surface == Surface.CONTEXTUAL_SHEET && entry.tabId == tabId) resolveEntry(entry, tabId, url)
    }

    fun onContextualSheetClosed(tabId: String) = mutate {
        val entry = activeEntry ?: return@mutate
        if (entry.surface == Surface.CONTEXTUAL_SHEET && entry.tabId == tabId) {
            finish("left_duckai", ExitTrigger.BACK_OR_CLOSE)
        }
    }

    override fun onLaunchLandingResolved(tabId: String?, url: String?) = mutate {
        if (explicitEntryReported) return@mutate
        selectedTabId = tabId
        pendingLanding = if (tabId != null && isDuckAi(url)) Landing(tabId, url) else null
        startPendingLanding()
    }

    private suspend fun startPendingLanding() {
        if (!foreground || launchIntentPending || launchOpensDuckAi || explicitEntryReported) return
        val landing = pendingLanding ?: return
        pendingLanding = null
        explicitEntryReported = true
        if (duckChat.value.isVoiceChatSessionActive(landing.tabId)) return
        reportImplicitEntry(DuckChatEntryPoint.APP_OPEN, landing.tabId, landing.url)
        start(DuckChatEntryPoint.APP_OPEN.toPixelValue(), false, Surface.DUCK_AI_TAB, landing.tabId, landing.url)
    }

    override fun onDuckAiPageVisible(tabId: String, url: String) = mutate {
        val entry = activeEntry ?: return@mutate
        if (tabId == entry.excludedTabId) return@mutate
        if (entry.surface == Surface.DUCK_AI_TAB && !entry.resolved && (entry.tabId == null || entry.tabId == tabId) && isDuckAi(url)) {
            resolveEntry(entry, tabId, url)
        }
    }

    override fun onSelectedTabChanged(tabId: String?, url: String?) = mutate {
        selectedTabId = tabId
        val entry = activeEntry ?: return@mutate
        if (entry.tabId == null) {
            if (tabId == entry.excludedTabId) return@mutate
            if (entry.surface == Surface.DUCK_AI_TAB && tabId != null && isDuckAi(url)) resolveEntry(entry, tabId, url)
            return@mutate
        }
        if (entry.tabId != tabId) {
            finish("left_duckai", entry.exitTrigger ?: ExitTrigger.TAB_SWITCHED)
        } else if (entry.surface == Surface.DUCK_AI_TAB && !isDuckAi(url)) {
            finish("left_duckai", entry.exitTrigger ?: ExitTrigger.OTHER_NAVIGATION)
        } else if (entry.surface == Surface.DUCK_AI_TAB && !entry.resolved) {
            resolveEntry(entry, entry.tabId ?: return@mutate, url)
        } else if (entry.url != url) {
            entry.url = url
            entry.exitTrigger = null
        }
    }

    override fun onExitIntent(tabId: String, trigger: DuckAiSessionExitTrigger) = mutate {
        activeEntry?.takeIf { it.tabId == tabId && it.exitTrigger == null }?.exitTrigger = when (trigger) {
            DuckAiSessionExitTrigger.BACK_OR_CLOSE -> ExitTrigger.BACK_OR_CLOSE
            DuckAiSessionExitTrigger.TAB_SWITCHED -> ExitTrigger.TAB_SWITCHED
            DuckAiSessionExitTrigger.NEW_TAB_OPENED -> ExitTrigger.NEW_TAB_OPENED
            DuckAiSessionExitTrigger.FIRE_TAB_OPENED -> ExitTrigger.FIRE_TAB_OPENED
            DuckAiSessionExitTrigger.OTHER_NAVIGATION -> ExitTrigger.OTHER_NAVIGATION
        }
    }

    override fun onTabSwiped(tabId: String, url: String?) = mutate {
        if (tabId == selectedTabId || !isDuckAi(url)) return@mutate
        explicitEntryReported = true
        pendingLanding = null
        reportImplicitEntry(DuckChatEntryPoint.TAB_SWIPE, tabId, url)
        start(DuckChatEntryPoint.TAB_SWIPE.toPixelValue(), false, Surface.DUCK_AI_TAB, tabId, url)
    }

    private suspend fun reportImplicitEntry(entryPoint: DuckChatEntryPoint, tabId: String, url: String?) {
        withContext(dispatchers.io()) {
            tabSessions.setPendingEntryPointSource(entryPoint.toPixelValue())
            tabSessions.tryClaimEntryPointSource(tabId, url)
            val duckAiEnabled = duckChat.value.isEnabled()
            pixels.value.sendDuckChatEntryPixel(
                entryPoint = entryPoint,
                opensNewTab = false,
                hasPrompt = false,
                duckAiEnabled = duckAiEnabled,
                inputScreenEnabled = duckAiEnabled && inputMode.value.getSelectedInputMode() == NativeInputState.InputMode.SEARCH_AND_DUCK_AI,
            )
        }
    }

    override fun onSearchStarted(tabId: String) = mutate {
        activeEntry?.takeIf { it.tabId == tabId && it.surface == Surface.DUCK_AI_TAB && it.exitTrigger == null }
            ?.exitTrigger = ExitTrigger.SEARCH_STARTED
    }

    fun onChatsDeleted(chatIds: Set<String>) = mutate {
        val entry = activeEntry ?: return@mutate
        val chatId = entry.url?.toUri()?.toChatIdOrNull(duckChat.value) ?: return@mutate
        if (chatId in chatIds) finish("left_duckai", ExitTrigger.CHAT_DELETED)
    }

    override fun onPromptSubmitted(tabId: String) = mutate {
        val entry = activeEntry ?: return@mutate
        if (tabId == entry.excludedTabId || entry.tabId != null && entry.tabId != tabId || entry.promptSubmitted) return@mutate
        entry.tabId = tabId
        entry.promptSubmitted = true
        wideEventClient.intervalEnd(entry.flowId, FIRST_PROMPT)
        wideEventClient.flowStep(entry.flowId, "prompt_submitted", metadata = mapOf("prompt_submitted" to "true"))
    }

    override fun onNewChatCreated(tabId: String) = mutate {}

    override fun onClose() = mutate {
        foreground = false
        launchIntentPending = false
        launchOpensDuckAi = false
        explicitEntryReported = false
        pendingLanding = null
        val entry = activeEntry
        val trigger = entry?.exitTrigger
        val voiceTabId = entry?.tabId?.takeIf { duckChat.value.isVoiceChatSessionActive(it) }
        if (trigger == null && voiceTabId != null) {
            backgroundVoiceMonitor += coroutineScope.launch {
                duckChat.value.activeVoiceChatSessions.firstOrNull { voiceTabId !in it } ?: return@launch
                mutate {
                    if (!foreground && activeEntry?.flowId == entry.flowId) finish("app_backgrounded")
                }
            }
        } else if (trigger == ExitTrigger.BACK_OR_CLOSE) {
            finish("left_duckai", trigger)
        } else {
            finish("app_backgrounded")
        }
    }

    private suspend fun start(
        source: String,
        hasPrompt: Boolean,
        surface: Surface,
        tabId: String? = null,
        url: String? = null,
        excludedTabId: String? = null,
        voice: Boolean = false,
    ) {
        finish("new_entry")
        if (!withContext(dispatchers.io()) { feature.value.self().isEnabled() }) return
        val flowId = wideEventClient.flowStart(
            name = "duckai_entry",
            definition = WideEventDefinition(version = WideEventDefinition.Version(minor = 0, patch = 1)),
            cleanupPolicy = CleanupPolicy.OnProcessStart(ignoreIfIntervalTimeoutPresent = false, flowStatus = FlowStatus.Unknown),
            metadata = mapOf(
                "source" to source,
                "has_prompt" to hasPrompt.toString(),
                "voice" to voice.toString(),
                "chat_state" to chatState(url),
                "surface" to surface.pixelValue,
                "prompt_submitted" to "false",
                "status_reason" to "app_terminated",
            ),
        ).getOrNull() ?: return
        activeEntry = Entry(flowId, surface, tabId, url, excludedTabId)
        wideEventClient.intervalStart(flowId, DURATION, buckets = WideEventClient.DEFAULT_INTERVAL_BUCKETS)
        wideEventClient.intervalStart(flowId, FIRST_PROMPT, buckets = WideEventClient.DEFAULT_INTERVAL_BUCKETS)
    }

    private suspend fun resolveEntry(entry: Entry, tabId: String, url: String?) {
        entry.tabId = tabId
        entry.url = url
        entry.resolved = true
        wideEventClient.flowStep(entry.flowId, "entry_resolved", metadata = mapOf("chat_state" to chatState(url)))
    }

    private suspend fun finish(reason: String, trigger: ExitTrigger? = null) {
        backgroundVoiceMonitor.cancel()
        val entry = activeEntry ?: return
        activeEntry = null
        wideEventClient.intervalEnd(entry.flowId, DURATION)
        wideEventClient.flowFinish(
            wideEventId = entry.flowId,
            status = if (reason == "app_backgrounded") FlowStatus.Cancelled else FlowStatus.Success,
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
                }
                block()
            }
        }
    }

    private fun isDuckAi(url: String?): Boolean = !url.isNullOrBlank() && duckChat.value.isDuckChatUrl(url.toUri())

    private fun chatState(url: String?): String =
        if (url?.toUri()?.toChatIdOrNull(duckChat.value).isNullOrBlank()) "new" else "existing"

    private enum class ExitTrigger {
        BACK_OR_CLOSE,
        TAB_SWITCHED,
        NEW_TAB_OPENED,
        FIRE_TAB_OPENED,
        SEARCH_STARTED,
        OTHER_NAVIGATION,
        CHAT_DELETED,
    }

    private enum class Surface(val pixelValue: String) {
        DUCK_AI_TAB("duck_ai_tab"),
        CONTEXTUAL_SHEET("contextual_sheet"),
    }

    private data class Landing(val tabId: String, val url: String?)

    private class Entry(
        val flowId: Long,
        val surface: Surface,
        var tabId: String?,
        var url: String?,
        val excludedTabId: String?,
        var resolved: Boolean = tabId != null,
        var promptSubmitted: Boolean = false,
        var exitTrigger: ExitTrigger? = null,
    )

    private companion object {
        const val FIRST_PROMPT = "time_to_first_prompt_ms_bucketed"
        const val DURATION = "time_in_duckai_ms_bucketed"
    }
}
