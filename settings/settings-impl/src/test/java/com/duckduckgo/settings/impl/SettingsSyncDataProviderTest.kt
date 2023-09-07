package com.duckduckgo.settings.impl

import androidx.arch.core.executor.testing.*
import androidx.room.*
import androidx.test.ext.junit.runners.*
import androidx.test.platform.app.*
import com.duckduckgo.app.*
import com.duckduckgo.app.global.plugins.PluginPoint
import com.duckduckgo.settings.api.*
import com.duckduckgo.sync.api.*
import com.duckduckgo.sync.api.engine.*
import com.duckduckgo.sync.api.engine.ModifiedSince.FirstSync
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.Ignore
import org.junit.runner.*

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class SettingsSyncDataProviderTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, SettingsDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    val metadataDao = db.settingsSyncDao()
    val settingSyncStore = FakeSettingsSyncStore()
    val duckAddressSetting = FakeSyncableSetting()
    val syncableSettingsPP = SyncableSettingsPluginPoint(mutableListOf(duckAddressSetting))

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
    fun whenGetChangesForFirstTimeThenMetadataIsInitialized() = runTest {
        assertTrue(metadataDao.getAllObservable().first().isEmpty())

        testee.getChanges()

        assertNotNull(metadataDao.getAllObservable().first().find { it.key == duckAddressSetting.key })
    }

    @Test
    fun whenGetChangesForFirstTimeThenExistingMetadataUpdated() = runTest {
        metadataDao.addOrUpdate(
            SettingsSyncMetadataEntity(duckAddressSetting.key, "", ""),
        )
        syncableSettingsPP.syncableSettings.add(
            FakeSyncableSetting().apply {
                key = "new_setting"
            },
        )
        assertTrue(metadataDao.getAllObservable().first().size == 1)

        testee.getChanges()

        assertTrue(metadataDao.getAllObservable().first().size == 2)
        val duckAddressMetadata = metadataDao.getAllObservable().first().find { it.key == duckAddressSetting.key }
        assertTrue(duckAddressMetadata?.modified_at.isNullOrEmpty().not())
    }

    @Test
    fun whenGetChangesForFirstSyncThenChangesIncludeAllValues() {
        val changes = testee.getChanges()

        assertTrue(changes.type == SyncableType.SETTINGS)
        patchAdapter.fromJson(changes.jsonString)?.let { patch ->
            assertEquals(1, patch.settings.updates.size)
            assertEquals(duckAddressSetting.key, patch.settings.updates[0].key)
            assertEquals(duckAddressSetting.getValue(), patch.settings.updates[0].value)
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

        assertTrue(changes.type == SyncableType.SETTINGS)
        patchAdapter.fromJson(changes.jsonString)?.let { patch ->
            assertEquals(1, patch.settings.updates.size)
            assertEquals(duckAddressSetting.key, patch.settings.updates[0].key)
            assertEquals(duckAddressSetting.getValue(), patch.settings.updates[0].value)
            assertTrue(patch.settings.updates[0].deleted.isNullOrEmpty())
        }
        assertTrue(changes.modifiedSince is ModifiedSince.Timestamp)
    }

    @Test
    fun whenGetChangesSubsequentCallsAndNoChangesThenUpdatesAreEmpty() {
        settingSyncStore.serverModifiedSince = "2022-01-01T00:00:00Z"
        settingSyncStore.clientModifiedSince = "2022-01-01T00:00:00Z"
        metadataDao.addOrUpdate(SettingsSyncMetadataEntity(duckAddressSetting.key, "2022-01-01T00:00:00Z", ""))

        val changes = testee.getChanges()

        assertTrue(changes.type == SyncableType.SETTINGS)
        assertTrue(changes.jsonString.isEmpty())
        assertTrue(changes.modifiedSince is ModifiedSince.Timestamp)
    }

    @Test
    fun whenDBHasDataButItIsFirstSyncThenIncludeAllValues() {
        metadataDao.addOrUpdate(SettingsSyncMetadataEntity(duckAddressSetting.key, "2022-01-01T00:00:00Z", ""))

        val changes = testee.getChanges()

        assertTrue(changes.type == SyncableType.SETTINGS)
        patchAdapter.fromJson(changes.jsonString)?.let { patch ->
            assertEquals(1, patch.settings.updates.size)
            assertEquals(duckAddressSetting.key, patch.settings.updates[0].key)
            assertEquals(duckAddressSetting.getValue(), patch.settings.updates[0].value)
            assertTrue(patch.settings.updates[0].deleted.isNullOrEmpty())
        }
        assertTrue(changes.modifiedSince is FirstSync)
    }

    @Test
    fun whenGetChangesForFirstSyncAndSettingNullThenSendAsDeleted() {
        duckAddressSetting.save(null)

        val changes = testee.getChanges()

        assertTrue(changes.type == SyncableType.SETTINGS)
        patchAdapter.fromJson(changes.jsonString)?.let { patch ->
            assertEquals(1, patch.settings.updates.size)
            assertEquals(duckAddressSetting.key, patch.settings.updates[0].key)
            assertEquals("", patch.settings.updates[0].value)
            assertFalse(patch.settings.updates[0].deleted.isNullOrEmpty())
        }
        assertTrue(changes.modifiedSince is FirstSync)
    }

    @Test
    fun whenGetChangesSubsequentCallsAndSettingNullThenUpdatesAreEmpty() {
        settingSyncStore.serverModifiedSince = "2022-01-01T00:00:00Z"
        settingSyncStore.clientModifiedSince = "2022-01-01T00:00:00Z"
        metadataDao.addOrUpdate(SettingsSyncMetadataEntity(duckAddressSetting.key, "2022-01-02T00:00:00Z", ""))
        duckAddressSetting.save(null)

        val changes = testee.getChanges()

        assertTrue(changes.type == SyncableType.SETTINGS)
        patchAdapter.fromJson(changes.jsonString)?.let { patch ->
            assertEquals(1, patch.settings.updates.size)
            assertEquals(duckAddressSetting.key, patch.settings.updates[0].key)
            assertEquals("", patch.settings.updates[0].value)
            assertFalse(patch.settings.updates[0].deleted.isNullOrEmpty())
        }
        assertTrue(changes.modifiedSince is ModifiedSince.Timestamp)
    }

    companion object {
        private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val patchAdapter: JsonAdapter<SyncSettingsRequest> = moshi.adapter(SyncSettingsRequest::class.java).serializeNulls()
    }
}

open class FakeSyncableSetting() : SyncableSetting {
    override var key: String = "fake_setting"

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

    override fun registerToRemoteChanges(onDataChanged: () -> Unit) {
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
    val syncableSettings: MutableList<SyncableSetting>,
) : PluginPoint<SyncableSetting> {
    override fun getPlugins(): Collection<SyncableSetting> {
        return syncableSettings
    }
}
