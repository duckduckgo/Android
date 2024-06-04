package com.duckduckgo.autofill.impl.ui.credential.management.viewing

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.sync.api.DeviceSyncState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class AutofillManagementStringBuilderImplTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val deviceSyncState: DeviceSyncState = mock()

    private val testee = AutofillManagementStringBuilderImpl(
        context = context,
        deviceSyncState = deviceSyncState,
        dispatchers = coroutineTestRule.testDispatcherProvider,
    )

    @Test
    fun whenDeletingOneLoginThenBuildsCorrectTitle() {
        val str = testee.stringForDeletePasswordDialogConfirmationTitle(numberToDelete = 1)
        assertEquals(DELETE_DIALOG_TITLE_1_PASSWORD, str)
    }

    @Test
    fun whenDeletingMultipleLoginsThenBuildsCorrectTitle() {
        val str = testee.stringForDeletePasswordDialogConfirmationTitle(numberToDelete = 2)
        assertEquals(DELETE_DIALOG_TITLE_2_PASSWORDS, str)
    }

    @Test
    fun whenDeletingOneLoginWithSyncEnabledThenBuildsCorrectMessage() = runTest {
        configureSyncState(enabled = true)
        val str = testee.stringForDeletePasswordDialogConfirmationMessage(numberToDelete = 1)
        assertEquals("$DELETE_DIALOG_MESSAGE_1_SYNC_ENABLED_SINGULAR $DELETE_DIALOG_MESSAGE_2_SINGULAR", str)
    }

    @Test
    fun whenDeletingOneLoginWithSyncDisabledThenBuildsCorrectMessage() = runTest {
        configureSyncState(enabled = false)
        val str = testee.stringForDeletePasswordDialogConfirmationMessage(numberToDelete = 1)
        assertEquals("$DELETE_DIALOG_MESSAGE_1_SYNC_DISABLED_SINGULAR $DELETE_DIALOG_MESSAGE_2_SINGULAR", str)
    }

    @Test
    fun whenDeletingTwoLoginsWithSyncEnabledThenBuildsCorrectMessage() = runTest {
        configureSyncState(enabled = true)
        val str = testee.stringForDeletePasswordDialogConfirmationMessage(numberToDelete = 2)
        assertEquals("$DELETE_DIALOG_MESSAGE_1_SYNC_ENABLED_PLURAL $DELETE_DIALOG_MESSAGE_2_PLURAL", str)
    }

    @Test
    fun whenDeletingTwoLoginsWithSyncDisabledThenBuildsCorrectMessage() = runTest {
        configureSyncState(enabled = false)
        val str = testee.stringForDeletePasswordDialogConfirmationMessage(numberToDelete = 2)
        assertEquals("$DELETE_DIALOG_MESSAGE_1_SYNC_DISABLED_PLURAL $DELETE_DIALOG_MESSAGE_2_PLURAL", str)
    }

    private fun configureSyncState(enabled: Boolean) {
        whenever(deviceSyncState.isUserSignedInOnDevice()).thenReturn(enabled)
    }

    private companion object {
        private const val DELETE_DIALOG_TITLE_1_PASSWORD = "Are you sure you want to delete this password?"
        private const val DELETE_DIALOG_TITLE_2_PASSWORDS = "Are you sure you want to delete 2 passwords?"

        private const val DELETE_DIALOG_MESSAGE_1_SYNC_ENABLED_SINGULAR = "Your password will be deleted from all synced devices."
        private const val DELETE_DIALOG_MESSAGE_1_SYNC_ENABLED_PLURAL = "Your passwords will be deleted from all synced devices."

        private const val DELETE_DIALOG_MESSAGE_1_SYNC_DISABLED_SINGULAR = "Your password will be deleted from this device."
        private const val DELETE_DIALOG_MESSAGE_1_SYNC_DISABLED_PLURAL = "Your passwords will be deleted from this device."

        private const val DELETE_DIALOG_MESSAGE_2_SINGULAR = "Make sure you still have a way to access your account."
        private const val DELETE_DIALOG_MESSAGE_2_PLURAL = "Make sure you still have a way to access your accounts."
    }
}
