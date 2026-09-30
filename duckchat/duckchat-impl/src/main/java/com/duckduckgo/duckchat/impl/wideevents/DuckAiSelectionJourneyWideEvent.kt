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

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.statistics.wideevents.CleanupPolicy
import com.duckduckgo.app.statistics.wideevents.FlowStatus
import com.duckduckgo.app.statistics.wideevents.WideEventClient
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.impl.contextual.DuckChatContextualTimeProvider
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import logcat.logcat
import javax.inject.Inject

enum class SelectionSubmissionAction(val value: String) {
    PROMPT("prompt"),
    SUMMARIZE("summarize"),
    TRANSLATE("translate"),
}

enum class SelectionTerminalReason(val value: String) {
    SUBMITTED("submitted"),
    SELECTIONS_REMOVED("selections_removed"),
    NEW_CHAT("new_chat"),
    CHAT_CLEARED("chat_cleared"),
    TAB_CLOSED("tab_closed"),
    MOVED_TO_TAB("moved_to_tab"),
    SESSION_EXPIRED("session_expired"),
}

interface DuckAiSelectionJourneyWideEvent {
    fun onSelectionAttached(tabId: String, count: Int)
    fun onSelectionRemoved(tabId: String, remaining: Int)
    fun onSuggestionsViewed(tabId: String)
    fun onSurfaceDismissed(tabId: String)
    fun onSuggestionSelected(tabId: String, action: SelectionSubmissionAction)
    fun onPromptSubmitted(tabId: String)
    fun onJourneyEnded(tabId: String, reason: SelectionTerminalReason)
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealDuckAiSelectionJourneyWideEvent @Inject constructor(
    private val wideEventClient: WideEventClient,
    private val timeProvider: DuckChatContextualTimeProvider,
    dispatchers: DispatcherProvider,
    @AppCoroutineScope appCoroutineScope: CoroutineScope,
) : DuckAiSelectionJourneyWideEvent {

    private val coroutineScope = CoroutineScope(appCoroutineScope.coroutineContext + dispatchers.io())
    private val channel = Channel<Action>(capacity = Channel.UNLIMITED)

    private val journeys = mutableMapOf<String, Journey>()

    init {
        coroutineScope.launch {
            for (action in channel) {
                try {
                    process(action)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logcat(tag = TAG) { "Error processing ${action::class.simpleName}: ${e.message}" }
                }
            }
        }
    }

    override fun onSelectionAttached(tabId: String, count: Int) = send(Action.Attached(tabId, count))
    override fun onSelectionRemoved(tabId: String, remaining: Int) = send(Action.Removed(tabId, remaining))
    override fun onSuggestionsViewed(tabId: String) = send(Action.SuggestionsViewed(tabId))
    override fun onSurfaceDismissed(tabId: String) = send(Action.Dismissed(tabId))
    override fun onSuggestionSelected(tabId: String, action: SelectionSubmissionAction) = send(Action.SuggestionSelected(tabId, action))
    override fun onPromptSubmitted(tabId: String) = send(Action.Submitted(tabId))
    override fun onJourneyEnded(tabId: String, reason: SelectionTerminalReason) = send(Action.Ended(tabId, reason))

    private fun send(action: Action) {
        channel.trySend(action)
    }

    private suspend fun process(action: Action) {
        val tabId = action.tabId
        when (action) {
            is Action.Attached -> {
                expireIfStale(tabId)
                val current = journeys[tabId] ?: start(tabId) ?: return
                journeys[tabId] = current.copy(maxSelectionCount = maxOf(current.maxSelectionCount, action.count))
            }

            is Action.Removed -> if (action.remaining == 0) finish(tabId, SelectionTerminalReason.SELECTIONS_REMOVED)

            is Action.SuggestionsViewed -> update(tabId) { it.copy(sawSuggestions = true) }

            is Action.Dismissed -> update(tabId) { it.copy(dismissalCount = it.dismissalCount + 1, submissionAction = null) }

            is Action.SuggestionSelected -> update(tabId) { it.copy(submissionAction = action.action) }

            is Action.Submitted -> finish(tabId, SelectionTerminalReason.SUBMITTED)

            is Action.Ended -> finish(tabId, action.reason)
        }
    }

    private fun update(tabId: String, transform: (Journey) -> Journey) {
        journeys[tabId]?.let { journeys[tabId] = transform(it) }
    }

    private suspend fun expireIfStale(tabId: String) {
        val current = journeys[tabId] ?: return
        if (timeProvider.currentTimeMillis() - current.startedAt > MAX_JOURNEY_DURATION_MS) {
            finish(tabId, SelectionTerminalReason.SESSION_EXPIRED)
        }
    }

    private suspend fun start(tabId: String): Journey? {
        val id = wideEventClient.flowStart(
            name = FLOW_NAME,
            cleanupPolicy = CleanupPolicy.OnProcessStart(ignoreIfIntervalTimeoutPresent = false),
        ).getOrNull() ?: return null
        wideEventClient.intervalStart(wideEventId = id, key = KEY_DURATION)
        return Journey(id = id, startedAt = timeProvider.currentTimeMillis()).also { journeys[tabId] = it }
    }

    private suspend fun finish(tabId: String, reason: SelectionTerminalReason) {
        val current = journeys.remove(tabId) ?: return
        wideEventClient.intervalEnd(wideEventId = current.id, key = KEY_DURATION)
        wideEventClient.flowFinish(
            wideEventId = current.id,
            status = if (reason == SelectionTerminalReason.SUBMITTED) FlowStatus.Success else FlowStatus.Cancelled,
            metadata = buildMap {
                put(KEY_TERMINAL_REASON, reason.value)
                put(KEY_SUBMISSION_ACTION, (current.submissionAction ?: SelectionSubmissionAction.PROMPT).value)
                put(KEY_MAX_COUNT, selectionCountBucket(current.maxSelectionCount))
                put(KEY_DISMISSAL_COUNT, dismissalCountBucket(current.dismissalCount))
                put(KEY_DISMISSED_BEFORE_SUBMISSION, (current.dismissalCount > 0).toString())
                put(KEY_SAW_SUGGESTIONS, current.sawSuggestions.toString())
            },
        )
    }

    private fun selectionCountBucket(count: Int): String = when (count) {
        1 -> "1"
        2 -> "2"
        else -> "3-5"
    }

    private fun dismissalCountBucket(count: Int): String = when (count) {
        0 -> "0"
        1 -> "1"
        else -> "2+"
    }

    private data class Journey(
        val id: Long,
        val startedAt: Long,
        val maxSelectionCount: Int = 1,
        val dismissalCount: Int = 0,
        val sawSuggestions: Boolean = false,
        val submissionAction: SelectionSubmissionAction? = null,
    )

    private sealed interface Action {
        val tabId: String

        data class Attached(override val tabId: String, val count: Int) : Action
        data class Removed(override val tabId: String, val remaining: Int) : Action
        data class SuggestionsViewed(override val tabId: String) : Action
        data class Dismissed(override val tabId: String) : Action
        data class SuggestionSelected(override val tabId: String, val action: SelectionSubmissionAction) : Action
        data class Submitted(override val tabId: String) : Action
        data class Ended(override val tabId: String, val reason: SelectionTerminalReason) : Action
    }

    private companion object {
        const val TAG = "DuckAiSelectionJourney"
        const val FLOW_NAME = "duckai-selection-journey"
        const val MAX_JOURNEY_DURATION_MS = 300_000L
        const val KEY_DURATION = "latency.journey_duration_ms_bucketed"
        const val KEY_TERMINAL_REASON = "outcome.terminal_reason"
        const val KEY_SUBMISSION_ACTION = "submission.action"
        const val KEY_MAX_COUNT = "selection.max_count_bucketed"
        const val KEY_DISMISSAL_COUNT = "interaction.dismissal_count_bucketed"
        const val KEY_DISMISSED_BEFORE_SUBMISSION = "interaction.dismissed_before_submission"
        const val KEY_SAW_SUGGESTIONS = "interaction.saw_selection_suggestions"
    }
}
