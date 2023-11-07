package com.duckduckgo.autofill.impl.ui.credential.saving

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autofill.api.AutofillEventListener
import com.duckduckgo.autofill.api.store.AutofillStore
import com.duckduckgo.autofill.impl.AutofillFireproofDialogSuppressor
import com.duckduckgo.autofill.impl.ui.credential.saving.declines.AutofillDeclineCounter
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class ResultHandlerPromptToDisableCredentialSavingTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()
    private val autofillFireproofDialogSuppressor: AutofillFireproofDialogSuppressor = mock()
    private val pixel: Pixel = mock()
    private val dispatchers: DispatcherProvider = coroutineTestRule.testDispatcherProvider
    private val declineCounter: AutofillDeclineCounter = mock()
    private val autofillStore: AutofillStore = mock()
    private val appCoroutineScope: CoroutineScope = coroutineTestRule.testScope
    private val context = getInstrumentation().targetContext
    private val callback: AutofillEventListener = mock()

    private val testee = ResultHandlerPromptToDisableCredentialSaving(
        autofillFireproofDialogSuppressor = autofillFireproofDialogSuppressor,
        pixel = pixel,
        dispatchers = dispatchers,
        declineCounter = declineCounter,
        autofillStore = autofillStore,
        appCoroutineScope = appCoroutineScope,
    )

    @Test
    fun whenResultProcessedThenFireproofNotifiedDialogNotVisible() {
        val result = bundleForAutofillDisablePrompt()
        testee.processResult(result, context, "tab-id-123", Fragment(), callback)
        verify(autofillFireproofDialogSuppressor).autofillSaveOrUpdateDialogVisibilityChanged(false)
    }

    @Test
    fun whenUserChoosesToDisableAutofillThenStoreUpdatedToFalse() {
        testee.onDisableAutofill(callback)
        verify(autofillStore).autofillEnabled = false
    }

    @Test
    fun whenUserChoosesToDisableAutofillThenDeclineCounterDisabled() = runTest {
        testee.onDisableAutofill(callback)
        verify(declineCounter).disableDeclineCounter()
    }

    @Test
    fun whenUserChoosesToDisableAutofillThenPageRefreshRequested() = runTest {
        testee.onDisableAutofill(callback)
        verify(callback).onAutofillStateChange()
    }

    @Test
    fun whenUserChoosesToKeepUsingAutofillThenDeclineCounterDisabled() = runTest {
        testee.onKeepUsingAutofill()
        verify(declineCounter).disableDeclineCounter()
    }

    private fun bundleForAutofillDisablePrompt(): Bundle = Bundle()
}
