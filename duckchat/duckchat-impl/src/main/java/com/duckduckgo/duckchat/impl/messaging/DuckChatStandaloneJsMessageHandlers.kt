/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.duckchat.impl.messaging

import com.duckduckgo.common.utils.AppUrl
import com.duckduckgo.contentscopescripts.api.ContentScopeJsMessageHandlersPlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.impl.DuckChatConstants.HOST_DUCK_AI
import com.duckduckgo.duckchat.impl.DuckChatConstants.StandaloneConstants.COUNT
import com.duckduckgo.duckchat.impl.DuckChatConstants.StandaloneConstants.INDEX
import com.duckduckgo.duckchat.impl.DuckChatConstants.StandaloneConstants.OK
import com.duckduckgo.duckchat.impl.DuckChatConstants.StandaloneConstants.REASON
import com.duckduckgo.duckchat.impl.DuckChatConstants.StandaloneConstants.SERIALIZED_MIGRATION_FILE
import com.duckduckgo.js.messaging.api.JsCallbackData
import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.duckduckgo.js.messaging.api.JsMessageHandler
import com.duckduckgo.js.messaging.api.JsMessaging
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import org.json.JSONObject
import javax.inject.Inject

interface StandaloneDuckChatStore {
    fun storeMigrationItem(item: String)
    fun getMigrationItemByIndex(index: Int): String?
    fun getMigrationItemCount(): Int
    fun clearMigrationItems()
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class InMemoryStandaloneStore @Inject constructor() : StandaloneDuckChatStore {
    private val migrationItems = mutableListOf<String>()

    override fun storeMigrationItem(item: String) {
        migrationItems.add(item)
    }

    override fun getMigrationItemByIndex(index: Int): String? {
        return migrationItems.getOrNull(index)
    }

    override fun getMigrationItemCount(): Int = migrationItems.size

    override fun clearMigrationItems() {
        migrationItems.clear()
    }
}

@ContributesMultibinding(AppScope::class)
class StoreMigrationDataHandler @Inject constructor(
    private val standaloneDuckChatStore: StandaloneDuckChatStore,
) : ContentScopeJsMessageHandlersPlugin {
    override fun getJsMessageHandler(): JsMessageHandler =
        object : JsMessageHandler {
            override fun process(
                jsMessage: JsMessage,
                jsMessaging: JsMessaging,
                jsMessageCallback: JsMessageCallback?,
            ) {
                if (jsMessage.id.isNullOrEmpty()) return
                val item = jsMessage.params.optString(SERIALIZED_MIGRATION_FILE)

                val result = if (!item.isNullOrEmpty() && item != JSONObject.NULL.toString()) {
                    standaloneDuckChatStore.storeMigrationItem(item)
                    true
                } else {
                    false
                }

                val payload = JSONObject().apply {
                    put(OK, result)
                    if (!result) put(REASON, "Missing or invalid serializedMigrationFile")
                }

                jsMessaging.onResponse(JsCallbackData(payload, featureName, jsMessage.method, jsMessage.id!!))
            }

            override val allowedDomains: List<String> =
                listOf(
                    AppUrl.Url.HOST,
                    HOST_DUCK_AI,
                )

            override val featureName: String = "aiChat"
            override val methods: List<String> = listOf("storeMigrationData")
        }
}

@ContributesMultibinding(AppScope::class)
class GetMigrationInfoHandler @Inject constructor(
    private val standaloneDuckChatStore: StandaloneDuckChatStore,
) : ContentScopeJsMessageHandlersPlugin {
    override fun getJsMessageHandler(): JsMessageHandler =
        object : JsMessageHandler {
            override fun process(
                jsMessage: JsMessage,
                jsMessaging: JsMessaging,
                jsMessageCallback: JsMessageCallback?,
            ) {
                if (jsMessage.id.isNullOrEmpty()) return

                val count = standaloneDuckChatStore.getMigrationItemCount()
                val payload = JSONObject().apply {
                    put(OK, true)
                    put(COUNT, count)
                }

                jsMessaging.onResponse(JsCallbackData(payload, featureName, jsMessage.method, jsMessage.id!!))
            }

            override val allowedDomains: List<String> =
                listOf(
                    AppUrl.Url.HOST,
                    HOST_DUCK_AI,
                )

            override val featureName: String = "aiChat"
            override val methods: List<String> = listOf("getMigrationInfo")
        }
}

@ContributesMultibinding(AppScope::class)
class GetMigrationDataByIndexHandler @Inject constructor(
    private val standaloneDuckChatStore: StandaloneDuckChatStore,
) : ContentScopeJsMessageHandlersPlugin {
    override fun getJsMessageHandler(): JsMessageHandler =
        object : JsMessageHandler {
            override fun process(
                jsMessage: JsMessage,
                jsMessaging: JsMessaging,
                jsMessageCallback: JsMessageCallback?,
            ) {
                if (jsMessage.id.isNullOrEmpty()) return
                val index = jsMessage.params.optInt(INDEX, -1)
                val value = standaloneDuckChatStore.getMigrationItemByIndex(index)

                val payload = JSONObject().apply {
                    if (value == null) {
                        put(OK, false)
                        put(REASON, "nothing at index: $index")
                    } else {
                        put(OK, true)
                        put(SERIALIZED_MIGRATION_FILE, value)
                    }
                }
                jsMessaging.onResponse(JsCallbackData(payload, featureName, jsMessage.method, jsMessage.id!!))
            }

            override val allowedDomains: List<String> =
                listOf(
                    AppUrl.Url.HOST,
                    HOST_DUCK_AI,
                )

            override val featureName: String = "aiChat"
            override val methods: List<String> = listOf("getMigrationDataByIndex")
        }
}

@ContributesMultibinding(AppScope::class)
class ClearMigrationDataHandler @Inject constructor(
    private val standaloneDuckChatStore: StandaloneDuckChatStore,
) : ContentScopeJsMessageHandlersPlugin {
    override fun getJsMessageHandler(): JsMessageHandler =
        object : JsMessageHandler {
            override fun process(
                jsMessage: JsMessage,
                jsMessaging: JsMessaging,
                jsMessageCallback: JsMessageCallback?,
            ) {
                if (jsMessage.id.isNullOrEmpty()) return
                standaloneDuckChatStore.clearMigrationItems()
                val jsonPayload = JSONObject().apply { put(OK, true) }
                jsMessaging.onResponse(JsCallbackData(jsonPayload, featureName, jsMessage.method, jsMessage.id!!))
            }

            override val allowedDomains: List<String> =
                listOf(
                    AppUrl.Url.HOST,
                    HOST_DUCK_AI,
                )

            override val featureName: String = "aiChat"
            override val methods: List<String> = listOf("clearMigrationData")
        }
}
