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

package com.duckduckgo.duckchat.impl.ui.inputscreen.suggestions.reader

import android.annotation.SuppressLint
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.impl.feature.DuckAiChatHistoryFeature
import com.duckduckgo.duckchat.impl.inputscreen.ui.suggestions.ChatSuggestion
import com.duckduckgo.duckchat.impl.inputscreen.ui.suggestions.reader.ChatSuggestionsJsMessaging
import com.duckduckgo.duckchat.impl.inputscreen.ui.suggestions.reader.RealChatSuggestionsReader
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.FeatureException
import com.duckduckgo.feature.toggles.api.Toggle.State
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import java.time.LocalDateTime
import java.time.ZoneId

@SuppressLint("DenyListedApi")
@RunWith(AndroidJUnit4::class)
class RealChatSuggestionsReaderTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val context: Context = mock()
    private val appBuildConfig: AppBuildConfig = mock()
    private val messaging: ChatSuggestionsJsMessaging = mock()
    private val duckAiChatHistoryFeature = FakeFeatureToggleFactory.create(DuckAiChatHistoryFeature::class.java)

    private lateinit var reader: RealChatSuggestionsReader

    @Before
    fun setup() {
        reader = RealChatSuggestionsReader(
            context = context,
            dispatchers = coroutineRule.testDispatcherProvider,
            appBuildConfig = appBuildConfig,
            messaging = messaging,
            duckAiChatHistoryFeature = duckAiChatHistoryFeature,
        )
    }

    // region parseResponse

    @Test
    fun `when parseResponse called with success true then returns DomainResult`() {
        val response = JSONObject().apply {
            put("success", true)
            put(
                "pinnedChats",
                JSONArray().apply {
                    put(
                        JSONObject().apply {
                            put("chatId", "pinned-1")
                            put("title", "Pinned Chat")
                            put("lastEdit", "2026-01-15T10:30:00Z")
                        },
                    )
                },
            )
            put(
                "chats",
                JSONArray().apply {
                    put(
                        JSONObject().apply {
                            put("chatId", "recent-1")
                            put("title", "Recent Chat")
                            put("lastEdit", "2026-01-14T08:00:00Z")
                        },
                    )
                },
            )
        }

        val result = reader.parseResponse(response)

        assertEquals(1, result!!.pinnedChats.size)
        assertEquals("pinned-1", result.pinnedChats[0].chatId)
        assertTrue(result.pinnedChats[0].pinned)
        assertEquals(1, result.recentChats.size)
        assertEquals("recent-1", result.recentChats[0].chatId)
        assertEquals(false, result.recentChats[0].pinned)
    }

    @Test
    fun `when parseResponse called with success false then returns null`() {
        val response = JSONObject().apply {
            put("success", false)
        }

        assertNull(reader.parseResponse(response))
    }

    @Test
    fun `when parseResponse called without success field then returns null`() {
        val response = JSONObject()

        assertNull(reader.parseResponse(response))
    }

    @Test
    fun `when parseResponse called with empty arrays then returns empty lists`() {
        val response = JSONObject().apply {
            put("success", true)
            put("pinnedChats", JSONArray())
            put("chats", JSONArray())
        }

        val result = reader.parseResponse(response)

        assertTrue(result!!.pinnedChats.isEmpty())
        assertTrue(result.recentChats.isEmpty())
    }

    @Test
    fun `when parseResponse called with missing arrays then returns empty lists`() {
        val response = JSONObject().apply {
            put("success", true)
        }

        val result = reader.parseResponse(response)

        assertTrue(result!!.pinnedChats.isEmpty())
        assertTrue(result.recentChats.isEmpty())
    }

    // endregion

    // region parseChats

    @Test
    fun `when parseChats called with null array then returns empty list`() {
        val result = reader.parseChats(null, pinned = false)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `when parseChats called with valid chat then returns ChatSuggestion`() {
        val jsonArray = JSONArray().apply {
            put(
                JSONObject().apply {
                    put("chatId", "chat-1")
                    put("title", "My Chat")
                    put("lastEdit", "2026-01-15T10:30:00Z")
                },
            )
        }

        val result = reader.parseChats(jsonArray, pinned = true)

        assertEquals(1, result.size)
        assertEquals("chat-1", result[0].chatId)
        assertEquals("My Chat", result[0].title)
        assertTrue(result[0].pinned)
    }

    @Test
    fun `when parseChats called with missing chatId then skips entry`() {
        val jsonArray = JSONArray().apply {
            put(
                JSONObject().apply {
                    put("title", "No ID Chat")
                    put("lastEdit", "2026-01-15T10:30:00Z")
                },
            )
        }

        val result = reader.parseChats(jsonArray, pinned = false)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `when parseChats called with empty chatId then skips entry`() {
        val jsonArray = JSONArray().apply {
            put(
                JSONObject().apply {
                    put("chatId", "")
                    put("title", "Empty ID Chat")
                },
            )
        }

        val result = reader.parseChats(jsonArray, pinned = false)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `when parseChats called with empty title then uses Untitled Chat`() {
        val jsonArray = JSONArray().apply {
            put(
                JSONObject().apply {
                    put("chatId", "chat-1")
                    put("title", "")
                },
            )
        }

        val result = reader.parseChats(jsonArray, pinned = false)

        assertEquals("Untitled Chat", result[0].title)
    }

    @Test
    fun `when parseChats called with missing title then uses Untitled Chat`() {
        val jsonArray = JSONArray().apply {
            put(
                JSONObject().apply {
                    put("chatId", "chat-1")
                },
            )
        }

        val result = reader.parseChats(jsonArray, pinned = false)

        assertEquals("Untitled Chat", result[0].title)
    }

    @Test
    fun `when parseChats called with valid ISO timestamp then parses correctly`() {
        val jsonArray = JSONArray().apply {
            put(
                JSONObject().apply {
                    put("chatId", "chat-1")
                    put("title", "Test")
                    put("lastEdit", "2026-01-15T10:30:00Z")
                },
            )
        }

        val result = reader.parseChats(jsonArray, pinned = false)

        val expectedTime = java.time.Instant.parse("2026-01-15T10:30:00Z")
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()
        assertEquals(expectedTime, result[0].lastEdit)
    }

    @Test
    fun `when parseChats called with invalid timestamp then falls back to now`() {
        val before = LocalDateTime.now()
        val jsonArray = JSONArray().apply {
            put(
                JSONObject().apply {
                    put("chatId", "chat-1")
                    put("title", "Test")
                    put("lastEdit", "not-a-date")
                },
            )
        }

        val result = reader.parseChats(jsonArray, pinned = false)
        val after = LocalDateTime.now()

        assertTrue(!result[0].lastEdit.isBefore(before))
        assertTrue(!result[0].lastEdit.isAfter(after))
    }

    @Test
    fun `when parseChats called with empty lastEdit then falls back to now`() {
        val before = LocalDateTime.now()
        val jsonArray = JSONArray().apply {
            put(
                JSONObject().apply {
                    put("chatId", "chat-1")
                    put("title", "Test")
                    put("lastEdit", "")
                },
            )
        }

        val result = reader.parseChats(jsonArray, pinned = false)
        val after = LocalDateTime.now()

        assertTrue(!result[0].lastEdit.isBefore(before))
        assertTrue(!result[0].lastEdit.isAfter(after))
    }

    @Test
    fun `when parseChats called with multiple entries then returns all valid ones`() {
        val jsonArray = JSONArray().apply {
            put(
                JSONObject().apply {
                    put("chatId", "chat-1")
                    put("title", "First")
                },
            )
            put(
                JSONObject().apply {
                    put("chatId", "")
                    put("title", "Invalid - empty id")
                },
            )
            put(
                JSONObject().apply {
                    put("chatId", "chat-3")
                    put("title", "Third")
                },
            )
        }

        val result = reader.parseChats(jsonArray, pinned = false)

        assertEquals(2, result.size)
        assertEquals("chat-1", result[0].chatId)
        assertEquals("chat-3", result[1].chatId)
    }

    @Test
    fun `when parseChats called then sets pinned flag correctly`() {
        val jsonArray = JSONArray().apply {
            put(
                JSONObject().apply {
                    put("chatId", "chat-1")
                    put("title", "Test")
                },
            )
        }

        val pinnedResult = reader.parseChats(jsonArray, pinned = true)
        val recentResult = reader.parseChats(jsonArray, pinned = false)

        assertTrue(pinnedResult[0].pinned)
        assertEquals(false, recentResult[0].pinned)
    }

    // endregion

    // region mergeSuggestions

    @Test
    fun `when mergeSuggestions called then all sorted by lastEdit descending regardless of pinned`() {
        val now = LocalDateTime.now()
        val pinned = listOf(
            ChatSuggestion("p1", "Pinned Old", now.minusDays(2), pinned = true),
            ChatSuggestion("p2", "Pinned New", now.minusDays(1), pinned = true),
        )
        val recent = listOf(
            ChatSuggestion("r1", "Recent", now, pinned = false),
        )

        val result = reader.mergeSuggestions(pinned, recent)

        assertEquals(3, result.size)
        assertEquals("r1", result[0].chatId)
        assertEquals("p2", result[1].chatId)
        assertEquals("p1", result[2].chatId)
    }

    @Test
    fun `when mergeSuggestions called with more than 10 total then takes only 10`() {
        val now = LocalDateTime.now()
        val pinned = (1..6).map {
            ChatSuggestion("p$it", "Pinned $it", now.minusHours(it.toLong()), pinned = true)
        }
        val recent = (1..6).map {
            ChatSuggestion("r$it", "Recent $it", now.minusHours(it.toLong() + 6), pinned = false)
        }

        val result = reader.mergeSuggestions(pinned, recent)

        assertEquals(10, result.size)
    }

    @Test
    fun `when mergeSuggestions called with empty pinned then returns only recent`() {
        val now = LocalDateTime.now()
        val recent = listOf(
            ChatSuggestion("r1", "Recent 1", now, pinned = false),
            ChatSuggestion("r2", "Recent 2", now.minusDays(1), pinned = false),
        )

        val result = reader.mergeSuggestions(emptyList(), recent)

        assertEquals(2, result.size)
        assertEquals("r1", result[0].chatId)
        assertEquals("r2", result[1].chatId)
    }

    @Test
    fun `when mergeSuggestions called with empty recent then returns only pinned`() {
        val now = LocalDateTime.now()
        val pinned = listOf(
            ChatSuggestion("p1", "Pinned 1", now, pinned = true),
        )

        val result = reader.mergeSuggestions(pinned, emptyList())

        assertEquals(1, result.size)
        assertEquals("p1", result[0].chatId)
    }

    @Test
    fun `when mergeSuggestions called with both empty then returns empty`() {
        val result = reader.mergeSuggestions(emptyList(), emptyList())

        assertTrue(result.isEmpty())
    }

    // endregion

    // region tearDown

    @Test
    fun `when tearDown called multiple times then app does not crash`() {
        reader.tearDown()
        reader.tearDown()
    }

    // endregion

    // region getContentScopeJson

    @Test
    fun `when feature is enabled then content scope state is enabled`() {
        duckAiChatHistoryFeature.self().setRawStoredState(State(enable = true))

        val result = JSONObject(reader.getContentScopeJson())
        val feature = result.getJSONObject("features").getJSONObject("duckAiChatHistory")

        assertEquals("enabled", feature.getString("state"))
    }

    @Test
    fun `when feature is disabled then content scope state is disabled`() {
        duckAiChatHistoryFeature.self().setRawStoredState(State(enable = false))

        val result = JSONObject(reader.getContentScopeJson())
        val feature = result.getJSONObject("features").getJSONObject("duckAiChatHistory")

        assertEquals("disabled", feature.getString("state"))
    }

    @Test
    fun `when feature has settings then content scope includes settings`() {
        duckAiChatHistoryFeature.self().setRawStoredState(
            State(enable = true, settings = """{"maxHistoryCount":5,"chatsLocalStorageKeys":["savedAIChats"]}"""),
        )

        val result = JSONObject(reader.getContentScopeJson())
        val settings = result.getJSONObject("features").getJSONObject("duckAiChatHistory").getJSONObject("settings")

        assertEquals(5, settings.getInt("maxHistoryCount"))
        assertEquals("savedAIChats", settings.getJSONArray("chatsLocalStorageKeys").getString(0))
    }

    @Test
    fun `when feature has no settings then content scope has empty settings`() {
        duckAiChatHistoryFeature.self().setRawStoredState(State(enable = true))

        val result = JSONObject(reader.getContentScopeJson())
        val settings = result.getJSONObject("features").getJSONObject("duckAiChatHistory").getJSONObject("settings")

        assertEquals(0, settings.length())
    }

    @Test
    fun `when feature has exceptions then content scope includes exceptions`() {
        duckAiChatHistoryFeature.self().setRawStoredState(
            State(
                enable = true,
                exceptions = listOf(FeatureException(domain = "example.com", reason = "test reason")),
            ),
        )

        val result = JSONObject(reader.getContentScopeJson())
        val exceptions = result.getJSONObject("features").getJSONObject("duckAiChatHistory").getJSONArray("exceptions")

        assertEquals(1, exceptions.length())
        assertEquals("example.com", exceptions.getJSONObject(0).getString("domain"))
        assertEquals("test reason", exceptions.getJSONObject(0).getString("reason"))
    }

    @Test
    fun `when getContentScopeJson called then result has unprotectedTemporary empty array`() {
        val result = JSONObject(reader.getContentScopeJson())

        assertEquals(0, result.getJSONArray("unprotectedTemporary").length())
    }

    // endregion

    // region getMaxHistoryCount

    @Test
    fun `when settings has maxHistoryCount then returns that value`() {
        duckAiChatHistoryFeature.self().setRawStoredState(
            State(enable = true, settings = """{"maxHistoryCount":5}"""),
        )

        assertEquals(5, reader.getMaxHistoryCount())
    }

    @Test
    fun `when settings has no maxHistoryCount then returns default`() {
        duckAiChatHistoryFeature.self().setRawStoredState(
            State(enable = true, settings = """{}"""),
        )

        assertEquals(10, reader.getMaxHistoryCount())
    }

    @Test
    fun `when settings is null then returns default`() {
        assertEquals(10, reader.getMaxHistoryCount())
    }

    @Test
    fun `when settings has invalid json then returns default`() {
        duckAiChatHistoryFeature.self().setRawStoredState(
            State(enable = true, settings = "invalid"),
        )

        assertEquals(10, reader.getMaxHistoryCount())
    }

    // endregion
}
