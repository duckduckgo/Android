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

package com.duckduckgo.contentscopescripts.impl.features.messagebridge.store

import com.duckduckgo.common.utils.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

interface MessageBridgeRepository {
    fun updateAll(
        messageBridgeEntity: MessageBridgeEntity,
    )
    var messageBridgeEntity: MessageBridgeEntity
}

class RealMessageBridgeRepository(
    val database: MessageBridgeDatabase,
    val coroutineScope: CoroutineScope,
    val dispatcherProvider: DispatcherProvider,
) : MessageBridgeRepository {

    private val messageBridgeDao: MessageBridgeDao = database.messageBridgeDao()
    override var messageBridgeEntity = MessageBridgeEntity(json = EMPTY_JSON)

    init {
        coroutineScope.launch(dispatcherProvider.io()) {
            loadToMemory()
        }
    }

    override fun updateAll(messageBridgeEntity: MessageBridgeEntity) {
        messageBridgeDao.updateAll(messageBridgeEntity)
        loadToMemory()
    }

    private fun loadToMemory() {
        messageBridgeEntity =
            messageBridgeDao.get() ?: MessageBridgeEntity(json = EMPTY_JSON)
    }

    companion object {
        const val EMPTY_JSON = "{}"
    }
}
