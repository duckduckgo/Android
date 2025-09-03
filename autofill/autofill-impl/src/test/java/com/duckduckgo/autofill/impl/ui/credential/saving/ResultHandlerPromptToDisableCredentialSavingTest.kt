package com.duckduckgo.autofill.impl.ui.credential.saving

import android.os.Bundle
import android.webkit.WebView
import androidx.fragment.app.Fragment
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.duckduckgo.autofill.api.AutofillEventListener
import com.duckduckgo.autofill.impl.AutofillFireproofDialogSuppressor
import com.duckduckgo.autofill.store.AutofillPrefsStore
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class ResultHandlerPromptToDisableCredentialSavingTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()
    private val autofillFireproofDialogSuppressor: AutofillFireproofDialogSuppressor = mock()
    private val context = getInstrumentation().targetContext
    private val disableAutofillPromptBehavior: DisableAutofillPromptBehavior = mock()
    private val autofillPrefsStore: AutofillPrefsStore = mock()
    private val disablePromptBehaviorFactory: DisableAutofillPromptBehaviorFactory = mock<DisableAutofillPromptBehaviorFactory>().also {
        whenever(it.createBehavior(any(), any(), any())).thenReturn(disableAutofillPromptBehavior)
    }
    private val callback: AutofillEventListener = mock()
    private val webView: WebView = mock()

    private val testee = ResultHandlerPromptToDisableCredentialSaving(
        autofillFireproofDialogSuppressor = autofillFireproofDialogSuppressor,
        behavior = disablePromptBehaviorFactory,
        autofillPrefsStore = autofillPrefsStore,
        appCoroutineScope = coroutineTestRule.testScope,
        dispatchers = coroutineTestRule.testDispatcherProvider,
    )

    @Test
    fun whenResultProcessedThenFireproofNotifiedDialogNotVisible() = runTest {
        val result = bundleForAutofillDisablePrompt()
        testee.processResult(result, context, "tab-id-123", Fragment(), callback, webView)
        verify(autofillFireproofDialogSuppressor).autofillSaveOrUpdateDialogVisibilityChanged(false)
    }

    @Test
    fun whenResultProcessedThenShowPrompt() = runTest {
        val fragment = Fragment()
        val result = bundleForAutofillDisablePrompt()
        testee.processResult(result, context, "tab-id-123", fragment, callback, webView)
        verify(disablePromptBehaviorFactory).createBehavior(context, fragment, callback)
        verify(disableAutofillPromptBehavior).showPrompt()
        verify(autofillPrefsStore).timestampUserLastPromptedToDisableAutofill = any()
    }

    private fun bundleForAutofillDisablePrompt(): Bundle = Bundle()
}
