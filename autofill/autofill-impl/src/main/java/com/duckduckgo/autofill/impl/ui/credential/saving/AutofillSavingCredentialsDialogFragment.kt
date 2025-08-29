/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.autofill.impl.ui.credential.saving

import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autofill.api.AutofillFeature
import com.duckduckgo.autofill.api.CredentialSavePickerDialog
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.AutofillFireproofDialogSuppressor
import com.duckduckgo.autofill.impl.R
import com.duckduckgo.autofill.impl.databinding.ContentAutofillSaveNewCredentialsBinding
import com.duckduckgo.autofill.impl.partialsave.PartialCredentialSaveStore
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_NEVER_SAVE_FOR_THIS_SITE_USER_SELECTED_FROM_SAVE_DIALOG
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_ONBOARDING_SAVE_PROMPT_DISMISSED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_ONBOARDING_SAVE_PROMPT_EXCLUDE
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_ONBOARDING_SAVE_PROMPT_SAVED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_ONBOARDING_SAVE_PROMPT_SHOWN
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_SAVE_LOGIN_PROMPT_DISMISSED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_SAVE_LOGIN_PROMPT_SAVED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_SAVE_LOGIN_PROMPT_SHOWN
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_SAVE_PASSWORD_PROMPT_DISMISSED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_SAVE_PASSWORD_PROMPT_SAVED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_SAVE_PASSWORD_PROMPT_SHOWN
import com.duckduckgo.autofill.impl.ui.credential.dialog.animateClosed
import com.duckduckgo.autofill.impl.ui.credential.saving.AutofillSavingCredentialsDialogFragment.AutofillSavingPixelEventNames.Companion.pixelNameDialogAccepted
import com.duckduckgo.autofill.impl.ui.credential.saving.AutofillSavingCredentialsDialogFragment.AutofillSavingPixelEventNames.Companion.pixelNameDialogDismissed
import com.duckduckgo.autofill.impl.ui.credential.saving.AutofillSavingCredentialsDialogFragment.AutofillSavingPixelEventNames.Companion.pixelNameDialogExclude
import com.duckduckgo.autofill.impl.ui.credential.saving.AutofillSavingCredentialsDialogFragment.AutofillSavingPixelEventNames.Companion.pixelNameDialogShown
import com.duckduckgo.autofill.impl.ui.credential.saving.AutofillSavingCredentialsDialogFragment.AutofillSavingPixelEventNames.Companion.saveType
import com.duckduckgo.autofill.impl.ui.credential.saving.AutofillSavingCredentialsDialogFragment.CredentialSaveType.PasswordOnly
import com.duckduckgo.autofill.impl.ui.credential.saving.AutofillSavingCredentialsDialogFragment.CredentialSaveType.UsernameAndPassword
import com.duckduckgo.autofill.impl.ui.credential.saving.AutofillSavingCredentialsDialogFragment.CredentialSaveType.UsernameOnly
import com.duckduckgo.autofill.impl.ui.credential.saving.AutofillSavingCredentialsDialogFragment.DialogEvent.Accepted
import com.duckduckgo.autofill.impl.ui.credential.saving.AutofillSavingCredentialsDialogFragment.DialogEvent.Dismissed
import com.duckduckgo.autofill.impl.ui.credential.saving.AutofillSavingCredentialsDialogFragment.DialogEvent.Exclude
import com.duckduckgo.autofill.impl.ui.credential.saving.AutofillSavingCredentialsDialogFragment.DialogEvent.Shown
import com.duckduckgo.autofill.impl.ui.credential.saving.AutofillSavingCredentialsViewModel.ViewState
import com.duckduckgo.autofill.impl.ui.credential.saving.declines.AutofillDeclineCounter
import com.duckduckgo.common.ui.view.button.DaxButton
import com.duckduckgo.common.ui.view.prependIconToText
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.FragmentViewModelFactory
import com.duckduckgo.common.utils.extractDomain
import com.duckduckgo.di.scopes.FragmentScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart.LAZY
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority.VERBOSE
import logcat.logcat

@InjectWith(FragmentScope::class)
class AutofillSavingCredentialsDialogFragment : BottomSheetDialogFragment(), CredentialSavePickerDialog {

    override fun getTheme(): Int = R.style.AutofillBottomSheetDialogTheme

