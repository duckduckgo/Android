package com.duckduckgo.autofill.impl.ui.credential.management.importpassword

import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_IMPORT_PASSWORDS_USER_TOOK_NO_ACTION
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class ImportPasswordsViewModelTest {

    private val pixel: Pixel = mock()
    private val testee = ImportPasswordsViewModel(pixel = pixel)

    @Test
    fun whenUserLeavesScreenWithoutTakingActionThenPixelSent() {
        testee.userLeavingScreen()
        verify(pixel).fire(AUTOFILL_IMPORT_PASSWORDS_USER_TOOK_NO_ACTION)
    }

    @Test
    fun whenUserLeavesScreenAfterClickingDesktopAppButtonThenNoPixelSent() {
        testee.onUserClickedGetDesktopAppButton()
        testee.userLeavingScreen()
        verify(pixel, never()).fire(AUTOFILL_IMPORT_PASSWORDS_USER_TOOK_NO_ACTION)
    }

    @Test
    fun whenUserLeavesScreenAfterClickingSyncButtonThenNoPixelSent() {
        testee.onUserClickedSyncWithDesktopButton()
        testee.userLeavingScreen()
        verify(pixel, never()).fire(AUTOFILL_IMPORT_PASSWORDS_USER_TOOK_NO_ACTION)
    }
}
