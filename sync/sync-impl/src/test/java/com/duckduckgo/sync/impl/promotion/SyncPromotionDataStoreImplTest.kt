package com.duckduckgo.sync.impl.promotion

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.sync.impl.promotion.SyncPromotionDataStore.PromotionType.BookmarkAddedDialog
import com.duckduckgo.sync.impl.promotion.SyncPromotionDataStore.PromotionType.BookmarksScreen
import com.duckduckgo.sync.impl.promotion.SyncPromotionDataStore.PromotionType.BookmarksScreenMultiDevice
import com.duckduckgo.sync.impl.promotion.SyncPromotionDataStore.PromotionType.ChatTabPage
import com.duckduckgo.sync.impl.promotion.SyncPromotionDataStore.PromotionType.PasswordsScreen
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

class SyncPromotionDataStoreImplTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val temporaryFolder = TemporaryFolder.builder().assureDeletion().build().also { it.create() }

    private val testDataStore: DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = coroutineTestRule.testScope,
            produceFile = { temporaryFolder.newFile("temp.preferences_pb") },
        )

    private var currentTimeMillis = 1_000_000L

    private val testee = SyncPromotionDataStoreImpl(
        dataStore = testDataStore,
        currentTimeProvider = mock<CurrentTimeProvider> {
            on { currentTimeMillis() } doAnswer { currentTimeMillis }
        },
    )

    @Test
    fun whenInitializedThenBookmarksScreenPromoHasNotBeenDismissed() = runTest {
        assertFalse(testee.hasPromoBeenDismissed(BookmarksScreen))
    }

    @Test
    fun whenBookmarksPromoRecordedThenBookmarksPromoHasBeenDismissed() = runTest {
        testee.recordPromoDismissed(BookmarksScreen)
        assertTrue(testee.hasPromoBeenDismissed(BookmarksScreen))
    }

    @Test
    fun whenInitializedThenPasswordsPromoHasNotBeenDismissed() = runTest {
        assertFalse(testee.hasPromoBeenDismissed(PasswordsScreen))
    }

    @Test
    fun whenPasswordsPromoRecordedThenPasswordsPromoHasBeenDismissed() = runTest {
        testee.recordPromoDismissed(PasswordsScreen)
        assertTrue(testee.hasPromoBeenDismissed(PasswordsScreen))
    }

    @Test
    fun whenInitializedThenBookmarkAddedPromoHasNotBeenDismissed() = runTest {
        assertFalse(testee.hasPromoBeenDismissed(BookmarkAddedDialog))
    }

    @Test
    fun whenBookmarkAddedPromoRecordedThenPromoHasBeenDismissed() = runTest {
        testee.recordPromoDismissed(BookmarkAddedDialog)
        assertTrue(testee.hasPromoBeenDismissed(BookmarkAddedDialog))
    }

    @Test
    fun whenInitializedThenChatTabPromoHasNotBeenDismissed() = runTest {
        assertFalse(testee.hasPromoBeenDismissed(ChatTabPage))
    }

    @Test
    fun whenChatTabPromoRecordedThenPromoHasBeenDismissed() = runTest {
        testee.recordPromoDismissed(ChatTabPage)
        assertTrue(testee.hasPromoBeenDismissed(ChatTabPage))
    }

    @Test
    fun whenInitializedThenBookmarksScreenPromoHasNoImpressions() = runTest {
        assertEquals(0L, testee.getPromoImpressionCount(BookmarksScreen))
    }

    @Test
    fun whenBookmarksPromoImpressionRecordedThenBookmarksImpressionCounterIncreases() = runTest {
        testee.recordPromoImpression(BookmarksScreen)
        assertEquals(1L, testee.getPromoImpressionCount(BookmarksScreen))

        testee.recordPromoImpression(BookmarksScreen)
        assertEquals(2L, testee.getPromoImpressionCount(BookmarksScreen))
    }

    @Test
    fun whenInitializedThenPasswordsPromoHasNoImpressions() = runTest {
        assertEquals(0L, testee.getPromoImpressionCount(PasswordsScreen))
    }

    @Test
    fun whenPasswordsPromoImpressionRecordedThenPasswordsImpressionCounterIncreases() = runTest {
        testee.recordPromoImpression(PasswordsScreen)
        assertEquals(1L, testee.getPromoImpressionCount(PasswordsScreen))

        testee.recordPromoImpression(PasswordsScreen)
        assertEquals(2L, testee.getPromoImpressionCount(PasswordsScreen))
    }

    @Test
    fun whenInitializedThenBookmarkAddedPromoHasNoImpressions() = runTest {
        assertEquals(0L, testee.getPromoImpressionCount(BookmarkAddedDialog))
    }

    @Test
    fun whenBookmarkAddedPromoImpressionRecordedThenImpressionCounterIncreases() = runTest {
        testee.recordPromoImpression(BookmarkAddedDialog)
        assertEquals(1L, testee.getPromoImpressionCount(BookmarkAddedDialog))

        testee.recordPromoImpression(BookmarkAddedDialog)
        assertEquals(2L, testee.getPromoImpressionCount(BookmarkAddedDialog))
    }

    @Test
    fun whenInitializedThenChatTabPromoHasNoImpressions() = runTest {
        assertEquals(0L, testee.getPromoImpressionCount(ChatTabPage))
    }

    @Test
    fun whenChatTabPromoImpressionRecordedThenImpressionCounterIncreases() = runTest {
        testee.recordPromoImpression(ChatTabPage)
        assertEquals(1L, testee.getPromoImpressionCount(ChatTabPage))

        testee.recordPromoImpression(ChatTabPage)
        assertEquals(2L, testee.getPromoImpressionCount(ChatTabPage))
    }

    @Test
    fun whenInitializedThenBookmarksScreenPromoHasNoLastImpression() = runTest {
        assertNull(testee.getTimeSinceLastPromoImpression(BookmarksScreen))
    }

    @Test
    fun whenBookmarksPromoImpressionRecordedThenTimeSinceLastImpressionGrowsWithTime() = runTest {
        testee.recordPromoImpression(BookmarksScreen)
        assertEquals(0.milliseconds, testee.getTimeSinceLastPromoImpression(BookmarksScreen))

        currentTimeMillis += 2.hours.inWholeMilliseconds
        assertEquals(2.hours, testee.getTimeSinceLastPromoImpression(BookmarksScreen))
    }

    @Test
    fun whenBookmarksPromoImpressionRecordedAgainThenTimeSinceLastImpressionResets() = runTest {
        testee.recordPromoImpression(BookmarksScreen)
        currentTimeMillis += 2.hours.inWholeMilliseconds

        testee.recordPromoImpression(BookmarksScreen)
        assertEquals(0.milliseconds, testee.getTimeSinceLastPromoImpression(BookmarksScreen))
    }

    @Test
    fun whenBookmarksPromoImpressionRecordedThenOtherPromosHaveNoLastImpression() = runTest {
        testee.recordPromoImpression(BookmarksScreen)
        assertNull(testee.getTimeSinceLastPromoImpression(BookmarksScreenMultiDevice))
    }

    @Test
    fun whenPromoHistoryClearedThenPromoHasNoLastImpression() = runTest {
        testee.recordPromoImpression(BookmarksScreen)

        testee.clearPromoHistory(BookmarksScreen)
        assertNull(testee.getTimeSinceLastPromoImpression(BookmarksScreen))
    }
}
