package com.duckduckgo.settings.impl

import androidx.arch.core.executor.testing.*
import androidx.room.*
import androidx.test.ext.junit.runners.*
import androidx.test.platform.app.*
import com.duckduckgo.app.global.plugins.PluginPoint
import com.duckduckgo.settings.api.*
import com.duckduckgo.sync.api.*
import com.duckduckgo.sync.api.engine.*
import com.duckduckgo.sync.api.engine.ModifiedSince.FirstSync
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.runner.*
import java.util.AbstractSet

@RunWith(AndroidJUnit4::class)
class SettingsSyncDataProviderTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, SettingsDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    val metadataDao = db.settingsSyncDao()
    val settingSyncStore = FakeSettingsSyncStore()
    val duckAddressSetting = FakeSyncableSetting()
    val syncableSettingsPP = SyncableSettingsPluginPoint(listOf(duckAddressSetting))

    private val testee: SettingsSyncDataProvider = SettingsSyncDataProvider(
        syncableSettings = syncableSettingsPP,
        settingsSyncMetadataDao = metadataDao,
        settingsSyncStore = settingSyncStore,
        syncCrypto = FakeCrypto(),
    )

    @After
    fun after() {
        db.close()
    }

    @Test
    fun whenGetChangesForFirstSyncThenChangesIncludeAllValues() {
        val changes = testee.getChanges()

        assertTrue(changes.type==SyncableType.SETTINGS)
        patchAdapter.fromJson(changes.jsonString)?.let { patch ->
            assertEquals(1, patch.settings.updates.size)
            assertEquals("fake_setting", patch.settings.updates[0].key)
            assertEquals("fake_value", patch.settings.updates[0].value)
            assertTrue(patch.settings.updates[0].deleted.isNullOrEmpty())
        }
        assertTrue(changes.isFirstSync())
    }

    @Test
    @Ignore("Need to decide strategy first")
    fun whenGetChangesSubsequentCallsWithNewValueThenIncludeNewValues() {
        settingSyncStore.serverModifiedSince = "2022-01-01T00:00:00Z"
        settingSyncStore.clientModifiedSince = "2022-01-01T00:00:00Z"

        val changes = testee.getChanges()

        assertTrue(changes.type==SyncableType.SETTINGS)
        assertTrue(changes.jsonString.contains("\"key\":\"fake_setting\",\"value\":\"fake_value\""))
        assertTrue(changes.modifiedSince is ModifiedSince.Timestamp)
    }

    @Test
    fun whenGetChangesSubsequentCallsAndNoChangesThenUpdatesAreEmpty() {
        settingSyncStore.serverModifiedSince = "2022-01-01T00:00:00Z"
        settingSyncStore.clientModifiedSince = "2022-01-01T00:00:00Z"
        metadataDao.addOrUpdate(SettingsSyncMetadataEntity("fake_setting", "2022-01-01T00:00:00Z", ""))

        val changes = testee.getChanges()

        assertTrue(changes.type==SyncableType.SETTINGS)
        assertFalse(changes.jsonString.contains("\"key\":\"fake_setting\",\"value\":\"fake_value\""))
        assertTrue(changes.modifiedSince is ModifiedSince.Timestamp)
    }

    @Test
    fun whenDBHasDataButItIsFirstSyncThenIncludeAllValues() {
        metadataDao.addOrUpdate(SettingsSyncMetadataEntity("fake_setting", "2022-01-01T00:00:00Z", ""))

        val changes = testee.getChanges()

        assertTrue(changes.type==SyncableType.SETTINGS)
        assertTrue(changes.jsonString.contains("\"key\":\"fake_setting\",\"value\":\"fake_value\""))
        assertTrue(changes.modifiedSince is FirstSync)
    }

    @Test
    fun whenSettingNullThenSendAsDeleted() {
        duckAddressSetting.save(null)

        val changes = testee.getChanges()

        assertTrue(changes.type==SyncableType.SETTINGS)
        patchAdapter.fromJson(changes.jsonString)?.let { patch ->
            assertEquals(1, patch.settings.updates.size)
            assertEquals("fake_setting", patch.settings.updates[0].key)
            assertEquals("", patch.settings.updates[0].value)
            assertFalse(patch.settings.updates[0].deleted.isNullOrEmpty())
        }
        assertTrue(changes.modifiedSince is FirstSync)
    }

    companion object {
        private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val patchAdapter: JsonAdapter<SyncSettingsRequest> = moshi.adapter(SyncSettingsRequest::class.java).serializeNulls()
    }
}

open class FakeSyncableSetting() : SyncableSetting {
    override val key: String = "fake_setting"

    private var value: String? = "fake_value"

    override fun getValue(): String? = value

    override fun save(value: String?): Boolean {
        this.value = value
        return true
    }

    override fun mergeRemote(value: String?): Boolean {
        this.value = value
        return true
    }

    override fun registerToRemoteChanges(listener: (String) -> Unit) {
        TODO("Not yet implemented")
    }
}

class FakeCrypto : SyncCrypto {
    override fun encrypt(text: String) = text

    override fun decrypt(data: String) = data
}

class FakeSettingsSyncStore : SettingsSyncStore {
    override var serverModifiedSince: String = "0"

    override var clientModifiedSince: String = "0"

    override var startTimeStamp: String = "0"
}

class SyncableSettingsPluginPoint(
    val syncableSettings: List<SyncableSetting>
): PluginPoint<SyncableSetting> {
    override fun getPlugins(): Collection<SyncableSetting> {
        return syncableSettings
    }
}
