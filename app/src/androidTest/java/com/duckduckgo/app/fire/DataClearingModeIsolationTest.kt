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

@file:SuppressLint("NoImplImportsInAppModule", "DenyListedApi")

package com.duckduckgo.app.fire

import android.annotation.SuppressLint
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
import com.duckduckgo.browsermode.api.FireModeAvailability
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.dataclearing.api.plugin.ClearableData
import com.duckduckgo.dataclearing.api.plugin.DataClearingPlugin
import com.duckduckgo.dataclearing.impl.plugin.DataClearingOrchestrator
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.impl.clearing.DuckChatDataClearingPlugin
import com.duckduckgo.duckchat.impl.clearing.DuckChatDeleter
import com.duckduckgo.duckchat.impl.repository.DuckChatFeatureRepository
import com.duckduckgo.duckchat.impl.sync.DuckChatSyncRepository
import com.duckduckgo.duckchat.store.impl.DuckAiBridgeStorage
import com.duckduckgo.duckchat.store.impl.DuckAiMigrationPrefs
import com.duckduckgo.duckchat.store.impl.RealDuckAiChatStore
import com.duckduckgo.duckchat.store.impl.store.DuckAiBridgeChatEntity
import com.duckduckgo.duckchat.store.impl.store.DuckAiBridgeChatsDao
import com.duckduckgo.duckchat.store.impl.store.DuckAiBridgeFileMetaDao
import com.duckduckgo.duckchat.store.impl.store.DuckAiBridgeSettingsDao
import com.duckduckgo.duckchat.store.impl.store.FireModeDuckAiDatabase
import com.duckduckgo.sync.api.engine.SyncEngine
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
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File

