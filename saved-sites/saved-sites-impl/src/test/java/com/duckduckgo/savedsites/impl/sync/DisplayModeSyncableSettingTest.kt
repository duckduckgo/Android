package com.duckduckgo.savedsites.impl.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.savedsites.store.FavoritesViewMode
import com.duckduckgo.sync.settings.api.SyncSettingsListener
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class DisplayModeSyncableSettingTest {

    @get:Rule
    private val coroutineRule = CoroutineTestRule()

    private val savedSitesSettingsStore = FakeSavedSitesSettingsStore(coroutineRule.testScope)
    private val syncSettingsListener: SyncSettingsListener = mock()

    private val testee = DisplayModeSyncableSetting(
        savedSitesSettingsStore,
        syncSettingsListener,
    )

    @Test
    fun whenGetValueThenReturnStoredValue() = runTest {
        savedSitesSettingsStore.favoritesDisplayMode = FavoritesViewMode.NATIVE
        assertEquals(FavoritesViewMode.NATIVE.value, testee.getValue())
    }

    @Test
    fun whenSaveWithValidValueThenReturnTrue() = runTest {
        assertTrue(testee.save(FavoritesViewMode.NATIVE.value))
    }

    @Test
    fun whenSaveWithInvalidValidValueThenReturnFalse() = runTest {
        assertFalse(testee.save("unknown_value"))
    }

    @Test
    fun whenDeduplicateWithValidValueThenReturnTrue() = runTest {
        assertTrue(testee.deduplicate(FavoritesViewMode.NATIVE.value))
    }

    @Test
    fun whenDeduplicateWithInvalidValidValueThenReturnFalse() = runTest {
        assertFalse(testee.deduplicate("unknown_value"))
    }

    @Test
    fun whenOnSettingChangedThenNotifyListener() = runTest {
        testee.onSettingChanged()
        verify(syncSettingsListener).onSettingChanged(testee.key)
    }
}
