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

package com.duckduckgo.duckchat.bridge.impl

import androidx.webkit.JavaScriptReplyProxy
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.bridge.impl.handler.MigrationBridgeHandler
import com.duckduckgo.duckchat.bridge.impl.store.DuckAiBridgeSettingEntity
import com.duckduckgo.duckchat.bridge.impl.store.DuckAiBridgeSettingsDao
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class MigrationBridgeHandlerTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    private val dao: DuckAiBridgeSettingsDao = mock()
    private val handler by lazy {
        MigrationBridgeHandler(dao, coroutineTestRule.testScope, coroutineTestRule.testDispatcherProvider)
    }

    // Business logic tests (unchanged from before)

    @Test
    fun `isDone returns false by default`() {
        whenever(dao.get(MigrationBridgeHandler.MIGRATION_KEY)).thenReturn(null)
        assertFalse(handler.handleIsDone())
    }

    @Test
    fun `markDone calls dao upsert with reserved key`() {
        handler.handleMarkDone()
        verify(dao).upsert(argThat { key == MigrationBridgeHandler.MIGRATION_KEY && value == "true" })
    }

    @Test
    fun `isDone returns true after markDone round-trip via mock`() {
        handler.handleMarkDone()
        whenever(dao.get(MigrationBridgeHandler.MIGRATION_KEY)).thenReturn(
            DuckAiBridgeSettingEntity(key = MigrationBridgeHandler.MIGRATION_KEY, value = "true"),
        )
        assertTrue(handler.handleIsDone())
    }

    // onMessage dispatch tests

    @Test
    fun `isDone action sends reply with value=false when not done`() {
        whenever(dao.get(MigrationBridgeHandler.MIGRATION_KEY)).thenReturn(null)
        val replyProxy: JavaScriptReplyProxy = mock()

        handler.onMessage("""{"action":"isDone"}""", replyProxy)

        verify(replyProxy).postMessage(
            argThat<String> {
                val json = JSONObject(this)
                json.getString("action") == "isDone" && !json.getBoolean("value")
            },
        )
    }

    @Test
    fun `isDone action sends reply with value=true when done`() {
        whenever(dao.get(MigrationBridgeHandler.MIGRATION_KEY)).thenReturn(
            DuckAiBridgeSettingEntity(key = MigrationBridgeHandler.MIGRATION_KEY, value = "true"),
        )
        val replyProxy: JavaScriptReplyProxy = mock()

        handler.onMessage("""{"action":"isDone"}""", replyProxy)

        verify(replyProxy).postMessage(
            argThat<String> {
                val json = JSONObject(this)
                json.getString("action") == "isDone" && json.getBoolean("value")
            },
        )
    }

    @Test
    fun `markDone action stores done flag and sends no reply`() {
        val replyProxy: JavaScriptReplyProxy = mock()

        handler.onMessage("""{"action":"markDone"}""", replyProxy)

        verify(dao).upsert(argThat { key == MigrationBridgeHandler.MIGRATION_KEY && value == "true" })
        verifyNoInteractions(replyProxy)
    }

    @Test
    fun `unknown action sends no reply`() {
        val replyProxy: JavaScriptReplyProxy = mock()

        handler.onMessage("""{"action":"unknown"}""", replyProxy)

        verifyNoInteractions(replyProxy)
    }

    @Test
    fun `malformed JSON sends no reply`() {
        val replyProxy: JavaScriptReplyProxy = mock()

        handler.onMessage("not json", replyProxy)

        verifyNoInteractions(replyProxy)
    }
}
