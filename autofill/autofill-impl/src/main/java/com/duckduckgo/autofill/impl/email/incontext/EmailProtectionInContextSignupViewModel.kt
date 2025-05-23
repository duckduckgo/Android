/*
 * Copyright (c) 2023 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.autofill.impl.email.incontext

import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autofill.impl.email.incontext.EmailProtectionInContextSignupViewModel.BackButtonAction.NavigateBack
import com.duckduckgo.autofill.impl.email.incontext.EmailProtectionInContextSignupViewModel.Companion.Urls.CHOOSE_ADDRESS
import com.duckduckgo.autofill.impl.email.incontext.EmailProtectionInContextSignupViewModel.Companion.Urls.DEFAULT_URL_ACTIONS
import com.duckduckgo.autofill.impl.email.incontext.EmailProtectionInContextSignupViewModel.Companion.Urls.EMAIL_VERIFICATION_LINK_URL
import com.duckduckgo.autofill.impl.email.incontext.EmailProtectionInContextSignupViewModel.Companion.Urls.IN_CONTEXT_SUCCESS
import com.duckduckgo.autofill.impl.email.incontext.EmailProtectionInContextSignupViewModel.Companion.Urls.REVIEW_INPUT
import com.duckduckgo.autofill.impl.email.incontext.EmailProtectionInContextSignupViewModel.ExitButtonAction.ExitTreatAsSuccess
import com.duckduckgo.autofill.impl.email.incontext.EmailProtectionInContextSignupViewModel.ExitButtonAction.ExitWithConfirmation
import com.duckduckgo.autofill.impl.email.incontext.EmailProtectionInContextSignupViewModel.ExitButtonAction.ExitWithoutConfirmation
import com.duckduckgo.autofill.impl.email.incontext.EmailProtectionInContextSignupViewModel.ViewState.CancellingInContextSignUp
import com.duckduckgo.autofill.impl.email.incontext.EmailProtectionInContextSignupViewModel.ViewState.ExitingAsSuccess
import com.duckduckgo.autofill.impl.email.incontext.EmailProtectionInContextSignupViewModel.ViewState.NavigatingBack
import com.duckduckgo.autofill.impl.email.incontext.EmailProtectionInContextSignupViewModel.ViewState.ShowingWebContent
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.EMAIL_PROTECTION_IN_CONTEXT_MODAL_DISMISSED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.EMAIL_PROTECTION_IN_CONTEXT_MODAL_DISPLAYED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.EMAIL_PROTECTION_IN_CONTEXT_MODAL_EXIT_EARLY_CANCEL
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.EMAIL_PROTECTION_IN_CONTEXT_MODAL_EXIT_EARLY_CONFIRM
import com.duckduckgo.common.utils.absoluteString
import com.duckduckgo.di.scopes.ActivityScope
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import logcat.LogPriority.INFO
import logcat.LogPriority.VERBOSE
import logcat.logcat

@ContributesViewModel(ActivityScope::class)
class EmailProtectionInContextSignupViewModel @Inject constructor(
    private val pixel: Pixel,
) : ViewModel() {

    private val _viewState = MutableStateFlow<ViewState>(ShowingWebContent(urlActions = DEFAULT_URL_ACTIONS))
    val viewState: StateFlow<ViewState> = _viewState

    fun onPageFinished(url: String) {
        val urlActions = url.getUrlActions()
        logcat(VERBOSE) { "EmailProtectionInContextSignup: onPageFinished: $url, urlActions=$urlActions" }

        _viewState.value = ShowingWebContent(urlActions = urlActions)
    }

    fun onBackButtonPressed(
        url: String?,
        canGoBack: Boolean,
    ) {
        logcat(VERBOSE) { "onBackButtonPressed: $url, canGoBack=$canGoBack" }

        // if WebView can't go back, then we're at the first stage or something's gone wrong. Either way, time to cancel out of the screen.
        if (!canGoBack) {
            terminateSignUpFlowAsCancellation()
            return
        }

        val urlActions = url.getUrlActions()
        when (urlActions.backButton) {
            BackButtonAction.ExitTreatAsSuccess -> {
                _viewState.value = ExitingAsSuccess
                return
            }

            NavigateBack -> {
                _viewState.value = NavigatingBack
                return
            }
        }
    }

    fun consumedBackNavigation(currentUrl: String?) {
        _viewState.value = ShowingWebContent(urlActions = currentUrl.getUrlActions())
    }

    private fun String?.getUrlActions(): UrlActions {
        if (this == null) return DEFAULT_URL_ACTIONS

        return urlActions[this.toUri().absoluteString] ?: DEFAULT_URL_ACTIONS
    }

    fun onUserConfirmedCancellationOfInContextSignUp() {
        pixel.fire(EMAIL_PROTECTION_IN_CONTEXT_MODAL_EXIT_EARLY_CONFIRM)
        terminateSignUpFlowAsCancellation()
    }

    fun onUserDecidedNotToCancelInContextSignUp() {
        pixel.fire(EMAIL_PROTECTION_IN_CONTEXT_MODAL_EXIT_EARLY_CANCEL)
    }

    private fun terminateSignUpFlowAsCancellation() {
        pixel.fire(EMAIL_PROTECTION_IN_CONTEXT_MODAL_DISMISSED)
        _viewState.value = CancellingInContextSignUp
    }

    fun signedInStateUpdated(
        signedIn: Boolean,
        url: String?,
    ) {
        logcat(INFO) { "Now signed in: $signedIn. Current URL is $url" }

        if (!signedIn) return

        if (url?.contains(EMAIL_VERIFICATION_LINK_URL) == true) {
            logcat { "Detected email verification link" }
            _viewState.value = ExitingAsSuccess
        }
    }

    fun userCancelledSignupWithoutConfirmation() {
        terminateSignUpFlowAsCancellation()
    }

    fun loadedStartingUrl() {
        pixel.fire(EMAIL_PROTECTION_IN_CONTEXT_MODAL_DISPLAYED)
    }

    sealed interface ViewState {
        data class ShowingWebContent(val urlActions: UrlActions) : ViewState
        object NavigatingBack : ViewState
        object CancellingInContextSignUp : ViewState
        object ConfirmingCancellationOfInContextSignUp : ViewState
        object ExitingAsSuccess : ViewState
    }

    data class UrlActions(
        val backButton: BackButtonAction,
        val exitButton: ExitButtonAction,
    ) {
        override fun toString(): String {
            return "UrlActions(backButton=${backButton.javaClass.simpleName}, exitButton=${exitButton.javaClass.simpleName})"
        }
    }

    sealed interface BackButtonAction {
        object NavigateBack : BackButtonAction
        object ExitTreatAsSuccess : BackButtonAction
    }

    sealed interface ExitButtonAction {
        object Disabled : ExitButtonAction
        object ExitWithoutConfirmation : ExitButtonAction
        object ExitTreatAsSuccess : ExitButtonAction
        object ExitWithConfirmation : ExitButtonAction
    }

    companion object {
        internal object Urls {
            const val START = "https://duckduckgo.com/email/start"
            const val CHOOSE_ADDRESS = "https://duckduckgo.com/email/choose-address"
            const val REVIEW_INPUT = "https://duckduckgo.com/email/review"
            const val IN_CONTEXT_SUCCESS = "https://duckduckgo.com/email/welcome-incontext"

            const val EMAIL_VERIFICATION_LINK_URL = "https://duckduckgo.com/email/login?"

            val DEFAULT_URL_ACTIONS = UrlActions(backButton = NavigateBack, exitButton = ExitWithoutConfirmation)
        }

        /**
         * There are specific business logic around which actions can be taken for each screen in the webflow.
         *
         * For example, in some parts of the flow the users are to be able to exit without confirmation, but in others they must be asked to confirm.
         * How we handle them trying to go back will also vary by screen.
         */
        private val urlActions: Map<String, UrlActions> = mutableMapOf<String, UrlActions>().also {
            it[CHOOSE_ADDRESS] = UrlActions(backButton = NavigateBack, exitButton = ExitWithConfirmation)
            it[REVIEW_INPUT] = UrlActions(backButton = NavigateBack, exitButton = ExitWithConfirmation)
            it[IN_CONTEXT_SUCCESS] = UrlActions(backButton = BackButtonAction.ExitTreatAsSuccess, exitButton = ExitTreatAsSuccess)
        }
    }
}
