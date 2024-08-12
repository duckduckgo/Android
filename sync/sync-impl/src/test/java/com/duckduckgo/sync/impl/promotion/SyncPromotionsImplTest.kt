package com.duckduckgo.sync.impl.promotion

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.sync.api.DeviceSyncState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SyncPromotionsImplTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val syncPromotionFeature = TestSyncPromotionFeature(
        topLevelToggle = TestToggle(),
        bookmarksToggle = TestToggle(),
        passwordsToggle = TestToggle(),
    )

    private val dataStore: SyncPromotionDataStore = mock()
    private val syncState: DeviceSyncState = mock()

    private val testee = SyncPromotionsImpl(
        dispatchers = coroutineTestRule.testDispatcherProvider,
        syncPromotionFeature = syncPromotionFeature,
        syncState = syncState,
        dataStore = dataStore,
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
        syncPromotionFeature.topLevelToggle.enabled = false
        assertFalse(testee.canShowPasswordsPromotion(savedPasswords = 5))
    }

    @Test
    fun whenCouldShowPasswordPromoButSyncFlagDisabledThenCannotShowPromo() = runTest {
        configureSyncFeatureFlagState(state = false)
        assertFalse(testee.canShowPasswordsPromotion(savedPasswords = 5))
    }

    @Test
    fun whenCouldShowBookmarkPromoButTopLevelPromoFlagDisabledThenCannotShowPromo() = runTest {
        syncPromotionFeature.topLevelToggle.enabled = false
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
    fun whenPasswordPromoDismissedThenEventRecordedInDataStore() = runTest {
        testee.recordPasswordsPromotionDismissed()
        verify(dataStore).recordPasswordsPromoDismissed()
    }

    @Test
    fun whenBookmarkPromoDismissedThenEventRecordedInDataStore() = runTest {
        testee.recordBookmarksPromotionDismissed()
        verify(dataStore).recordBookmarksPromoDismissed()
    }

    private fun configureUserHasEnabledSync(enabled: Boolean) {
        whenever(syncState.isUserSignedInOnDevice()).thenReturn(enabled)
    }

    private fun configureSyncFeatureFlagState(state: Boolean) {
        whenever(syncState.isFeatureEnabled()).thenReturn(state)
    }

    private suspend fun configureBookmarksPromoPreviouslyDismissed(previouslyDismissed: Boolean) {
        whenever(dataStore.hasBookmarksPromoBeenDismissed()).thenReturn(previouslyDismissed)
    }

    private suspend fun configurePasswordsPromoPreviouslyDismissed(previouslyDismissed: Boolean) {
        whenever(dataStore.hasPasswordsPromoBeenDismissed()).thenReturn(previouslyDismissed)
    }

    private fun configureAllTogglesEnabled() {
        configureSyncFeatureFlagState(state = true)
        syncPromotionFeature.topLevelToggle.enabled = true
        syncPromotionFeature.bookmarksToggle.enabled = true
        syncPromotionFeature.passwordsToggle.enabled = true
    }

    private class TestSyncPromotionFeature(
        val topLevelToggle: TestToggle,
        val bookmarksToggle: TestToggle,
        val passwordsToggle: TestToggle,
    ) : SyncPromotionFeature {

        override fun self() = topLevelToggle
        override fun bookmarks() = bookmarksToggle
        override fun passwords() = passwordsToggle
    }

    private class TestToggle : Toggle {
        var enabled = false

        override fun isEnabled() = enabled

        override fun setEnabled(state: State) {
            enabled = state.enable
        }

        override fun getRawStoredState(): State? = null
    }
}
