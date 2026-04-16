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

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.duckchat.api.DuckAiHostProvider
import com.duckduckgo.duckchat.store.impl.handler.DuckAiNativeStorageJsMessageHandler
import com.duckduckgo.duckchat.store.impl.store.DuckAiBridgeDatabase
import com.duckduckgo.duckchat.store.impl.store.DuckAiBridgeFileMetaDao
import com.duckduckgo.duckchat.store.impl.store.DuckAiBridgeSettingsDao
import com.duckduckgo.js.messaging.api.JsCallbackData
import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.js.messaging.api.JsMessaging
import dagger.Lazy
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File

@RunWith(AndroidJUnit4::class)
class GetChatIntegrationTest {

    private lateinit var db: DuckAiBridgeDatabase
    private lateinit var handler: com.duckduckgo.js.messaging.api.JsMessageHandler
    private val jsMessaging: JsMessaging = mock()

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            DuckAiBridgeDatabase::class.java,
        ).allowMainThreadQueries().build()

        val hostProvider = mock<DuckAiHostProvider>().also { whenever(it.getHost()).thenReturn("duck.ai") }
        val handlerPlugin = DuckAiNativeStorageJsMessageHandler(
            settingsDao = mock<DuckAiBridgeSettingsDao>(),
            chatsDao = db.chatsDao(),
            fileMetaDao = mock<DuckAiBridgeFileMetaDao>(),
            filesDirLazy = Lazy { File("") },
            duckAiHostProvider = hostProvider,
            migrationPrefs = DuckAiMigrationPrefs(mock()),
            pixels = mock(),
        )
        handler = handlerPlugin.getJsMessageHandler()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `getChat returns correctly assembled chat object stored via putChat`() {
        handler.process(
            jsMessage("putChat", """{"chatId":"chat-1","data":${sampleChatData("chat-1")}}"""),
            jsMessaging,
            null,
        )

        val captor = argumentCaptor<JsCallbackData>()
        handler.process(jsMessage("getChat", """{"chatId":"chat-1"}""", id = "r1"), jsMessaging, null)
        verify(jsMessaging).onResponse(captor.capture())

        val expected = TestChatData(
            chatId = "chat-1",
            model = "claude-3",
            messages = listOf(
                TestMessage(role = "user", content = "hello"),
                TestMessage(role = "assistant", content = "hi there"),
            ),
        )

        assertEquals(expected, captor.firstValue.params.getJSONObject("chat").toTestChat())
    }

    @Test
    fun `getChat response has exactly one top-level key chat`() {
        handler.process(
            jsMessage("putChat", """{"chatId":"chat-1","data":${sampleChatData("chat-1")}}"""),
            jsMessaging,
            null,
        )

        val captor = argumentCaptor<JsCallbackData>()
        handler.process(jsMessage("getChat", """{"chatId":"chat-1"}""", id = "r1"), jsMessaging, null)
        verify(jsMessaging).onResponse(captor.capture())

        val params = captor.firstValue.params
        assertEquals(1, params.length())
        assertFalse(params.isNull("chat"))
    }

    @Test
    fun `getChat returns null chat for unknown chatId`() {
        val captor = argumentCaptor<JsCallbackData>()
        handler.process(jsMessage("getChat", """{"chatId":"unknown"}""", id = "r1"), jsMessaging, null)
        verify(jsMessaging).onResponse(captor.capture())

        val params = captor.firstValue.params
        assertEquals(1, params.length())
        assert(params.isNull("chat"))
    }

    // --- helpers ---

    private fun sampleChatData(chatId: String): String =
        """{"chatId":"$chatId","model":"claude-3","messages":[{"role":"user","content":"hello"},{"role":"assistant","content":"hi there"}]}"""

    private fun jsMessage(method: String, paramsJson: String, id: String? = null): JsMessage =
        JsMessage("contentScopeScripts", "duckAiNativeStorage", method, JSONObject(paramsJson), id)

    private fun JSONObject.toTestChat(): TestChatData {
        val messagesArray = getJSONArray("messages")
        val messages = (0 until messagesArray.length()).map { i ->
            val msg = messagesArray.getJSONObject(i)
            TestMessage(role = msg.getString("role"), content = msg.getString("content"))
        }
        return TestChatData(
            chatId = getString("chatId"),
            model = getString("model"),
            messages = messages,
        )
    }

    data class TestChatData(
        val chatId: String,
        val model: String,
        val messages: List<TestMessage>,
    )

    data class TestMessage(
        val role: String,
        val content: String,
    )
}
