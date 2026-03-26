package com.duckduckgo.sync.impl.promotion

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.sync.api.DeviceSyncState
import com.duckduckgo.sync.impl.promotion.SyncPromotionDataStore.PromotionType.BookmarkAddedDialog
import com.duckduckgo.sync.impl.promotion.SyncPromotionDataStore.PromotionType.BookmarksScreen
import com.duckduckgo.sync.impl.promotion.SyncPromotionDataStore.PromotionType.PasswordsScreen
import com.duckduckgo.sync.impl.promotion.bookmarks.addeddialog.SetupSyncBookmarkAddedPromoRules
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SyncPromotionsImplTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val syncPromotionFeature = FakeFeatureToggleFactory.create(SyncPromotionFeature::class.java)

    private val bookmarkAddedPromoRules: SetupSyncBookmarkAddedPromoRules = mock()
    private val dataStore: SyncPromotionDataStore = mock()
    private val syncState: DeviceSyncState = mock()

    private val testee = SyncPromotionsImpl(
        dispatchers = coroutineTestRule.testDispatcherProvider,
        syncPromotionFeature = syncPromotionFeature,
        syncState = syncState,
        dataStore = dataStore,
        bookmarkAddedPromoRules = bookmarkAddedPromoRules,
    )

    @Before
    fun setup() = runTest {
        configureAllTogglesEnabled()
        configureUserHasEnabledSync(enabled = false)
        configureBookmarksPromoPreviouslyDismissed(previouslyDismissed = false)
        configurePasswordsPromoPreviouslyDismissed(previouslyDismissed = false)
    }

    @Test
    fun whenSomeBookmarksSavedThenCanShowPromo() = runTest {
        assertTrue(testee.canShowBookmarksPromotion(savedBookmarks = 5))
    }

    @Test
    fun whenNoBookmarksSavedThenCannotShowPromo() = runTest {
        assertFalse(testee.canShowBookmarksPromotion(savedBookmarks = 0))
    }

    @Test
    fun whenBookmarksPromoDismissedBeforeThenCannotShowPromo() = runTest {
        configureBookmarksPromoPreviouslyDismissed(previouslyDismissed = true)
        assertFalse(testee.canShowBookmarksPromotion(savedBookmarks = 5))
    }

    @Test
    fun whenPasswordsPromoDismissedBeforeThenCannotShowPromo() = runTest {
        configurePasswordsPromoPreviouslyDismissed(previouslyDismissed = true)
        assertFalse(testee.canShowPasswordsPromotion(savedPasswords = 5))
    }

    @Test
    fun whenSomePasswordsSavedThenCanShowPromo() = runTest {
        assertTrue(testee.canShowPasswordsPromotion(savedPasswords = 5))
    }

    @Test
    fun whenNoPasswordsSavedThenCannotShowPromo() = runTest {
        assertFalse(testee.canShowPasswordsPromotion(savedPasswords = 0))
    }

    @Test
    fun whenCouldShowPasswordPromoButTopLevelPromoFlagDisabledThenCannotShowPromo() = runTest {
        syncPromotionFeature.self().setRawStoredState(State(enable = false))
        assertFalse(testee.canShowPasswordsPromotion(savedPasswords = 5))
    }

    @Test
    fun whenCouldShowPasswordPromoButSyncFlagDisabledThenCannotShowPromo() = runTest {
        configureSyncFeatureFlagState(state = false)
        assertFalse(testee.canShowPasswordsPromotion(savedPasswords = 5))
    }

    @Test
    fun whenCouldShowBookmarkPromoButTopLevelPromoFlagDisabledThenCannotShowPromo() = runTest {
        syncPromotionFeature.self().setRawStoredState(State(enable = false))
        assertFalse(testee.canShowBookmarksPromotion(savedBookmarks = 5))
    }

    @Test
    fun whenCouldShowBookmarkPromoButSyncFlagDisabledThenCannotShowPromo() = runTest {
        configureSyncFeatureFlagState(state = false)
        assertFalse(testee.canShowBookmarksPromotion(savedBookmarks = 5))
    }

    @Test
    fun whenCouldShowPasswordPromoButAlreadySyncedThenCannotShowPromo() = runTest {
        configureUserHasEnabledSync(enabled = true)
        assertFalse(testee.canShowPasswordsPromotion(savedPasswords = 5))
    }

    @Test
    fun whenCouldShowBookmarkPromoButAlreadySyncedThenCannotShowPromo() = runTest {
        configureUserHasEnabledSync(enabled = true)
        assertFalse(testee.canShowBookmarksPromotion(savedBookmarks = 5))
    }

    @Test
    fun whenCouldShowBookmarksAddedDialogThenUsePromoRulesToDecide() = runTest {
        testee.canShowBookmarkAddedDialogPromotion()
        verify(bookmarkAddedPromoRules).canShowPromo()
    }

    @Test
    fun whenPasswordPromoDismissedThenEventRecordedInDataStore() = runTest {
        testee.recordPasswordsPromotionDismissed()
        verify(dataStore).recordPromoDismissed(PasswordsScreen)
    }

    @Test
    fun whenBookmarkScreenPromoDismissedThenEventRecordedInDataStore() = runTest {
        testee.recordBookmarksPromotionDismissed()
        verify(dataStore).recordPromoDismissed(BookmarksScreen)
    }

    @Test
    fun whenBookmarkAddedDialogPromoDismissedThenEventRecordedInDataStore() = runTest {
        testee.recordBookmarkAddedDialogPromotionDismissed()
        verify(dataStore).recordPromoDismissed(BookmarkAddedDialog)
    }

    private fun configureUserHasEnabledSync(enabled: Boolean) {
        whenever(syncState.isUserSignedInOnDevice()).thenReturn(enabled)
    }

    private fun configureSyncFeatureFlagState(state: Boolean) {
        whenever(syncState.isFeatureEnabled()).thenReturn(state)
    }

    private suspend fun configureBookmarksPromoPreviouslyDismissed(previouslyDismissed: Boolean) {
        whenever(dataStore.hasPromoBeenDismissed(BookmarksScreen)).thenReturn(previouslyDismissed)
    }

    private suspend fun configurePasswordsPromoPreviouslyDismissed(previouslyDismissed: Boolean) {
        whenever(dataStore.hasPromoBeenDismissed(PasswordsScreen)).thenReturn(previouslyDismissed)
    }

    private fun configureAllTogglesEnabled() {
        configureSyncFeatureFlagState(state = true)
        syncPromotionFeature.self().setRawStoredState(State(enable = true))
        syncPromotionFeature.bookmarks().setRawStoredState(State(enable = true))
        syncPromotionFeature.passwords().setRawStoredState(State(enable = true))
    }
}
