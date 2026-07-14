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

package com.duckduckgo.duckchat.store.impl

import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.browsermode.api.BrowserModeDataProvider
import com.duckduckgo.duckchat.api.DuckAiHostProvider
import com.duckduckgo.duckchat.store.impl.handler.DuckAiNativeStorageJsMessageHandler
import com.duckduckgo.duckchat.store.impl.store.DuckAiBridgeChatsDao
import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.js.messaging.api.JsMessageHandler
import dagger.Lazy
import org.json.JSONObject
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

/**
 * Verifies the per-mode routing of the JS write/read bridge: the handler is bound per [BrowserMode]
 * (injected at construction, frozen for the activity's lifetime) and that mode selects which storage it writes to.
 */
class DuckAiNativeStorageJsMessageHandlerModeTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val regularChats: DuckAiBridgeChatsDao = mock()
    private val fireChats: DuckAiBridgeChatsDao = mock()
    private val hostProvider: DuckAiHostProvider = mock<DuckAiHostProvider>().also { whenever(it.getHost()).thenReturn("duck.ai") }
    private lateinit var provider: BrowserModeDataProvider<DuckAiBridgeStorage>

    @Before
    fun setup() {
        val regularDir = tempFolder.newFolder("regular")
        val fireDir = tempFolder.newFolder("fire")
        val regularStorage = RealDuckAiBridgeStorage(
            settings = mock(),
            chats = regularChats,
            fileMeta = mock(),
            filesDirLazy = Lazy { regularDir },
        )
        val fireStorage = RealDuckAiBridgeStorage(
            settings = mock(),
            chats = fireChats,
            fileMeta = mock(),
            filesDirLazy = Lazy { fireDir },
        )
        provider = mock<BrowserModeDataProvider<DuckAiBridgeStorage>>().also {
            whenever(it.forMode(BrowserMode.REGULAR)).thenReturn(regularStorage)
            whenever(it.forMode(BrowserMode.FIRE)).thenReturn(fireStorage)
        }
    }

    @Test
    fun `putChat in FIRE mode writes only to the fire store`() {
        handlerForMode(BrowserMode.FIRE).process(putChat("c1"), mock(), null)

        verify(fireChats).upsert(argThat { chatId == "c1" })
        verifyNoInteractions(regularChats)
    }

    @Test
    fun `putChat in REGULAR mode writes only to the regular store`() {
        handlerForMode(BrowserMode.REGULAR).process(putChat("c1"), mock(), null)

        verify(regularChats).upsert(argThat { chatId == "c1" })
        verifyNoInteractions(fireChats)
    }

    private fun handlerForMode(mode: BrowserMode): JsMessageHandler =
        DuckAiNativeStorageJsMessageHandler(provider, hostProvider, DuckAiMigrationPrefs(mock()), mock(), mode)
            .getJsMessageHandler()

    private fun putChat(chatId: String): JsMessage =
        JsMessage(
            context = "contentScopeScripts",
            featureName = "duckAiNativeStorage",
            method = "putChat",
            params = JSONObject("""{"chatId":"$chatId","data":{"chatId":"$chatId"}}"""),
            id = null,
        )
}
