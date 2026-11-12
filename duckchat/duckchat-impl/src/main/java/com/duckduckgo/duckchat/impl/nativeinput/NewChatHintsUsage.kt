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

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.impl.di.DuckChat
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import logcat.LogPriority.WARN
import logcat.logcat
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/**
 * Hints hide once the user has started [HIDE_AFTER_IGNORED_CHATS] new chats after seeing them without tapping one;
 * a new chat started after a tap starts over. Leaving the new chat, or prompting outside one, drops the tab's interaction,
 * so only a prompt sent from the new chat the hints were shown in counts. Hiding is derived from the count rather than
 * stored, so raising the threshold shows hints again. Callers only record Regular-mode activity, so a Fire session leaves nothing behind.
 */
@SingleIn(AppScope::class)
class NewChatHintsUsage @Inject constructor(
    @DuckChat private val store: DataStore<Preferences>,
    private val dispatchers: DispatcherProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) {
    private val interactions = ConcurrentHashMap<String, HintInteraction>()
    private val writeMutex = Mutex()

    val isExhausted: Flow<Boolean> = store.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { preferences -> (preferences[DUCK_AI_NEW_CHAT_HINTS_IGNORED_CHATS] ?: 0) >= HIDE_AFTER_IGNORED_CHATS }
        .distinctUntilChanged()

    fun onHintsDisplayed(tabId: String) {
        // Hints show again after a tap (e.g. a cancelled picker), which must not undo the tap.
        interactions.putIfAbsent(tabId, HintInteraction.DISPLAYED)
    }

    fun onHintTapped(tabId: String) {
        interactions[tabId] = HintInteraction.TAPPED
    }

    fun onLeftNewChat(tabId: String) {
        interactions.remove(tabId)
    }

    fun onPromptSubmitted(tabId: String, isNewChat: Boolean) {
        val interaction = interactions.remove(tabId)
        if (!isNewChat) return
        when (interaction) {
            HintInteraction.TAPPED -> edit { preferences -> preferences.remove(DUCK_AI_NEW_CHAT_HINTS_IGNORED_CHATS) }
            HintInteraction.DISPLAYED -> edit { preferences ->
                preferences[DUCK_AI_NEW_CHAT_HINTS_IGNORED_CHATS] = (preferences[DUCK_AI_NEW_CHAT_HINTS_IGNORED_CHATS] ?: 0) + 1
            }
            null -> Unit
        }
    }

    suspend fun reset() {
        interactions.clear()
        writeMutex.withLock { editIgnoringIoFailure { preferences -> preferences.remove(DUCK_AI_NEW_CHAT_HINTS_IGNORED_CHATS) } }
    }

    // Undispatched so writes queue on the fair mutex in call order; otherwise a reset and an increment could land swapped.
    private fun edit(transform: (MutablePreferences) -> Unit) {
        appCoroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            writeMutex.withLock { withContext(dispatchers.io()) { editIgnoringIoFailure(transform) } }
        }
    }

    private suspend fun editIgnoringIoFailure(transform: (MutablePreferences) -> Unit) {
        try {
            store.edit(transform)
        } catch (e: IOException) {
            logcat(WARN) { "Duck.ai new chat hints: usage write failed: ${e.message}" }
        }
    }

    private enum class HintInteraction { DISPLAYED, TAPPED }

    companion object {
        const val HIDE_AFTER_IGNORED_CHATS = 5

        private val DUCK_AI_NEW_CHAT_HINTS_IGNORED_CHATS = intPreferencesKey("DUCK_AI_NEW_CHAT_HINTS_IGNORED_CHATS")
    }
}
