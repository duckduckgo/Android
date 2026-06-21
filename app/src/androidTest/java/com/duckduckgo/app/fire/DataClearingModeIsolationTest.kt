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

package com.duckduckgo.app.fire

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.fire.clearing.TabsDataClearingPlugin
import com.duckduckgo.app.fire.db.FireModeDatabase
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.app.tabs.db.TabsDao
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.app.tabs.model.TabSwitcherData
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.browsermode.api.BrowserModeDataProvider
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.dataclearing.api.plugin.ClearableData
import com.duckduckgo.duckchat.store.impl.DuckAiBridgeStorage
import com.duckduckgo.duckchat.store.impl.DuckAiMigrationPrefs
import com.duckduckgo.duckchat.store.impl.RealDuckAiChatStore
import com.duckduckgo.duckchat.store.impl.store.DuckAiBridgeChatEntity
import com.duckduckgo.duckchat.store.impl.store.DuckAiBridgeChatsDao
import com.duckduckgo.duckchat.store.impl.store.DuckAiBridgeFileMetaDao
import com.duckduckgo.duckchat.store.impl.store.DuckAiBridgeSettingsDao
import com.duckduckgo.duckchat.store.impl.store.FireModeDuckAiDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File

/**
 * G3 — Mode isolation safety-net.
 *
 * Proves that clearing Fire-mode data through the real plugins does NOT touch Regular-mode data,
 * and vice versa. Backed by real in-memory Room databases — no logic is mocked.
 */
class DataClearingModeIsolationTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    // ---------- Tab databases ----------

    /** Two separate in-memory instances of the same schema act as "regular" and "fire" tab DBs. */
    private lateinit var regularTabDb: FireModeDatabase
    private lateinit var fireTabDb: FireModeDatabase

    private lateinit var regularTabsDao: TabsDao
    private lateinit var fireTabsDao: TabsDao

    // ---------- Chat databases ----------

    private lateinit var regularChatDb: FireModeDuckAiDatabase
    private lateinit var fireChatDb: FireModeDuckAiDatabase

    private lateinit var regularChatStore: RealDuckAiChatStore
    private lateinit var fireChatStore: RealDuckAiChatStore

    // ---------- Plugin under test ----------

    private lateinit var tabsPlugin: TabsDataClearingPlugin

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // --- Tab DBs ---
        regularTabDb = Room.inMemoryDatabaseBuilder(context, FireModeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        fireTabDb = Room.inMemoryDatabaseBuilder(context, FireModeDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        regularTabsDao = regularTabDb.tabsDao()
        fireTabsDao = fireTabDb.tabsDao()

        // --- Chat DBs ---
        regularChatDb = Room.inMemoryDatabaseBuilder(context, FireModeDuckAiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        fireChatDb = Room.inMemoryDatabaseBuilder(context, FireModeDuckAiDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val mockMigrationPrefs: DuckAiMigrationPrefs = mock()
        whenever(mockMigrationPrefs.isMigrationDone(DuckAiMigrationPrefs.CHATS_KEY)).thenReturn(true)

        regularChatStore = RealDuckAiChatStore(
            storage = FakeDuckAiBridgeStorage(regularChatDb.chatsDao(), regularChatDb.fileMetaDao(), context),
            dispatchers = coroutineTestRule.testDispatcherProvider,
            migrationPrefs = mockMigrationPrefs,
        )
        fireChatStore = RealDuckAiChatStore(
            storage = FakeDuckAiBridgeStorage(fireChatDb.chatsDao(), fireChatDb.fileMetaDao(), context),
            dispatchers = coroutineTestRule.testDispatcherProvider,
            migrationPrefs = mockMigrationPrefs,
        )

        // Provider routes by mode to the matching DAO-backed stub
        val regularRepo = DaoBackedTabRepository(regularTabsDao)
        val fireRepo = DaoBackedTabRepository(fireTabsDao)
        val provider = object : BrowserModeDataProvider<TabRepository> {
            override fun forMode(mode: BrowserMode): TabRepository =
                if (mode == BrowserMode.FIRE) fireRepo else regularRepo
        }
        tabsPlugin = TabsDataClearingPlugin(provider)
    }

    @After
    fun tearDown() {
        regularTabDb.close()
        fireTabDb.close()
        regularChatDb.close()
        fireChatDb.close()
    }

    // -----------------------------------------------------------------------
    // Tab isolation
    // -----------------------------------------------------------------------

    @Test
    fun `clearing all fire tabs leaves regular tabs untouched`() = runTest {
        regularTabsDao.insertTab(TabEntity(tabId = "reg1", position = 0))
        regularTabsDao.insertTab(TabEntity(tabId = "reg2", position = 1))
        fireTabsDao.insertTab(TabEntity(tabId = "fire1", position = 0))
        fireTabsDao.insertTab(TabEntity(tabId = "fire2", position = 1))

        tabsPlugin.onClearData(setOf(ClearableData.Tabs.AllForMode(BrowserMode.FIRE)))

        assertEquals("Fire tabs should be cleared", 0, fireTabsDao.tabs().size)
        assertEquals("Regular tabs must be untouched", 2, regularTabsDao.tabs().size)
    }

    @Test
    fun `clearing a single fire tab leaves regular tabs and other fire tabs untouched`() = runTest {
        regularTabsDao.insertTab(TabEntity(tabId = "reg1", position = 0))
        fireTabsDao.insertTab(TabEntity(tabId = "fire1", position = 0))
        fireTabsDao.insertTab(TabEntity(tabId = "fire2", position = 1))

        tabsPlugin.onClearData(setOf(ClearableData.Tabs.SingleForMode(tabId = "fire1", mode = BrowserMode.FIRE)))

        val remainingFireTabs = fireTabsDao.tabs()
        assertEquals("Only one fire tab should remain", 1, remainingFireTabs.size)
        assertEquals("The surviving fire tab should be fire2", "fire2", remainingFireTabs.first().tabId)
        assertEquals("Regular tabs must be untouched", 1, regularTabsDao.tabs().size)
    }

    @Test
    fun `tabs plugin ignores non-fire AllForMode type`() = runTest {
        regularTabsDao.insertTab(TabEntity(tabId = "reg1", position = 0))
        fireTabsDao.insertTab(TabEntity(tabId = "fire1", position = 0))

        // Regular-mode all-tabs type — plugin should not act on this
        tabsPlugin.onClearData(setOf(ClearableData.Tabs.AllForMode(BrowserMode.REGULAR)))

        assertEquals("Regular tabs must not be cleared by fire plugin", 1, regularTabsDao.tabs().size)
        assertEquals("Fire tabs must not be cleared when regular mode requested", 1, fireTabsDao.tabs().size)
    }

    // -----------------------------------------------------------------------
    // Chat isolation
    // -----------------------------------------------------------------------

    @Test
    fun `clearing all fire chats leaves regular chats untouched`() = runTest {
        regularChatDb.chatsDao().upsert(DuckAiBridgeChatEntity(chatId = "chat-reg", data = chatJson("chat-reg")))
        fireChatDb.chatsDao().upsert(DuckAiBridgeChatEntity(chatId = "chat-fire", data = chatJson("chat-fire")))

        fireChatStore.deleteAllChats()

        assertTrue("Fire chats should be deleted", fireChatDb.chatsDao().getAll().isEmpty())
        assertEquals("Regular chats must be untouched", 1, regularChatDb.chatsDao().getAll().size)
    }

    @Test
    fun `clearing a single fire chat leaves other fire chats and regular chats untouched`() = runTest {
        regularChatDb.chatsDao().upsert(DuckAiBridgeChatEntity(chatId = "chat-reg", data = chatJson("chat-reg")))
        fireChatDb.chatsDao().upsert(DuckAiBridgeChatEntity(chatId = "fire-a", data = chatJson("fire-a")))
        fireChatDb.chatsDao().upsert(DuckAiBridgeChatEntity(chatId = "fire-b", data = chatJson("fire-b")))

        fireChatStore.deleteChat("fire-a")

        val remainingFireChats = fireChatDb.chatsDao().getAll()
        assertEquals("Only one fire chat should remain", 1, remainingFireChats.size)
        assertEquals("The surviving fire chat should be fire-b", "fire-b", remainingFireChats.first().chatId)
        assertEquals("Regular chats must be untouched", 1, regularChatDb.chatsDao().getAll().size)
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private fun chatJson(chatId: String): String =
        """{"chatId":"$chatId","title":"Test","model":"gpt-4","lastEdit":"2026-01-01T00:00:00.000Z","pinned":false}"""

    /** Thin stub that delegates the methods exercised by [TabsDataClearingPlugin] to the real DAO. */
    private class DaoBackedTabRepository(private val dao: TabsDao) : TabRepository {
        override val liveTabs: LiveData<List<TabEntity>> get() = MutableLiveData(emptyList())
        override val flowTabs: Flow<List<TabEntity>> get() = emptyFlow()
        override val childClosedTabs: SharedFlow<String> get() = error("not used in test")
        override val flowDeletableTabs: Flow<List<TabEntity>> get() = emptyFlow()
        override val liveSelectedTab: LiveData<TabEntity> get() = MutableLiveData()
        override val flowSelectedTab: Flow<TabEntity?> get() = emptyFlow()
        override val tabSwitcherData: Flow<TabSwitcherData> get() = emptyFlow()
        override val flowLastAccessedTab: Flow<TabEntity?> get() = emptyFlow()

        override suspend fun deleteAll() = dao.deleteAllTabs()
        override suspend fun deleteTabs(tabIds: List<String>) = dao.deleteTabs(tabIds)
        override suspend fun getTabs(): List<TabEntity> = dao.tabs()

        override suspend fun add(url: String?, skipHome: Boolean) = error("not used in test")
        override suspend fun addDefaultTab() = error("not used in test")
        override suspend fun addFromSourceTab(url: String?, skipHome: Boolean, sourceTabId: String) = error("not used in test")
        override suspend fun addNewTabAfterExistingTab(url: String?, tabId: String) = error("not used in test")
        override suspend fun update(tabId: String, site: Site?) = error("not used in test")
        override suspend fun updateTabPosition(from: Int, to: Int) = error("not used in test")
        override suspend fun updateTabLastAccess(tabId: String) = error("not used in test")
        override fun retrieveSiteData(tabId: String): MutableLiveData<Site> = error("not used in test")
        override suspend fun delete(tab: TabEntity) = error("not used in test")
        override suspend fun markDeletable(tab: TabEntity) = error("not used in test")
        override suspend fun markDeletable(tabIds: List<String>) = error("not used in test")
        override suspend fun undoDeletable(tab: TabEntity) = error("not used in test")
        override suspend fun undoDeletable(tabIds: List<String>, moveActiveTabToEnd: Boolean) = error("not used in test")
        override suspend fun purgeDeletableTabs() = error("not used in test")
        override suspend fun getDeletableTabIds(): List<String> = error("not used in test")
        override suspend fun deleteTabAndSelectSource(tabId: String) = error("not used in test")
        override suspend fun getSelectedTab(): TabEntity? = error("not used in test")
        override suspend fun getLastAccessedTab(): TabEntity? = error("not used in test")
        override suspend fun select(tabId: String) = error("not used in test")
        override suspend fun getTab(tabId: String): TabEntity? = error("not used in test")
        override fun updateTabPreviewImage(tabId: String, fileName: String?) = error("not used in test")
        override fun updateTabFavicon(tabId: String, fileName: String?) = error("not used in test")
        override suspend fun selectByUrlOrNewTab(url: String) = error("not used in test")
        override suspend fun getTabId(url: String): String? = error("not used in test")
        override suspend fun setIsUserNew(isUserNew: Boolean) = error("not used in test")
        override suspend fun setTabLayoutType(layoutType: TabSwitcherData.LayoutType) = error("not used in test")
        override fun getOpenTabCount() = error("not used in test")
        override fun countTabsAccessedWithinRange(accessOlderThan: Long, accessNotMoreThan: Long?) = error("not used in test")
    }

    /** Minimal [DuckAiBridgeStorage] wired to a real in-memory database. */
    private class FakeDuckAiBridgeStorage(
        override val chats: DuckAiBridgeChatsDao,
        override val fileMeta: DuckAiBridgeFileMetaDao,
        context: android.content.Context,
    ) : DuckAiBridgeStorage {
        override val settings: DuckAiBridgeSettingsDao get() = error("not used in test")
        override val filesDir: File = context.cacheDir
    }
}