    @Inject
    lateinit var faviconManager: FaviconManager

    @Inject
    lateinit var viewModelFactory: FragmentViewModelFactory

    @Inject
    lateinit var autofillDeclineCounter: AutofillDeclineCounter

    @Inject
    @AppCoroutineScope
    lateinit var appCoroutineScope: CoroutineScope

    @Inject
    lateinit var pixel: Pixel

    @Inject
    lateinit var dispatcherProvider: DispatcherProvider

    @Inject
    lateinit var autofillFireproofDialogSuppressor: AutofillFireproofDialogSuppressor

    @Inject
    lateinit var partialCredentialSaveStore: PartialCredentialSaveStore

    @Inject
    lateinit var autofillFeature: AutofillFeature

    /**
     * To capture all the ways the BottomSheet can be dismissed, we might end up with onCancel being called when we don't want it
     * This flag is set to true when taking an action which dismisses the dialog, but should not be treated as a cancellation.
     */
    private var ignoreCancellationEvents = false

    private lateinit var keyFeaturesContainer: ViewGroup

    private lateinit var secondaryButton: DaxButton

    private val viewModel by lazy {
        ViewModelProvider(this, viewModelFactory)[AutofillSavingCredentialsViewModel::class.java]
    }

    private val wasUsernameBackFilled: Deferred<Boolean> = lifecycleScope.async(start = LAZY) {
        val usernameToSave = getCredentialsToSave().username ?: return@async false
        partialCredentialSaveStore.wasBackFilledRecently(url = getOriginalUrl(), username = usernameToSave).also {
            logcat(VERBOSE) { "Determined that username was ${if (it) "" else "not "}backFilled" }
        }
    }

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState != null) {
            // If being created after a configuration change, dismiss the dialog as the WebView will be re-created too
            dismiss()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        autofillFireproofDialogSuppressor.autofillSaveOrUpdateDialogVisibilityChanged(visible = true)
        viewModel.userPromptedToSaveCredentials()

        val binding = ContentAutofillSaveNewCredentialsBinding.inflate(inflater, container, false)
        configureViews(binding)
        observeViewModel()
        return binding.root
    }

    private fun observeViewModel() {
        viewModel.viewState
            .flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
            .onEach { viewState ->
                renderViewState(viewState)
            }.launchIn(lifecycleScope)
    }

    private fun renderViewState(viewState: ViewState) {
        keyFeaturesContainer.isVisible = viewState.expandedDialog
        configureSecondaryButtons(viewState.expandedDialog)
        (dialog as? BottomSheetDialog)?.behavior?.isDraggable = viewState.expandedDialog
        pixelNameDialogEvent(Shown, viewState.expandedDialog)?.let {
            lifecycleScope.launch {
                pixel.fire(it, paramsForPixel())
            }
        }
    }

    private fun configureViews(binding: ContentAutofillSaveNewCredentialsBinding) {
        keyFeaturesContainer = binding.keyFeaturesContainer
        secondaryButton = binding.secondaryButton
        (dialog as BottomSheetDialog).behavior.state = BottomSheetBehavior.STATE_EXPANDED
        configureCloseButtons(binding)
        configureSaveButton(binding)
        configureSubtitleText(binding)
    }

    private fun configureSubtitleText(binding: ContentAutofillSaveNewCredentialsBinding) {
        binding.onboardingSubtitle.text = binding.root.context.prependIconToText(R.string.saveLoginDialogSubtitle, R.drawable.ic_lock_solid_12)
    }

    private fun configureSaveButton(binding: ContentAutofillSaveNewCredentialsBinding) {
        binding.saveLoginButton.setOnClickListener {
            logcat(VERBOSE) { "onSave: AutofillSavingCredentialsDialogFragment. User saved credentials" }

            pixelNameDialogEvent(Accepted, binding.keyFeaturesContainer.isVisible)?.let {
                lifecycleScope.launch {
                    pixel.fire(it, paramsForPixel())
                }
            }

            lifecycleScope.launch(dispatcherProvider.io()) {
                faviconManager.persistCachedFavicon(getTabId(), getOriginalUrl())
            }

            val result = Bundle().also {
                it.putString(CredentialSavePickerDialog.KEY_URL, getOriginalUrl())
                it.putParcelable(CredentialSavePickerDialog.KEY_CREDENTIALS, getCredentialsToSave())
            }
            parentFragment?.setFragmentResult(CredentialSavePickerDialog.resultKeyUserChoseToSaveCredentials(getTabId()), result)

            ignoreCancellationEvents = true
            animateClosed()
        }
    }

    override fun onCancel(dialog: DialogInterface) {
        if (ignoreCancellationEvents) {
            logcat(VERBOSE) { "onCancel: Ignoring cancellation event" }
            return
        }

        logcat(VERBOSE) { "onCancel: AutofillSavingCredentialsDialogFragment. User declined to save credentials" }

        onUserRejectedToSaveCredentials()

        pixelNameDialogEvent(Dismissed, isOnboardingMode())?.let {
            lifecycleScope.launch {
                pixel.fire(it, paramsForPixel())
            }
        }
    }

    private fun onUserRejectedToSaveCredentials() {
        // need a reference to this early as it could be null after launching the coroutine
        val parentFragmentForResult = parentFragment

        appCoroutineScope.launch(dispatcherProvider.io()) {
            autofillDeclineCounter.userDeclinedToSaveCredentials(getOriginalUrl().extractDomain())

            if (autofillDeclineCounter.shouldPromptToDisableAutofill()) {
                parentFragmentForResult?.setFragmentResult(CredentialSavePickerDialog.resultKeyShouldPromptToDisableAutofill(getTabId()), Bundle())
            } else {
                autofillFireproofDialogSuppressor.autofillSaveOrUpdateDialogVisibilityChanged(visible = false)
            }
        }
    }

    private fun onUserChoseNotNow() {
        pixelNameDialogEvent(Dismissed, isOnboardingMode())?.let {
            lifecycleScope.launch {
                pixel.fire(it, paramsForPixel())
            }
        }

        // this is another way to refuse saving credentials, so ensure that normal logic still runs
        onUserRejectedToSaveCredentials()

        // avoid the standard cancellation logic from running
        ignoreCancellationEvents = true
    }

    private fun onUserChoseNeverSaveThisSite() {
        pixelNameDialogEvent(Exclude, isOnboardingMode())?.let {
            lifecycleScope.launch {
                pixel.fire(it, paramsForPixel())
            }
        }
        viewModel.addSiteToNeverSaveList(getOriginalUrl())

        // this is another way to refuse saving credentials, so ensure that normal logic still runs
        onUserRejectedToSaveCredentials()

        // avoid the standard cancellation logic from running
        ignoreCancellationEvents = true
    }

    /**
     * Close button, back button and tapping outside the dialog all handled in [onCancel]
     * For "never save" button, which is a special way of saying "no" to saving credentials, we need to do additional work to save that choice
     */
    private fun configureCloseButtons(binding: ContentAutofillSaveNewCredentialsBinding) {
        binding.closeButton.setOnClickListener { animateClosed() }
    }

    private fun configureSecondaryButtons(isOnboarding: Boolean) {
        if (isOnboarding) {
            secondaryButton.text = getString(R.string.saveOnboardingLoginDialogSecondaryButton)
            secondaryButton.setOnClickListener {
                onUserChoseNotNow()
                animateClosed()
            }
        } else {
            secondaryButton.text = getString(R.string.saveLoginDialogNeverForThisSite)
            secondaryButton.setOnClickListener {
                onUserChoseNeverSaveThisSite()
                animateClosed()
            }
        }
    }

    private fun animateClosed() {
        (dialog as BottomSheetDialog).animateClosed()
    }

    private fun pixelNameDialogEvent(dialogEvent: DialogEvent, onboardingMode: Boolean): AutofillPixelNames? {
        val saveType = getCredentialsToSave().saveType()
        return when (dialogEvent) {
            is Shown -> pixelNameDialogShown(saveType, onboardingMode)
            is Dismissed -> pixelNameDialogDismissed(saveType, onboardingMode)
            is Accepted -> pixelNameDialogAccepted(saveType, onboardingMode)
            is Exclude -> pixelNameDialogExclude(saveType, onboardingMode)
            else -> null
        }
    }

    private suspend fun paramsForPixel(): Map<String, String> {
        return withContext(dispatcherProvider.io()) {
            if (autofillFeature.partialFormSaves().isEnabled()) {
                mapOf(PIXEL_PARAM_WAS_USERNAME_BACKFILLED to wasUsernameBackFilled.await().toString())
            } else {
                emptyMap()
            }
        }
    }

    private fun isOnboardingMode() = if (this::keyFeaturesContainer.isInitialized) {
        keyFeaturesContainer.isVisible
    } else {
        false
    }

    internal sealed interface CredentialSaveType {
        data object UsernameAndPassword : CredentialSaveType
        data object UsernameOnly : CredentialSaveType
        data object PasswordOnly : CredentialSaveType
    }

    private interface DialogEvent {
        object Shown : DialogEvent
        object Dismissed : DialogEvent
        object Accepted : DialogEvent
        object Exclude : DialogEvent
    }

    private fun getCredentialsToSave() = arguments?.getParcelable<LoginCredentials>(CredentialSavePickerDialog.KEY_CREDENTIALS)!!
    private fun getTabId() = arguments?.getString(CredentialSavePickerDialog.KEY_TAB_ID)!!
    private fun getOriginalUrl() = arguments?.getString(CredentialSavePickerDialog.KEY_URL)!!

    companion object {

        fun instance(
            url: String,
            credentials: LoginCredentials,
            tabId: String,
        ): AutofillSavingCredentialsDialogFragment {
            val fragment = AutofillSavingCredentialsDialogFragment()
            fragment.arguments =
                Bundle().also {
                    it.putString(CredentialSavePickerDialog.KEY_URL, url)
                    it.putParcelable(CredentialSavePickerDialog.KEY_CREDENTIALS, credentials)
                    it.putString(CredentialSavePickerDialog.KEY_TAB_ID, tabId)
                }
            return fragment
        }

        private const val PIXEL_PARAM_WAS_USERNAME_BACKFILLED = "backfilled"
    }

    internal class AutofillSavingPixelEventNames {

        companion object {

            fun LoginCredentials.saveType(): CredentialSaveType {
                return if (!username.isNullOrBlank() && !password.isNullOrBlank()) {
                    UsernameAndPassword
                } else if (username.isNullOrBlank()) {
                    PasswordOnly
                } else {
                    UsernameOnly
                }
            }

            fun pixelNameDialogShown(credentialSaveType: CredentialSaveType, onboardingMode: Boolean): AutofillPixelNames? {
                if (onboardingMode) return AUTOFILL_ONBOARDING_SAVE_PROMPT_SHOWN
                return when (credentialSaveType) {
                    UsernameAndPassword -> AUTOFILL_SAVE_LOGIN_PROMPT_SHOWN
                    PasswordOnly -> AUTOFILL_SAVE_PASSWORD_PROMPT_SHOWN
                    else -> null
                }
            }

            fun pixelNameDialogDismissed(credentialSaveType: CredentialSaveType, onboardingMode: Boolean): AutofillPixelNames? {
                if (onboardingMode) return AUTOFILL_ONBOARDING_SAVE_PROMPT_DISMISSED
                return when (credentialSaveType) {
                    UsernameAndPassword -> AUTOFILL_SAVE_LOGIN_PROMPT_DISMISSED
                    PasswordOnly -> AUTOFILL_SAVE_PASSWORD_PROMPT_DISMISSED
                    else -> null
                }
            }

            fun pixelNameDialogAccepted(credentialSaveType: CredentialSaveType, onboardingMode: Boolean): AutofillPixelNames? {
                if (onboardingMode) return AUTOFILL_ONBOARDING_SAVE_PROMPT_SAVED
                return when (credentialSaveType) {
                    UsernameAndPassword -> AUTOFILL_SAVE_LOGIN_PROMPT_SAVED
                    PasswordOnly -> AUTOFILL_SAVE_PASSWORD_PROMPT_SAVED
                    else -> null
                }
            }

            fun pixelNameDialogExclude(saveType: CredentialSaveType, onboardingMode: Boolean): AutofillPixelNames {
                if (onboardingMode) return AUTOFILL_ONBOARDING_SAVE_PROMPT_EXCLUDE
                return AUTOFILL_NEVER_SAVE_FOR_THIS_SITE_USER_SELECTED_FROM_SAVE_DIALOG
            }
        }
    }
}
