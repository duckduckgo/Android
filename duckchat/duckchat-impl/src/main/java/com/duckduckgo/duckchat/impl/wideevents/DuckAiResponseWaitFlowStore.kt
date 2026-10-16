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

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.impl.di.DuckChat
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.flow.firstOrNull
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject

data class PersistedResponseWaitFlow(
    val flowId: Long,
    val tabId: String,
    val chatId: String?,
    val left: Boolean,
    val returned: Boolean,
    val done: Boolean,
    val result: String,
    val readyWhileAwayRecorded: Boolean,
    val readyToReturnRunning: Boolean,
    val chatSwitchedAway: Boolean,
)

/** Keeps open response-wait flows on the device so they can be picked up again after the process restarts. */
interface DuckAiResponseWaitFlowStore {
    suspend fun load(): List<PersistedResponseWaitFlow>
    suspend fun save(flows: List<PersistedResponseWaitFlow>)
}

@ContributesBinding(AppScope::class)
class DuckAiResponseWaitFlowStoreImpl @Inject constructor(
    @DuckChat private val store: DataStore<Preferences>,
) : DuckAiResponseWaitFlowStore {

    override suspend fun load(): List<PersistedResponseWaitFlow> {
        return try {
            val json = store.data.firstOrNull()?.get(OPEN_FLOWS) ?: return emptyList()
            val array = JSONArray(json)
            List(array.length()) { array.getJSONObject(it).toFlow() }
        } catch (e: JSONException) {
            emptyList()
        } catch (e: IOException) {
            emptyList()
        }
    }

    // Write failures propagate so the caller can retry the save after its next change.
    override suspend fun save(flows: List<PersistedResponseWaitFlow>) {
        val json = JSONArray(flows.map { it.toJson() }).toString()
        store.edit { it[OPEN_FLOWS] = json }
    }

    private fun PersistedResponseWaitFlow.toJson() = JSONObject()
        .put(FLOW_ID, flowId)
        .put(TAB_ID, tabId)
        .put(CHAT_ID, chatId)
        .put(LEFT, left)
        .put(RETURNED, returned)
        .put(DONE, done)
        .put(RESULT, result)
        .put(READY_WHILE_AWAY_RECORDED, readyWhileAwayRecorded)
        .put(READY_TO_RETURN_RUNNING, readyToReturnRunning)
        .put(CHAT_SWITCHED_AWAY, chatSwitchedAway)

    private fun JSONObject.toFlow() = PersistedResponseWaitFlow(
        flowId = getLong(FLOW_ID),
        tabId = getString(TAB_ID),
        chatId = if (isNull(CHAT_ID)) null else getString(CHAT_ID),
        left = getBoolean(LEFT),
        returned = getBoolean(RETURNED),
        done = getBoolean(DONE),
        result = getString(RESULT),
        readyWhileAwayRecorded = getBoolean(READY_WHILE_AWAY_RECORDED),
        readyToReturnRunning = getBoolean(READY_TO_RETURN_RUNNING),
        chatSwitchedAway = optBoolean(CHAT_SWITCHED_AWAY, false),
    )

    private companion object {
        val OPEN_FLOWS = stringPreferencesKey("duckai_response_wait_open_flows")

        const val FLOW_ID = "flowId"
        const val TAB_ID = "tabId"
        const val CHAT_ID = "chatId"
        const val LEFT = "left"
        const val RETURNED = "returned"
        const val DONE = "done"
        const val RESULT = "result"
        const val READY_WHILE_AWAY_RECORDED = "readyWhileAwayRecorded"
        const val READY_TO_RETURN_RUNNING = "readyToReturnRunning"
        const val CHAT_SWITCHED_AWAY = "chatSwitchedAway"
    }
}
