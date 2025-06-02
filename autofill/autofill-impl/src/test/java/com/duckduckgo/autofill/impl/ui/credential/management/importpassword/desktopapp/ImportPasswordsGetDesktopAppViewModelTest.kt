package com.duckduckgo.autofill.impl.ui.credential.management.importpassword.desktopapp

import app.cash.turbine.test
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_IMPORT_PASSWORDS_COPIED_DESKTOP_LINK
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_IMPORT_PASSWORDS_SHARED_DESKTOP_LINK
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillClipboardInteractor
import com.duckduckgo.autofill.impl.ui.credential.management.importpassword.desktopapp.ImportPasswordsGetDesktopAppViewModel.Command
import com.duckduckgo.autofill.impl.ui.credential.management.importpassword.desktopapp.ImportPasswordsGetDesktopAppViewModel.Command.ShareLink
import com.duckduckgo.autofill.impl.ui.credential.management.importpassword.desktopapp.ImportPasswordsGetDesktopAppViewModel.Command.ShowCopiedNotification
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ImportPasswordsGetDesktopAppViewModelTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private lateinit var testee: ImportPasswordsGetDesktopAppViewModel
    private val pixel: Pixel = mock()
    private val autofillClipboardInteractor: AutofillClipboardInteractor = mock()

    @Before
    fun setup() {
        testee = ImportPasswordsGetDesktopAppViewModel(
            pixel = pixel,
            dispatchers = coroutineTestRule.testDispatcherProvider,
            autofillClipboardInteractor = autofillClipboardInteractor,
        )
    }

    @Test
    fun whenLinkClickedThenCopiedToClipboard() = runTest {
        testee.onLinkClicked()
        verify(autofillClipboardInteractor).copyToClipboard(toCopy = any(), isSensitive = eq(false))
    }

    @Test
    fun whenLinkCopiedToClipboardAndSystemNotificationNotShownThenWeShowOurOwnNotification() = runTest {
        whenever(autofillClipboardInteractor.shouldShowCopyNotification()).thenReturn(true)
        testee.onLinkClicked()
        testee.commands.test {
            awaitItem().assertIsShowNotification()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenLinkClickedThenPixelFired() = runTest {
        testee.onLinkClicked()
        verify(pixel).fire(AUTOFILL_IMPORT_PASSWORDS_COPIED_DESKTOP_LINK)
    }

    @Test
    fun whenShareClickedThenCommandSent() = runTest {
        testee.onShareClicked()
        testee.commands.test {
            val command = awaitItem().assertIsShareLink()
            assertEquals("https://duckduckgo.com/browser?origin=funnel_browser_android_sync", command.link)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenShareClickedThenPixelFired() = runTest {
        testee.onShareClicked()
        verify(pixel).fire(AUTOFILL_IMPORT_PASSWORDS_SHARED_DESKTOP_LINK)
    }

    private fun Command.assertIsShareLink() = this as ShareLink
    private fun Command.assertIsShowNotification() = this as ShowCopiedNotification
}
