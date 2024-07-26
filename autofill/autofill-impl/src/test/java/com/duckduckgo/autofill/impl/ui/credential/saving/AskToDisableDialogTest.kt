package com.duckduckgo.autofill.impl.ui.credential.saving

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autofill.api.AutofillEventListener
import com.duckduckgo.autofill.impl.store.InternalAutofillStore
import com.duckduckgo.autofill.impl.ui.credential.saving.declines.AutofillDeclineCounter
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

@RunWith(AndroidJUnit4::class)
class AskToDisableDialogTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val pixel: Pixel = mock()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dispatchers: DispatcherProvider = coroutineTestRule.testDispatcherProvider
    private val declineCounter: AutofillDeclineCounter = mock()
    private val autofillStore: InternalAutofillStore = mock()
    private val appCoroutineScope: CoroutineScope = coroutineTestRule.testScope
    private val callback: AutofillEventListener = mock()

    private val testee = AskToDisableDialog(
        pixel = pixel,
        dispatchers = dispatchers,
        declineCounter = declineCounter,
        autofillStore = autofillStore,
        autofillCallback = callback,
        appCoroutineScope = appCoroutineScope,
        context = context,
    )

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
}
