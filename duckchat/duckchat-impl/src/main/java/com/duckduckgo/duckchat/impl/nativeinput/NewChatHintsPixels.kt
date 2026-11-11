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

package com.duckduckgo.duckchat.impl.nativeinput

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixels
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/**
 * A New Chat counts once it shows, whether or not the hints flag is on, so the flag-off half is the baseline for how often a
 * New Chat reaches its first prompt. It stays counted until the user leaves it, so the same New Chat reported again isn't
 * counted twice. That includes after the first prompt: the input clears before the chat gets its id, so the page still looks
 * like a New Chat with an empty draft for a moment.
 */
@SingleIn(AppScope::class)
class NewChatHintsPixels @Inject constructor(
    private val duckChatPixels: DuckChatPixels,
    private val feature: DuckChatFeature,
    private val dispatchers: DispatcherProvider,
) {
    private val newChats = ConcurrentHashMap<String, CountedNewChat>()

    // Tracked from the input rather than the hints view, which only exists while the hints flag is on. The caller passes the
    // flows because the tabs and chats it reads belong to the activity's browser mode, which an app-scoped class can't inject.
    suspend fun track(tabId: String, isTabSelected: Flow<Boolean>, newChatStates: Flow<NewChat>) {
        val hasNewChatHints = withContext(dispatchers.io()) { feature.newChatHints().isEnabled() }
        // The frontend hiding the input on the same page isn't leaving. A New Chat already counted may have been left while
        // tracking was paused for another tab, so it starts as one to be left.
        var wasNewChat = newChats.containsKey(tabId)
        combine(isTabSelected, newChatStates) { selected, newChat -> selected to newChat }
            .collect { (isSelected, newChat) ->
                if (wasNewChat && !newChat.isNewChatPage) newChats.remove(tabId)
                wasNewChat = newChat.isGuaranteed || (wasNewChat && newChat.isNewChatPage)
                if (isSelected && newChat.isGuaranteed) newChat(tabId, hasNewChatHints)
            }
    }

    // The hints observe the same New Chat separately from track, so either may report it first.
    // Only the first row shown is reported; a hint whose model loads later isn't counted as an impression.
    fun onHintsShown(tabId: String, hints: List<NewChatHint>) {
        val newChat = newChat(tabId, hasNewChatHints = true)
        if (newChat.isPrompted || newChat.isHintsShownReported) return
        newChat.isHintsShownReported = true
        duckChatPixels.fireNewChatHintsShown(hints)
    }

    fun onHintTapped(tabId: String, hint: NewChatHint, position: Int) {
        newChats[tabId]?.lastTappedHint = hint
        duckChatPixels.fireNewChatHintTapped(hint, position)
    }

    fun onPromptSubmitted(
        tabId: String,
        isNewChat: Boolean,
        hasImageAttachment: Boolean,
        hasFileAttachment: Boolean,
        selectedTool: String,
    ) {
        if (!isNewChat) {
            newChats.remove(tabId)
            return
        }
        val newChat = newChats[tabId]?.takeUnless { it.isPrompted } ?: return
        newChat.isPrompted = true
        duckChatPixels.fireNewChatFirstPromptSent(
            hasNewChatHints = newChat.hasNewChatHints,
            hintTapped = newChat.lastTappedHint,
            hasImageAttachment = hasImageAttachment,
            hasFileAttachment = hasFileAttachment,
            selectedTool = selectedTool,
        )
    }

    private fun newChat(tabId: String, hasNewChatHints: Boolean): CountedNewChat {
        val counted = CountedNewChat(hasNewChatHints)
        return newChats.putIfAbsent(tabId, counted) ?: counted.also { duckChatPixels.fireNewChatShown(hasNewChatHints) }
    }

    private class CountedNewChat(val hasNewChatHints: Boolean) {
        @Volatile var isHintsShownReported = false

        @Volatile var lastTappedHint: NewChatHint? = null

        @Volatile var isPrompted = false
    }
}
