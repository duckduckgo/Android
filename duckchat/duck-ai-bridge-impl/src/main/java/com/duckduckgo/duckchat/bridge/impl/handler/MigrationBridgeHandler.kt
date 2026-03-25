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

package com.duckduckgo.duckchat.bridge.impl.handler

import androidx.webkit.JavaScriptReplyProxy
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.bridge.impl.DuckAiBridgeHandler
import com.duckduckgo.duckchat.bridge.impl.store.DuckAiBridgeSettingEntity
import com.duckduckgo.duckchat.bridge.impl.store.DuckAiBridgeSettingsDao
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Exposes migration status to duck.ai JS as `window.MigrationBridge`.
 *
 * JS → Native: `window.MigrationBridge.postMessage(JSON.stringify({action: "isDone"}))`
 * Native → JS: `replyProxy.postMessage(WebMessageCompat('{"action":"isDone","value":true/false}'))`
 *
 * JS → Native (fire-and-forget): `window.MigrationBridge.postMessage(JSON.stringify({action: "markDone"}))`
 *
 * Uses a reserved key in [DuckAiBridgeSettingsDao] so migration state survives process restarts.
 * Business logic is in `internal` functions so tests can call them directly.
 */
@ContributesMultibinding(AppScope::class)
class MigrationBridgeHandler @Inject constructor(
    private val dao: DuckAiBridgeSettingsDao,
    @AppCoroutineScope private val appScope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
) : DuckAiBridgeHandler {

    override val bridgeName = "MigrationBridge"

    override fun onMessage(message: String, replyProxy: JavaScriptReplyProxy) {
        val json = try { JSONObject(message) } catch (e: Exception) { return }
        appScope.launch(dispatchers.io()) {
            when (json.optString("action")) {
                "isDone" -> {
                    val result = JSONObject().put("action", "isDone").put("value", handleIsDone())
                    replyProxy.postMessage(result.toString())
                }
                "markDone" -> handleMarkDone()
            }
        }
    }

    internal fun handleIsDone(): Boolean = dao.get(MIGRATION_KEY) != null

    internal fun handleMarkDone() {
        dao.upsert(DuckAiBridgeSettingEntity(key = MIGRATION_KEY, value = "true"))
    }

    companion object {
        internal const val MIGRATION_KEY = "__duckAiMigrationDone"
    }
}
