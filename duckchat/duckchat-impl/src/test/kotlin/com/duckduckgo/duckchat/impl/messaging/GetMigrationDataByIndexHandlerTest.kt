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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.duckchat.impl.messaging.fakes.FakeJsMessaging
import com.duckduckgo.js.messaging.api.JsMessage
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GetMigrationDataByIndexHandlerTest {
    private val standaloneStore = InMemoryStandaloneStore()
    private val plugin = GetMigrationDataByIndexHandler(standaloneStore)
    private val handler = plugin.getJsMessageHandler()
    private lateinit var messaging: FakeJsMessaging

    @Before
    fun setup() {
        messaging = FakeJsMessaging()
    }

    @After
    fun tearDown() {
        standaloneStore.clearMigrationItems()
    }

    @Test
    fun `when get migration data by index, then return item`() {
        standaloneStore.storeMigrationItem("Blob1")

        val message = JsMessage(
            context = "context",
            featureName = handler.featureName,
            method = handler.methods.first(),
            id = "123",
            params = JSONObject(mapOf("index" to 0)),
        )

        handler.process(message, messaging, null)

        val data = messaging.getLastResponse()

        assertEquals(handler.featureName, data!!.featureName)
        assertEquals(handler.methods.first(), data.method)
        assertEquals("123", data.id)
        assertTrue(data.params.has("ok"))
        assertTrue(data.params.getBoolean("ok"))
        assertTrue(data.params.has("serializedMigrationFile"))
        assertEquals("Blob1", data.params.getString("serializedMigrationFile"))
    }

    @Test
    fun `when get migration data by index and index is invalid, return empty payload`() {
        standaloneStore.storeMigrationItem("Blob1")

        val message = JsMessage(
            context = "context",
            featureName = handler.featureName,
            method = handler.methods.first(),
            id = "123",
            params = JSONObject(mapOf("index" to 1)),
        )

        handler.process(message, messaging, null)

        val data = messaging.getLastResponse()

        assertEquals(handler.featureName, data!!.featureName)
        assertEquals(handler.methods.first(), data.method)
        assertEquals("123", data.id)
        assertTrue(data.params.has("ok"))
        assertFalse(data.params.getBoolean("ok"))
        assertFalse(data.params.has("serializedMigrationFile"))
    }

    @Test
    fun `when get migration data by index and no id then do nothing`() {
        val message = JsMessage(
            context = "context",
            featureName = handler.featureName,
            method = handler.methods.first(),
            id = "",
            params = JSONObject(),
        )

        handler.process(message, messaging, null)

        val data = messaging.getLastResponse()
        assertNull(data)
    }

    @Test
    fun `only allow duckduckgo dot com domains`() {
        val domains = handler.allowedDomains
        assertTrue(domains.size == 2)
        assertTrue(domains[0] == "duckduckgo.com")
        assertTrue(domains[1] == "duck.ai")
    }

    @Test
    fun `feature name is ai chat`() {
        assertTrue(handler.featureName == "aiChat")
    }

    @Test
    fun `only contains valid methods`() {
        val methods = handler.methods
        assertTrue(methods.size == 1)
        assertTrue(methods[0] == "getMigrationDataByIndex")
    }
}