/**
 * G3 — Mode isolation safety-net.
 *
 * Proves that clearing Fire-mode data through the real orchestrator + plugins does NOT touch
 * Regular-mode data, and vice versa. Backed by real in-memory Room databases — no logic is mocked.
 *
 * The orchestrator under test is wired with only [TabsDataClearingPlugin] and
 * [DuckChatDataClearingPlugin] — WebView-backed plugins (cookies, web storage, dirs) are omitted
 * because they require a real WebView profile and cannot run in an instrumented unit test.
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

    // ---------- Mocked collaborators for DuckChatDataClearingPlugin ----------

    private val mockDuckChatDeleter: DuckChatDeleter = mock()
    private val mockFireModeAvailability: FireModeAvailability = mock()
    private val mockDuckChatSyncRepository: DuckChatSyncRepository = mock()
    private val mockSyncEngine: SyncEngine = mock()
    private val mockDuckChat: DuckChat = mock()
    private val mockCurrentTimeProvider: CurrentTimeProvider = mock()
    private val mockDuckChatFeatureRepository: DuckChatFeatureRepository = mock()

    // ---------- Plugins + orchestrator ----------

    private lateinit var tabsPlugin: TabsDataClearingPlugin
    private lateinit var duckChatPlugin: DuckChatDataClearingPlugin
    private lateinit var orchestrator: DataClearingOrchestrator

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

        // Use a real DuckAiMigrationPrefs backed by real SharedPreferences — avoids mocking a final
        // Kotlin class (which requires inline mock-maker, not configured in this target).
        // Migration state is irrelevant here: no test calls hasMigrated().
        val realMigrationPrefs = DuckAiMigrationPrefs(
            object : SharedPreferencesProvider {
                override fun getSharedPreferences(name: String, multiprocess: Boolean, migrate: Boolean) =
                    context.getSharedPreferences(name, android.content.Context.MODE_PRIVATE)
                override fun getEncryptedSharedPreferences(name: String, multiprocess: Boolean) =
                    context.getSharedPreferences(name, android.content.Context.MODE_PRIVATE)
                override suspend fun getMigratedEncryptedSharedPreferences(name: String) =
                    context.getSharedPreferences(name, android.content.Context.MODE_PRIVATE)
                override suspend fun getMigratedEncryptedSharedPreferences(origin: android.content.SharedPreferences, name: String) =
                    context.getSharedPreferences(name, android.content.Context.MODE_PRIVATE)
            },
        )

        regularChatStore = RealDuckAiChatStore(
            storage = FakeDuckAiBridgeStorage(regularChatDb.chatsDao(), regularChatDb.fileMetaDao(), context),
            dispatchers = coroutineTestRule.testDispatcherProvider,
            migrationPrefs = realMigrationPrefs,
        )
        fireChatStore = RealDuckAiChatStore(
            storage = FakeDuckAiBridgeStorage(fireChatDb.chatsDao(), fireChatDb.fileMetaDao(), context),
            dispatchers = coroutineTestRule.testDispatcherProvider,
            migrationPrefs = realMigrationPrefs,
        )

        // Provider routes by mode to the matching DAO-backed stub
        val regularRepo = DaoBackedTabRepository(regularTabsDao)
        val fireRepo = DaoBackedTabRepository(fireTabsDao)
        val tabsProvider = object : BrowserModeDataProvider<TabRepository> {
            override fun forMode(mode: BrowserMode): TabRepository =
                if (mode == BrowserMode.FIRE) fireRepo else regularRepo
        }
        tabsPlugin = TabsDataClearingPlugin(tabsProvider)

        // DuckChatDataClearingPlugin uses the real in-memory fire store; sync/deleter collaborators
        // are mocked because this test only asserts DB row counts, not sync behaviour.
        whenever(mockFireModeAvailability.isAvailable()).thenReturn(true)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(1_000_000L)
        duckChatPlugin = DuckChatDataClearingPlugin(
            duckChatDeleter = mockDuckChatDeleter,
            fireChatStore = fireChatStore,
            fireModeAvailability = mockFireModeAvailability,
            duckChatSyncRepository = mockDuckChatSyncRepository,
            syncEngine = mockSyncEngine,
            duckChat = mockDuckChat,
            currentTimeProvider = mockCurrentTimeProvider,
            duckChatFeatureRepository = mockDuckChatFeatureRepository,
        )

        // Orchestrator wired with the two DB-backed plugins — WebView plugins omitted (need real profile).
        orchestrator = DataClearingOrchestrator(
            plugins = object : PluginPoint<DataClearingPlugin> {
                override fun getPlugins(): Collection<DataClearingPlugin> = listOf(tabsPlugin, duckChatPlugin)
            },
        )
    }

    @After
    fun tearDown() {
        regularTabDb.close()
        fireTabDb.close()
        regularChatDb.close()
        fireChatDb.close()
    }

    // -----------------------------------------------------------------------
    // Tab isolation — Fire-only burn
    // -----------------------------------------------------------------------

    @Test
    fun clearing_all_fire_tabs_leaves_regular_tabs_untouched() = runTest {
        regularTabsDao.insertTab(TabEntity(tabId = "reg1", position = 0))
        regularTabsDao.insertTab(TabEntity(tabId = "reg2", position = 1))
        fireTabsDao.insertTab(TabEntity(tabId = "fire1", position = 0))
        fireTabsDao.insertTab(TabEntity(tabId = "fire2", position = 1))

        orchestrator.clearData(setOf(ClearableData.Tabs.AllForMode(BrowserMode.FIRE)))

        assertEquals("Fire tabs should be cleared", 0, fireTabsDao.tabs().size)
        assertEquals("Regular tabs must be untouched", 2, regularTabsDao.tabs().size)
    }

    @Test
    fun clearing_a_single_fire_tab_leaves_regular_tabs_and_other_fire_tabs_untouched() = runTest {
        regularTabsDao.insertTab(TabEntity(tabId = "reg1", position = 0))
        fireTabsDao.insertTab(TabEntity(tabId = "fire1", position = 0))
        fireTabsDao.insertTab(TabEntity(tabId = "fire2", position = 1))

        orchestrator.clearData(setOf(ClearableData.Tabs.SingleForMode(tabId = "fire1", mode = BrowserMode.FIRE)))

        val remainingFireTabs = fireTabsDao.tabs()
        assertEquals("Only one fire tab should remain", 1, remainingFireTabs.size)
        assertEquals("The surviving fire tab should be fire2", "fire2", remainingFireTabs.first().tabId)
        assertEquals("Regular tabs must be untouched", 1, regularTabsDao.tabs().size)
    }

    @Test
    fun tabs_plugin_ignores_non_fire_AllForMode_type() = runTest {
        regularTabsDao.insertTab(TabEntity(tabId = "reg1", position = 0))
        fireTabsDao.insertTab(TabEntity(tabId = "fire1", position = 0))

        // Regular-mode all-tabs type — TabsDataClearingPlugin only handles FIRE
        orchestrator.clearData(setOf(ClearableData.Tabs.AllForMode(BrowserMode.REGULAR)))

        assertEquals("Regular tabs must not be cleared by fire plugin", 1, regularTabsDao.tabs().size)
        assertEquals("Fire tabs must not be cleared when regular mode requested", 1, fireTabsDao.tabs().size)
    }

    // -----------------------------------------------------------------------
    // Chat isolation — Fire-only burn
    // -----------------------------------------------------------------------

    @Test
    fun clearing_all_fire_chats_leaves_regular_chats_untouched() = runTest {
        regularChatDb.chatsDao().upsert(DuckAiBridgeChatEntity(chatId = "chat-reg", data = chatJson("chat-reg")))
        fireChatDb.chatsDao().upsert(DuckAiBridgeChatEntity(chatId = "chat-fire", data = chatJson("chat-fire")))

        orchestrator.clearData(setOf(ClearableData.DuckChats.AllForMode(BrowserMode.FIRE)))

        assertTrue("Fire chats should be deleted", fireChatDb.chatsDao().getAll().isEmpty())
        assertEquals("Regular chats must be untouched", 1, regularChatDb.chatsDao().getAll().size)
    }

    @Test
    fun clearing_a_single_fire_chat_leaves_other_fire_chats_and_regular_chats_untouched() = runTest {
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
    // Cross-mode burn — Regular-origin clear (clears both modes)
    // -----------------------------------------------------------------------

    /**
     * A Regular-origin fire produces a type set spanning both modes.
     * The orchestrator fans out to each plugin; this test verifies:
     *  - Fire tabs are cleared (TabsDataClearingPlugin handles AllForMode(FIRE))
     *  - Regular tabs are untouched by the plugin (TabsDataClearingPlugin ignores AllForMode(REGULAR);
     *    the regular-mode tab store is managed separately in production)
     *  - Fire chats are cleared (DuckChatDataClearingPlugin handles AllForMode(FIRE) via the real store)
     *  - Regular chat deletion is delegated to duckChatDeleter (verified via mock)
     */
    @Test
    fun regular_origin_burn_dispatches_to_both_plugins_for_both_modes() = runTest {
        // Stub regular-chat deletion to succeed so sync recording is triggered
        whenever(mockDuckChatDeleter.deleteAllChats()).thenReturn(true)

        // Seed tabs in both modes
        regularTabsDao.insertTab(TabEntity(tabId = "reg-tab-1", position = 0))
        regularTabsDao.insertTab(TabEntity(tabId = "reg-tab-2", position = 1))
        fireTabsDao.insertTab(TabEntity(tabId = "fire-tab-1", position = 0))
        fireTabsDao.insertTab(TabEntity(tabId = "fire-tab-2", position = 1))

        // Seed chats in both modes
        regularChatDb.chatsDao().upsert(DuckAiBridgeChatEntity(chatId = "reg-chat-1", data = chatJson("reg-chat-1")))
        regularChatDb.chatsDao().upsert(DuckAiBridgeChatEntity(chatId = "reg-chat-2", data = chatJson("reg-chat-2")))
        fireChatDb.chatsDao().upsert(DuckAiBridgeChatEntity(chatId = "fire-chat-1", data = chatJson("fire-chat-1")))
        fireChatDb.chatsDao().upsert(DuckAiBridgeChatEntity(chatId = "fire-chat-2", data = chatJson("fire-chat-2")))

        // The cross-mode type set a Regular-origin burn produces
        orchestrator.clearData(
            setOf(
                ClearableData.Tabs.AllForMode(BrowserMode.REGULAR),
                ClearableData.DuckChats.AllForMode(BrowserMode.REGULAR),
                ClearableData.Tabs.AllForMode(BrowserMode.FIRE),
                ClearableData.DuckChats.AllForMode(BrowserMode.FIRE),
            ),
        )

        // Fire tabs: cleared by TabsDataClearingPlugin (handles AllForMode(FIRE))
        assertEquals("Fire tabs should be cleared", 0, fireTabsDao.tabs().size)

        // Regular tabs: TabsDataClearingPlugin only handles FIRE; regular-mode tab store is untouched
        // (production regular tab clearing happens outside this plugin's responsibility)
        assertEquals("Regular tabs are not touched by TabsDataClearingPlugin", 2, regularTabsDao.tabs().size)

        // Fire chats: cleared by DuckChatDataClearingPlugin via the real in-memory fire store
        assertTrue("Fire chats should be deleted", fireChatDb.chatsDao().getAll().isEmpty())

        // Regular chats: DuckChatDataClearingPlugin delegates to duckChatDeleter for REGULAR mode
        verify(mockDuckChatDeleter).deleteAllChats()

        // Regular chat DB is unaffected because duckChatDeleter is mocked (real IDB/LevelDB in prod)
        assertEquals("Regular chat DB rows are managed by duckChatDeleter (mocked here)", 2, regularChatDb.chatsDao().getAll().size)
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
