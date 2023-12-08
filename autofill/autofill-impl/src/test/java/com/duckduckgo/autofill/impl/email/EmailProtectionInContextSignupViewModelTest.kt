package com.duckduckgo.autofill.impl.email

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autofill.api.email.EmailManager
import com.duckduckgo.autofill.impl.email.incontext.EmailProtectionInContextSignupViewModel
import com.duckduckgo.autofill.impl.email.incontext.EmailProtectionInContextSignupViewModel.BackButtonAction.NavigateBack
import com.duckduckgo.autofill.impl.email.incontext.EmailProtectionInContextSignupViewModel.ExitButtonAction.ExitWithConfirmation
import com.duckduckgo.autofill.impl.email.incontext.EmailProtectionInContextSignupViewModel.ExitButtonAction.ExitWithoutConfirmation
import com.duckduckgo.autofill.impl.email.incontext.EmailProtectionInContextSignupViewModel.ViewState.CancellingInContextSignUp
import com.duckduckgo.autofill.impl.email.incontext.EmailProtectionInContextSignupViewModel.ViewState.NavigatingBack
import com.duckduckgo.autofill.impl.email.incontext.EmailProtectionInContextSignupViewModel.ViewState.ShowingWebContent
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.EMAIL_PROTECTION_IN_CONTEXT_MODAL_DISMISSED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.EMAIL_PROTECTION_IN_CONTEXT_MODAL_DISPLAYED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.EMAIL_PROTECTION_IN_CONTEXT_MODAL_EXIT_EARLY_CANCEL
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.EMAIL_PROTECTION_IN_CONTEXT_MODAL_EXIT_EARLY_CONFIRM
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

@RunWith(AndroidJUnit4::class)
class EmailProtectionInContextSignupViewModelTest {

    private val emailManager: EmailManager = mock()
    private val pixel: Pixel = mock()
    private val testee = EmailProtectionInContextSignupViewModel(pixel = pixel)

    @Test
    fun whenInitialStateThenCanExitWithoutConfirmation() = runTest {
        testee.viewState.test {
            (awaitItem() as ShowingWebContent).verifyCanExitWithoutConfirmation()
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenInitialStateThenCanNavigateBackwards() = runTest {
        testee.viewState.test {
            assertTrue((awaitItem() as ShowingWebContent).urlActions.backButton == NavigateBack)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenBackButtonPressedAndWebViewCannotGoBackThenCancelInContextSignUpState() = runTest {
        testee.onBackButtonPressed("", false)
        testee.viewState.test {
            awaitItem() as CancellingInContextSignUp
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenUrlNotKnownAndBackButtonPressedThenDefaultBackActionTaken() = runTest {
        testee.onBackButtonPressed("", true)
        testee.viewState.test {
            awaitItem() as NavigatingBack
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenPageFinishedIsUnknownUrlThenDefaultExitActionTaken() = runTest {
        testee.onPageFinished(UNKNOWN_URL)
        testee.viewState.test {
            (awaitItem() as ShowingWebContent).verifyCanExitWithoutConfirmation()
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenBackButtonPressedWebViewCanGoBackAndUrlInAllowableBackListThenNavigateBackState() = runTest {
        testee.onBackButtonPressed(URL_IN_ALLOWED_BACK_NAVIGATION_LIST, true)
        testee.viewState.test {
            awaitItem() as NavigatingBack
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenBackNavigationConsumedThenStateReturnsToViewingWebContent() = runTest {
        testee.onBackButtonPressed(URL_IN_ALLOWED_BACK_NAVIGATION_LIST, true)
        testee.viewState.test {
            awaitItem() as NavigatingBack

            testee.consumedBackNavigation(URL_IN_ALLOWED_BACK_NAVIGATION_LIST)

            awaitItem() as ShowingWebContent
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenPageFinishedIsUrlOnAllowedBackListThenCanExitWithoutConfirmation() = runTest {
        testee.onPageFinished(URL_IN_ALLOWED_BACK_NAVIGATION_LIST)
        testee.viewState.test {
            (awaitItem() as ShowingWebContent).verifyCanExitWithoutConfirmation()
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenPageFinishedIsUrlThatRequiresExitConfirmationThenConfirmationRequired() = runTest {
        testee.onPageFinished(URL_REQUIRES_EXIT_CONFIRMATION)
        testee.viewState.test {
            (awaitItem() as ShowingWebContent).verifyCanExitWithConfirmation()
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenUrlIsLandingPageAndNotSignedInThenDoesNotExit() = runTest {
        testee.signedInStateUpdated(signedIn = false, url = "https://duckduckgo.com/email/")
        testee.viewState.test {
            assertTrue(awaitItem() is ShowingWebContent)
            ensureAllEventsConsumed()
        }
    }

    @Test
    fun whenUserCancelsEarlyExitThenPixelFired() {
        testee.onUserDecidedNotToCancelInContextSignUp()
        verify(pixel).fire(EMAIL_PROTECTION_IN_CONTEXT_MODAL_EXIT_EARLY_CANCEL)
    }

    @Test
    fun whenUserConfirmsEarlyExitThenPixelFired() {
        testee.onUserConfirmedCancellationOfInContextSignUp()
        verify(pixel).fire(EMAIL_PROTECTION_IN_CONTEXT_MODAL_EXIT_EARLY_CONFIRM)
    }

    @Test
    fun whenUserCancelsWithoutConfirmationThenPixelFired() {
        testee.userCancelledSignupWithoutConfirmation()
        verify(pixel).fire(EMAIL_PROTECTION_IN_CONTEXT_MODAL_DISMISSED)
    }

    @Test
    fun whenUserCancelsWithoutConfirmationThenCancellationViewStateSent() = runTest {
        testee.userCancelledSignupWithoutConfirmation()
        testee.viewState.test {
            assertTrue(awaitItem() is CancellingInContextSignUp)
            ensureAllEventsConsumed()
        }
    }

    @Test
    fun whenLoadedStartingUrlThenPixelFired() {
        testee.loadedStartingUrl()
        verify(pixel).fire(EMAIL_PROTECTION_IN_CONTEXT_MODAL_DISPLAYED)
    }

    private fun ShowingWebContent.verifyCanExitWithoutConfirmation() {
        assertTrue(this.urlActions.exitButton == ExitWithoutConfirmation)
    }

    private fun ShowingWebContent.verifyCanExitWithConfirmation() {
        assertTrue(this.urlActions.exitButton == ExitWithConfirmation)
    }

    companion object {
        private const val URL_IN_ALLOWED_BACK_NAVIGATION_LIST = "https://duckduckgo.com/email/"
        private const val URL_REQUIRES_EXIT_CONFIRMATION = "https://duckduckgo.com/email/choose-address"
        private const val UNKNOWN_URL = "https://foo.com"
    }
}
