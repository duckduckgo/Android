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
import com.duckduckgo.duckchat.bridge.impl.handler.SettingsBridgeHandler
import com.duckduckgo.duckchat.bridge.impl.store.DuckAiBridgeSettingEntity
import com.duckduckgo.duckchat.bridge.impl.store.DuckAiBridgeSettingsDao
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class SettingsBridgeHandlerTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    private val dao: DuckAiBridgeSettingsDao = mock()
    private val handler by lazy {
        SettingsBridgeHandler(dao, coroutineTestRule.testScope, coroutineTestRule.testDispatcherProvider)
    }

    @Test
    fun `getSetting returns JSON string literal for existing key`() {
        whenever(dao.get("theme")).thenReturn(DuckAiBridgeSettingEntity(key = "theme", value = "dark"))
        assertEquals("\"dark\"", handler.handleGetOne("theme"))
    }

    @Test
    fun `getSetting returns null for missing key`() {
        whenever(dao.get("theme")).thenReturn(null)
        assertNull(handler.handleGetOne("theme"))
    }

    @Test
    fun `getSetting returns null for blank key`() {
        assertNull(handler.handleGetOne(""))
        verifyNoInteractions(dao)
    }

    @Test
    fun `getAllSettings returns JSON object of all pairs`() {
        whenever(dao.getAll()).thenReturn(
            listOf(
                DuckAiBridgeSettingEntity(key = "theme", value = "dark"),
                DuckAiBridgeSettingEntity(key = "lang", value = "en"),
            ),
        )
        val result = JSONObject(handler.handleGetAll())
        assertEquals("dark", result.getString("theme"))
        assertEquals("en", result.getString("lang"))
    }

    @Test
    fun `putSetting calls dao upsert with parsed value`() {
        handler.handlePut("theme", "\"dark\"")
        verify(dao).upsert(argThat { key == "theme" && value == "dark" })
    }

    @Test
    fun `putSetting with blank key does nothing`() {
        handler.handlePut("", "\"dark\"")
        verifyNoInteractions(dao)
    }

    @Test
    fun `putSetting with non-JSON-string body does nothing`() {
        handler.handlePut("theme", "dark") // missing quotes — not a JSON string literal
        verifyNoInteractions(dao)
    }

    @Test
    fun `replaceAllSettings calls dao replaceAll`() {
        handler.handleReplaceAll("""{"theme":"dark","lang":"en"}""")
        verify(dao).replaceAll(argThat { size == 2 })
    }

    @Test
    fun `replaceAllSettings with invalid json does nothing`() {
        handler.handleReplaceAll("not-json")
        verifyNoInteractions(dao)
    }

    @Test
    fun `deleteSetting calls dao delete`() {
        handler.handleDelete("theme")
        verify(dao).delete("theme")
    }

    @Test
    fun `deleteSetting with blank key does nothing`() {
        handler.handleDelete("")
        verifyNoInteractions(dao)
    }

    @Test
    fun `deleteAllSettings calls dao deleteAll`() {
        handler.handleDeleteAll()
        verify(dao).deleteAll()
    }

    // --- New onMessage dispatch tests ---

    @Test
    fun `getSetting action replies with value when key exists`() {
        whenever(dao.get("theme")).thenReturn(DuckAiBridgeSettingEntity(key = "theme", value = "dark"))
        val replyProxy: JavaScriptReplyProxy = mock()

        handler.onMessage("""{"action":"getSetting","key":"theme"}""", replyProxy)

        verify(replyProxy).postMessage(
            argThat<String> {
                val json = JSONObject(this)
                json.getString("action") == "getSetting" && json.getString("value") == "\"dark\""
            },
        )
    }

    @Test
    fun `getSetting action replies with null value when key missing`() {
        whenever(dao.get("theme")).thenReturn(null)
        val replyProxy: JavaScriptReplyProxy = mock()

        handler.onMessage("""{"action":"getSetting","key":"theme"}""", replyProxy)

        verify(replyProxy).postMessage(
            argThat<String> {
                val json = JSONObject(this)
                json.getString("action") == "getSetting" && json.isNull("value")
            },
        )
    }

    @Test
    fun `getAllSettings action replies with json object containing all settings`() {
        whenever(dao.getAll()).thenReturn(listOf(DuckAiBridgeSettingEntity("k", "v")))
        val replyProxy: JavaScriptReplyProxy = mock()

        handler.onMessage("""{"action":"getAllSettings"}""", replyProxy)

        verify(replyProxy).postMessage(
            argThat<String> {
                val json = JSONObject(this)
                json.getString("action") == "getAllSettings" &&
                    JSONObject(json.getString("value")).getString("k") == "v"
            },
        )
    }

    @Test
    fun `putSetting action stores value and sends no reply`() {
        val replyProxy: JavaScriptReplyProxy = mock()

        handler.onMessage("""{"action":"putSetting","key":"theme","value":"\"dark\""}""", replyProxy)

        verify(dao).upsert(argThat { key == "theme" && value == "dark" })
        verifyNoInteractions(replyProxy)
    }

    @Test
    fun `replaceAllSettings action stores all values and sends no reply`() {
        val replyProxy: JavaScriptReplyProxy = mock()

        handler.onMessage("""{"action":"replaceAllSettings","data":{"k":"v"}}""", replyProxy)

        verify(dao).replaceAll(argThat { size == 1 && first().key == "k" })
        verifyNoInteractions(replyProxy)
    }

    @Test
    fun `deleteSetting action deletes key and sends no reply`() {
        val replyProxy: JavaScriptReplyProxy = mock()

        handler.onMessage("""{"action":"deleteSetting","key":"theme"}""", replyProxy)

        verify(dao).delete("theme")
        verifyNoInteractions(replyProxy)
    }

    @Test
    fun `deleteAllSettings action clears all and sends no reply`() {
        val replyProxy: JavaScriptReplyProxy = mock()

        handler.onMessage("""{"action":"deleteAllSettings"}""", replyProxy)

        verify(dao).deleteAll()
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
