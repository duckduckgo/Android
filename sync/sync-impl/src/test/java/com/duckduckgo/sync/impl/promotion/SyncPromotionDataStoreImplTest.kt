package com.duckduckgo.sync.impl.promotion

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SyncPromotionDataStoreImplTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val temporaryFolder = TemporaryFolder.builder().assureDeletion().build().also { it.create() }

    private val testDataStore: DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = coroutineTestRule.testScope,
            produceFile = { temporaryFolder.newFile("temp.preferences_pb") },
        )

    private val testee = SyncPromotionDataStoreImpl(testDataStore)

    @Test
    fun whenInitializedThenBookmarksPromoHasNotBeenDismissed() = runTest {
        assertFalse(testee.hasBookmarksPromoBeenDismissed())
    }

    @Test
    fun whenBookmarksPromoRecordedThenBookmarksPromoHasBeenDismissed() = runTest {
        testee.recordBookmarksPromoDismissed()
        assertTrue(testee.hasBookmarksPromoBeenDismissed())
    }

    @Test
    fun whenInitializedThenPasswordsPromoHasNotBeenDismissed() = runTest {
        assertFalse(testee.hasPasswordsPromoBeenDismissed())
    }

    @Test
    fun whenPasswordsPromoRecordedThenPasswordsPromoHasBeenDismissed() = runTest {
        testee.recordPasswordsPromoDismissed()
        assertTrue(testee.hasPasswordsPromoBeenDismissed())
    }
}
