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
import com.duckduckgo.duckchat.store.impl.store.DuckAiBridgeSettingEntity
import com.duckduckgo.duckchat.store.impl.store.DuckAiBridgeSettingsDao
import com.duckduckgo.js.messaging.api.JsCallbackData
import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.js.messaging.api.JsMessageHandler
import com.duckduckgo.js.messaging.api.JsMessaging
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

/**
 * Verifies the per-mode routing of the JS write/read bridge: the handler is bound per [BrowserMode]
 * (injected at construction, frozen for the activity's lifetime) and that mode selects which storage it writes to.
 */
class DuckAiNativeStorageJsMessageHandlerModeTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val regularSettings: DuckAiBridgeSettingsDao = mock()
    private val fireSettings: DuckAiBridgeSettingsDao = mock()
    private val regularUserDefault = DuckAiBridgeSettingEntity("defaultDuckaiModel", "\"regular-model\"")
    private val fireUserDefault = DuckAiBridgeSettingEntity("defaultDuckaiModel", "\"fire-model\"")
    private val theme = DuckAiBridgeSettingEntity("theme", "dark")
    private val regularChats: DuckAiBridgeChatsDao = mock()
    private val fireChats: DuckAiBridgeChatsDao = mock()
    private val hostProvider: DuckAiHostProvider = mock<DuckAiHostProvider>().also { whenever(it.getHost()).thenReturn("duck.ai") }
    private lateinit var provider: BrowserModeDataProvider<DuckAiBridgeStorage>

    @Before
    fun setup() {
        val regularDir = tempFolder.newFolder("regular")
        val fireDir = tempFolder.newFolder("fire")
        val regularStorage = RealDuckAiBridgeStorage(
            settings = regularSettings,
            chats = regularChats,
            fileMeta = mock(),
            filesDirLazy = lazy { regularDir },
        )
        val fireStorage = RealDuckAiBridgeStorage(
            settings = fireSettings,
            chats = fireChats,
            fileMeta = mock(),
            filesDirLazy = lazy { fireDir },
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

    @Test
    fun `getEntry in Fire returns the regular default even when Fire has its own default`() {
        whenever(regularSettings.get(regularUserDefault.key)).thenReturn(regularUserDefault)
        whenever(fireSettings.get(fireUserDefault.key)).thenReturn(fireUserDefault)

        val result = request(
            handlerForMode(BrowserMode.FIRE, userDefaultEnabled = true),
            "getEntry",
            JSONObject().put("key", regularUserDefault.key),
        )

        assertEquals(regularUserDefault.value, result.getString("value"))
        verifyNoInteractions(fireSettings)
    }

    @Test
    fun `getEntry in Fire returns null when the regular default is unset`() {
        whenever(fireSettings.get(fireUserDefault.key)).thenReturn(fireUserDefault)

        val result = request(
            handlerForMode(BrowserMode.FIRE, userDefaultEnabled = true),
            "getEntry",
            JSONObject().put("key", regularUserDefault.key),
        )

        assertTrue(result.has("value") && result.isNull("value"))
        verifyNoInteractions(fireSettings)
    }

    @Test
    fun `getAllEntries in Fire adds the regular default when Fire has none`() {
        whenever(regularSettings.get(regularUserDefault.key)).thenReturn(regularUserDefault)
        whenever(fireSettings.getAll()).thenReturn(listOf(theme))

        val entries = request(handlerForMode(BrowserMode.FIRE, userDefaultEnabled = true), "getAllEntries").getJSONObject("entries")

        assertEquals(regularUserDefault.value, entries.getString(regularUserDefault.key))
        assertEquals(theme.value, entries.getString(theme.key))
        verify(fireSettings).getAll()
        verify(regularSettings).get(regularUserDefault.key)
        verifyNoMoreInteractions(fireSettings, regularSettings)
    }

    @Test
    fun `getAllEntries in Fire preserves its existing default`() {
        whenever(fireSettings.getAll()).thenReturn(listOf(fireUserDefault, theme))

        val entries = request(handlerForMode(BrowserMode.FIRE, userDefaultEnabled = true), "getAllEntries").getJSONObject("entries")

        assertEquals(fireUserDefault.value, entries.getString(fireUserDefault.key))
        assertEquals(theme.value, entries.getString(theme.key))
        verifyNoInteractions(regularSettings)
    }

    @Test
    fun `getAllEntries in Fire omits the default when neither store has one`() {
        whenever(fireSettings.getAll()).thenReturn(listOf(theme))

        val entries = request(handlerForMode(BrowserMode.FIRE, userDefaultEnabled = true), "getAllEntries").getJSONObject("entries")

        assertFalse(entries.has(regularUserDefault.key))
        assertEquals(theme.value, entries.getString(theme.key))
    }

    @Test
    fun `getAllEntries in Fire returns Fire settings when the regular default lookup fails`() {
        whenever(regularSettings.get(regularUserDefault.key)).thenThrow(IllegalStateException())
        whenever(fireSettings.getAll()).thenReturn(listOf(theme))

        val entries = request(handlerForMode(BrowserMode.FIRE, userDefaultEnabled = true), "getAllEntries").getJSONObject("entries")

        assertFalse(entries.has(regularUserDefault.key))
        assertEquals(theme.value, entries.getString(theme.key))
    }

    @Test
    fun `Fire reads stay local when the user default gate is off`() {
        whenever(fireSettings.get(fireUserDefault.key)).thenReturn(fireUserDefault)
        whenever(fireSettings.getAll()).thenReturn(listOf(theme))
        val handler = handlerForMode(BrowserMode.FIRE)
        val entry = request(handler, "getEntry", JSONObject().put("key", fireUserDefault.key))
        val entries = request(handler, "getAllEntries").getJSONObject("entries")

        assertEquals(fireUserDefault.value, entry.getString("value"))
        assertFalse(entries.has(fireUserDefault.key))
        assertEquals(theme.value, entries.getString(theme.key))
        verifyNoInteractions(regularSettings)
    }

    @Test
    fun `Fire rejects writes of the default but still writes other settings`() {
        val handler = handlerForMode(BrowserMode.FIRE, userDefaultEnabled = true)

        putEntry(handler, fireUserDefault)
        putEntry(handler, theme)
        replaceAllEntries(handler, fireUserDefault)
        replaceAllEntries(handler, fireUserDefault, theme)

        verify(fireSettings).upsert(theme)
        verify(fireSettings).replaceAll(listOf(theme))
        verifyNoMoreInteractions(fireSettings)
        verifyNoInteractions(regularSettings)
    }

    @Test
    fun `Fire writes stay local when the user default gate is off`() {
        val handler = handlerForMode(BrowserMode.FIRE)

        putEntry(handler, fireUserDefault)
        replaceAllEntries(handler, fireUserDefault)

        verify(fireSettings).upsert(fireUserDefault)
        verify(fireSettings).replaceAll(listOf(fireUserDefault))
        verifyNoInteractions(regularSettings)
    }

    @Test
    fun `Regular mode writes the default to its own store`() {
        val handler = handlerForMode(BrowserMode.REGULAR, userDefaultEnabled = true)

        putEntry(handler, regularUserDefault)
        replaceAllEntries(handler, regularUserDefault)

        verify(regularSettings).upsert(regularUserDefault)
        verify(regularSettings).replaceAll(listOf(regularUserDefault))
        verifyNoInteractions(fireSettings)
    }

    private fun handlerForMode(mode: BrowserMode, userDefaultEnabled: Boolean = false): JsMessageHandler =
        DuckAiNativeStorageJsMessageHandler(
            storageProvider = provider,
            duckAiHostProvider = hostProvider,
            migrationPrefs = DuckAiMigrationPrefs(mock()),
            pixels = mock(),
            deletionListener = mock(),
            browserMode = mode,
            userDefaultModelSetting = mock {
                on { isUserDefaultEnabled() } doReturn userDefaultEnabled
            },
        ).getJsMessageHandler()

    private fun request(
        handler: JsMessageHandler,
        method: String,
        params: JSONObject = JSONObject(),
    ): JSONObject {
        val jsMessaging: JsMessaging = mock()
        val callback = argumentCaptor<JsCallbackData>()
        handler.process(JsMessage("contentScopeScripts", "duckAiNativeStorage", method, params, "r1"), jsMessaging, null)
        verify(jsMessaging).onResponse(callback.capture())
        return callback.firstValue.params
    }

    private fun putEntry(handler: JsMessageHandler, entity: DuckAiBridgeSettingEntity) {
        val params = JSONObject().put("key", entity.key).put("value", entity.value)
        handler.process(JsMessage("contentScopeScripts", "duckAiNativeStorage", "putEntry", params, null), mock(), null)
    }

    private fun replaceAllEntries(handler: JsMessageHandler, vararg entities: DuckAiBridgeSettingEntity) {
        val entries = JSONObject()
        entities.forEach { entries.put(it.key, it.value) }
        val params = JSONObject().put("entries", entries)
        handler.process(JsMessage("contentScopeScripts", "duckAiNativeStorage", "replaceAllEntries", params, null), mock(), null)
    }

    private fun putChat(chatId: String): JsMessage =
        JsMessage(
            context = "contentScopeScripts",
            featureName = "duckAiNativeStorage",
            method = "putChat",
            params = JSONObject("""{"chatId":"$chatId","data":{"chatId":"$chatId"}}"""),
            id = null,
        )
}
