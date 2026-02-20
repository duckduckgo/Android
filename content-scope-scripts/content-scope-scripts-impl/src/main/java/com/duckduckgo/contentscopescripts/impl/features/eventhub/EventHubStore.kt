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

package com.duckduckgo.contentscopescripts.impl.features.eventhub

import android.content.Context
import android.content.SharedPreferences
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import javax.inject.Inject

/**
 * Persistence for eventHub telemetry state.
 *
 * Uses a dedicated SharedPreferences file to isolate from fire button
 * and other data clearing operations.
 */
interface EventHubStore {
    fun loadAllTelemetryStates(): List<TelemetryPersistedState>
    fun saveTelemetryState(state: TelemetryPersistedState)
    fun removeTelemetryState(name: String)
    fun removeAllTelemetryStates()
}

@ContributesBinding(AppScope::class)
class SharedPrefsEventHubStore @Inject constructor(
    private val context: Context,
) : EventHubStore {

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val moshi = Moshi.Builder().build()
    private val listType = Types.newParameterizedType(List::class.java, TelemetryPersistedState::class.java)
    private val adapter: JsonAdapter<List<TelemetryPersistedState>> = moshi.adapter(listType)

    override fun loadAllTelemetryStates(): List<TelemetryPersistedState> {
        val json = prefs.getString(KEY_STATES, null) ?: return emptyList()
        return try {
            adapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun saveTelemetryState(state: TelemetryPersistedState) {
        val states = loadAllTelemetryStates().filter { it.name != state.name } + state
        saveAll(states)
    }

    override fun removeTelemetryState(name: String) {
        val states = loadAllTelemetryStates().filter { it.name != name }
        saveAll(states)
    }

    override fun removeAllTelemetryStates() {
        prefs.edit().remove(KEY_STATES).apply()
    }

    private fun saveAll(states: List<TelemetryPersistedState>) {
        val json = adapter.toJson(states)
        prefs.edit().putString(KEY_STATES, json).apply()
    }

    companion object {
        private const val PREFS_NAME = "com.duckduckgo.eventhub"
        private const val KEY_STATES = "telemetryStates"
    }
}
