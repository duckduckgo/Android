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

import android.os.Handler
import android.os.Looper
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.Toggle
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import javax.inject.Inject
import javax.inject.Singleton

/**
 * EventHub manages client-side telemetry that processes webEvent notifications
 * from C-S-S's webDetection feature.
 *
 * The feature MUST NOT be disabled due to privacy protections being disabled.
 * The ONLY way to disable it is through an explicit disabled state (or feature absent)
 * in the remote configuration.
 */
interface EventHub {
    fun handleWebEvent(type: String, tabId: String)
    fun onConfigChanged()
    fun resetDeduplication(tabId: String)
}

@Singleton
@ContributesBinding(AppScope::class)
class RealEventHub @Inject constructor(
    private val eventHubFeature: EventHubFeature,
    private val pixel: Pixel,
    private val store: EventHubStore,
) : EventHub {

    private val telemetryMap = mutableMapOf<String, TelemetryEntry>()
    private val handler = Handler(Looper.getMainLooper())
    private val scheduledRunnables = mutableMapOf<String, Runnable>()

    /** Tracks seen event types per tab to deduplicate events per page. */
    private val seenEventsPerTab = mutableMapOf<String, MutableSet<String>>()

    private var currentTelemetryConfig: Map<String, Map<String, Any?>>? = null

    private val moshi = Moshi.Builder().build()

    init {
        restorePersistedState()
    }

    private fun isEnabled(): Boolean {
        return eventHubFeature.self().isEnabled()
    }

    // MARK: - Config Handling

    @Suppress("UNCHECKED_CAST")
    override fun onConfigChanged() {
        val toggle = eventHubFeature.self()
        val settingsJson = toggle.getSettings()

        if (!isEnabled()) {
            onDisable()
            return
        }

        val telemetryConfig = parseTelemetryConfig(settingsJson)
        currentTelemetryConfig = telemetryConfig

        if (telemetryConfig != null) {
            for (name in telemetryConfig.keys) {
                registerTelemetry(name, telemetryConfig)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseTelemetryConfig(settingsJson: String?): Map<String, Map<String, Any?>>? {
        if (settingsJson == null) return null
        return try {
            val type = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
            val adapter = moshi.adapter<Map<String, Any?>>(type)
            val settings = adapter.fromJson(settingsJson) ?: return null
            settings["telemetry"] as? Map<String, Map<String, Any?>>
        } catch (e: Exception) {
            null
        }
    }

    // MARK: - Telemetry Registration

    private fun registerTelemetry(name: String, telemetryConfig: Map<String, Map<String, Any?>>) {
        if (!isEnabled()) return
        val pixelConfig = telemetryConfig[name] ?: return
        if (telemetryMap.containsKey(name)) return

        val state = pixelConfig["state"] as? String
        if (state != "enabled") return

        val entry = TelemetryEntry.fromConfig(name, pixelConfig, System.currentTimeMillis()) ?: return

        telemetryMap[name] = entry
        store.saveTelemetryState(entry.toPersistedState())
        scheduleFireTelemetry(entry.periodEndMs, name)
    }

    private fun deregisterTelemetry(name: String) {
        if (!telemetryMap.containsKey(name)) return
        scheduledRunnables[name]?.let { handler.removeCallbacks(it) }
        scheduledRunnables.remove(name)
        telemetryMap.remove(name)
        store.removeTelemetryState(name)
    }

    private fun onDisable() {
        for ((name, runnable) in scheduledRunnables) {
            handler.removeCallbacks(runnable)
        }
        scheduledRunnables.clear()

        for (name in telemetryMap.keys.toList()) {
            deregisterTelemetry(name)
        }
    }

    // MARK: - Timer Scheduling

    private fun scheduleFireTelemetry(fireTimeMs: Long, telemetryName: String) {
        if (scheduledRunnables.containsKey(telemetryName)) return
        if (!telemetryMap.containsKey(telemetryName)) return

        val now = System.currentTimeMillis()
        val delay = maxOf(fireTimeMs - now, 0L)

        val runnable = Runnable { fireTelemetry(telemetryName) }
        scheduledRunnables[telemetryName] = runnable

        if (delay <= 0) {
            fireTelemetry(telemetryName)
        } else {
            handler.postDelayed(runnable, delay)
        }
    }

    private fun fireTelemetry(telemetryName: String) {
        if (!isEnabled()) return
        val entry = telemetryMap[telemetryName] ?: return

        scheduledRunnables[telemetryName]?.let { handler.removeCallbacks(it) }
        scheduledRunnables.remove(telemetryName)

        // Fire the pixel
        val pixelData = entry.buildPixel()
        val attributionPeriod = TelemetryEntry.calculateAttributionPeriod(entry.periodStartMs, entry.periodSeconds)
        val additionalParams = mapOf("attributionPeriod" to attributionPeriod.toString())

        if (pixelData.isNotEmpty()) {
            val allParams = pixelData + additionalParams
            pixel.fire(pixelName = entry.name, parameters = allParams)
        }

        // Re-register with latest config
        deregisterTelemetry(telemetryName)
        currentTelemetryConfig?.let { config ->
            registerTelemetry(telemetryName, config)
        }
    }

    // MARK: - Event Handling

    override fun handleWebEvent(type: String, tabId: String) {
        if (!isEnabled()) return

        // Deduplicate per tab per page
        val seenSet = seenEventsPerTab.getOrPut(tabId) { mutableSetOf() }
        if (type in seenSet) return
        seenSet.add(type)

        for (entry in telemetryMap.values) {
            entry.handleEvent(type, System.currentTimeMillis())
            store.saveTelemetryState(entry.toPersistedState())
        }
    }

    override fun resetDeduplication(tabId: String) {
        seenEventsPerTab.remove(tabId)
    }

    // MARK: - Persistence

    private fun restorePersistedState() {
        val savedStates = store.loadAllTelemetryStates()
        val now = System.currentTimeMillis()

        for (state in savedStates) {
            val entry = TelemetryEntry.fromPersistedState(state)
            telemetryMap[state.name] = entry
            scheduleFireTelemetry(state.periodEndMs, state.name)
        }
    }
}
