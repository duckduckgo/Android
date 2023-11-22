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
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autofill.api.CredentialSavePickerDialog
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.AutofillFireproofDialogSuppressor
import com.duckduckgo.autofill.impl.R
import com.duckduckgo.autofill.impl.databinding.ContentAutofillSaveNewCredentialsBinding
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_SAVE_LOGIN_PROMPT_DISMISSED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_SAVE_LOGIN_PROMPT_SAVED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_SAVE_LOGIN_PROMPT_SHOWN
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_SAVE_PASSWORD_PROMPT_DISMISSED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_SAVE_PASSWORD_PROMPT_SAVED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_SAVE_PASSWORD_PROMPT_SHOWN
import com.duckduckgo.autofill.impl.ui.credential.dialog.animateClosed
import com.duckduckgo.autofill.impl.ui.credential.management.sorting.InitialExtractor
import com.duckduckgo.autofill.impl.ui.credential.saving.AutofillSavingCredentialsDialogFragment.AutofillSavingPixelEventNames.Companion.pixelNameDialogAccepted
import com.duckduckgo.autofill.impl.ui.credential.saving.AutofillSavingCredentialsDialogFragment.AutofillSavingPixelEventNames.Companion.pixelNameDialogDismissed
import com.duckduckgo.autofill.impl.ui.credential.saving.AutofillSavingCredentialsDialogFragment.AutofillSavingPixelEventNames.Companion.pixelNameDialogShown
import com.duckduckgo.autofill.impl.ui.credential.saving.AutofillSavingCredentialsDialogFragment.AutofillSavingPixelEventNames.Companion.saveType
import com.duckduckgo.autofill.impl.ui.credential.saving.AutofillSavingCredentialsDialogFragment.CredentialSaveType.PasswordOnly
import com.duckduckgo.autofill.impl.ui.credential.saving.AutofillSavingCredentialsDialogFragment.CredentialSaveType.UsernameAndPassword
import com.duckduckgo.autofill.impl.ui.credential.saving.AutofillSavingCredentialsDialogFragment.CredentialSaveType.UsernameOnly
import com.duckduckgo.autofill.impl.ui.credential.saving.AutofillSavingCredentialsDialogFragment.DialogEvent.Accepted
import com.duckduckgo.autofill.impl.ui.credential.saving.AutofillSavingCredentialsDialogFragment.DialogEvent.Dismissed
import com.duckduckgo.autofill.impl.ui.credential.saving.AutofillSavingCredentialsDialogFragment.DialogEvent.Shown
import com.duckduckgo.autofill.impl.ui.credential.saving.declines.AutofillDeclineCounter
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
import kotlinx.coroutines.launch
import timber.log.Timber

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
    lateinit var initialExtractor: InitialExtractor

    @Inject
    lateinit var dispatcherProvider: DispatcherProvider

    @Inject
    lateinit var autofillFireproofDialogSuppressor: AutofillFireproofDialogSuppressor

    /**
     * To capture all the ways the BottomSheet can be dismissed, we might end up with onCancel being called when we don't want it
     * This flag is set to true when taking an action which dismisses the dialog, but should not be treated as a cancellation.
     */
    private var ignoreCancellationEvents = false

    private val viewModel by lazy {
        ViewModelProvider(this, viewModelFactory)[AutofillSavingCredentialsViewModel::class.java]
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
        pixelNameDialogEvent(Shown)?.let { pixel.fire(it) }
        autofillFireproofDialogSuppressor.autofillSaveOrUpdateDialogVisibilityChanged(visible = true)
        viewModel.userPromptedToSaveCredentials()

        val binding = ContentAutofillSaveNewCredentialsBinding.inflate(inflater, container, false)
        configureViews(binding, getCredentialsToSave())
        return binding.root
    }

    private fun configureViews(
        binding: ContentAutofillSaveNewCredentialsBinding,
        credentials: LoginCredentials,
    ) {
        (dialog as BottomSheetDialog).behavior.state = BottomSheetBehavior.STATE_EXPANDED
        configureCloseButtons(binding)
        configureSaveButton(binding)
    }

    private fun configureSaveButton(binding: ContentAutofillSaveNewCredentialsBinding) {
        binding.saveLoginButton.setOnClickListener {
            Timber.v("onSave: AutofillSavingCredentialsDialogFragment. User saved credentials")

            pixelNameDialogEvent(Accepted)?.let { pixel.fire(it) }

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
        // need a reference to this early as it will could null after launching the coroutine
        val parentFragmentForResult = parentFragment

        if (ignoreCancellationEvents) {
            Timber.v("onCancel: Ignoring cancellation event")
            return
        }

        Timber.v("onCancel: AutofillSavingCredentialsDialogFragment. User declined to save credentials")

        appCoroutineScope.launch(dispatcherProvider.io()) {
            autofillDeclineCounter.userDeclinedToSaveCredentials(getOriginalUrl().extractDomain())

            if (autofillDeclineCounter.shouldPromptToDisableAutofill()) {
                parentFragmentForResult?.setFragmentResult(CredentialSavePickerDialog.resultKeyShouldPromptToDisableAutofill(getTabId()), Bundle())
            } else {
                autofillFireproofDialogSuppressor.autofillSaveOrUpdateDialogVisibilityChanged(visible = false)
            }
        }

        pixelNameDialogEvent(Dismissed)?.let { pixel.fire(it) }
    }

    private fun configureCloseButtons(binding: ContentAutofillSaveNewCredentialsBinding) {
        binding.closeButton.setOnClickListener { animateClosed() }
        binding.cancelButton.setOnClickListener { animateClosed() }
    }

    private fun animateClosed() {
        (dialog as BottomSheetDialog).animateClosed()
    }

    private fun pixelNameDialogEvent(dialogEvent: DialogEvent): AutofillPixelNames? {
        val saveType = getCredentialsToSave().saveType()
        return when (dialogEvent) {
            is Shown -> pixelNameDialogShown(saveType)
            is Dismissed -> pixelNameDialogDismissed(saveType)
            is Accepted -> pixelNameDialogAccepted(saveType)
            else -> null
        }
    }

    internal sealed interface CredentialSaveType {
        object UsernameAndPassword : CredentialSaveType
        object UsernameOnly : CredentialSaveType
        object PasswordOnly : CredentialSaveType
    }

    private interface DialogEvent {
        object Shown : DialogEvent
        object Dismissed : DialogEvent
        object Accepted : DialogEvent
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

            fun pixelNameDialogShown(credentialSaveType: CredentialSaveType): AutofillPixelNames? {
                return when (credentialSaveType) {
                    UsernameAndPassword -> AUTOFILL_SAVE_LOGIN_PROMPT_SHOWN
                    PasswordOnly -> AUTOFILL_SAVE_PASSWORD_PROMPT_SHOWN
                    else -> null
                }
            }

            fun pixelNameDialogDismissed(credentialSaveType: CredentialSaveType): AutofillPixelNames? {
                return when (credentialSaveType) {
                    UsernameAndPassword -> AUTOFILL_SAVE_LOGIN_PROMPT_DISMISSED
                    PasswordOnly -> AUTOFILL_SAVE_PASSWORD_PROMPT_DISMISSED
                    else -> null
                }
            }

            fun pixelNameDialogAccepted(credentialSaveType: CredentialSaveType): AutofillPixelNames? {
                return when (credentialSaveType) {
                    UsernameAndPassword -> AUTOFILL_SAVE_LOGIN_PROMPT_SAVED
                    PasswordOnly -> AUTOFILL_SAVE_PASSWORD_PROMPT_SAVED
                    else -> null
                }
            }
        }
    }
}
