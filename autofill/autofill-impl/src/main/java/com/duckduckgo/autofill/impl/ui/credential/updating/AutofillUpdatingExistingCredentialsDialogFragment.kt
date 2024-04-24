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

package com.duckduckgo.autofill.impl.ui.credential.updating

import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.BundleCompat
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.ViewModelProvider
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autofill.api.AutofillWebMessageRequest
import com.duckduckgo.autofill.api.CredentialUpdateExistingCredentialsDialog
import com.duckduckgo.autofill.api.CredentialUpdateExistingCredentialsDialog.Companion.KEY_CREDENTIALS
import com.duckduckgo.autofill.api.CredentialUpdateExistingCredentialsDialog.Companion.KEY_CREDENTIAL_UPDATE_TYPE
import com.duckduckgo.autofill.api.CredentialUpdateExistingCredentialsDialog.Companion.KEY_TAB_ID
import com.duckduckgo.autofill.api.CredentialUpdateExistingCredentialsDialog.Companion.KEY_URL
import com.duckduckgo.autofill.api.CredentialUpdateExistingCredentialsDialog.CredentialUpdateType
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.AutofillFireproofDialogSuppressor
import com.duckduckgo.autofill.impl.R
import com.duckduckgo.autofill.impl.databinding.ContentAutofillUpdateExistingCredentialsBinding
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_UPDATE_LOGIN_PROMPT_DISMISSED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_UPDATE_LOGIN_PROMPT_SAVED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_UPDATE_LOGIN_PROMPT_SHOWN
import com.duckduckgo.autofill.impl.ui.credential.dialog.animateClosed
import com.duckduckgo.autofill.impl.ui.credential.updating.AutofillUpdatingExistingCredentialsDialogFragment.DialogEvent.Dismissed
import com.duckduckgo.autofill.impl.ui.credential.updating.AutofillUpdatingExistingCredentialsDialogFragment.DialogEvent.Shown
import com.duckduckgo.autofill.impl.ui.credential.updating.AutofillUpdatingExistingCredentialsDialogFragment.DialogEvent.Updated
import com.duckduckgo.common.utils.FragmentViewModelFactory
import com.duckduckgo.di.scopes.FragmentScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject
import timber.log.Timber

@InjectWith(FragmentScope::class)
class AutofillUpdatingExistingCredentialsDialogFragment : BottomSheetDialogFragment(), CredentialUpdateExistingCredentialsDialog {

    override fun getTheme(): Int = R.style.AutofillBottomSheetDialogTheme

    @Inject
    lateinit var viewModelFactory: FragmentViewModelFactory

    @Inject
    lateinit var pixel: Pixel

    @Inject
    lateinit var autofillFireproofDialogSuppressor: AutofillFireproofDialogSuppressor

    /**
     * To capture all the ways the BottomSheet can be dismissed, we might end up with onCancel being called when we don't want it
     * This flag is set to true when taking an action which dismisses the dialog, but should not be treated as a cancellation.
     */
    private var ignoreCancellationEvents = false

    private val viewModel by lazy {
        ViewModelProvider(this, viewModelFactory)[AutofillUpdatingExistingCredentialViewModel::class.java]
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

        val binding = ContentAutofillUpdateExistingCredentialsBinding.inflate(inflater, container, false)
        configureViews(binding)
        return binding.root
    }

    private fun configureViews(binding: ContentAutofillUpdateExistingCredentialsBinding) {
        (dialog as BottomSheetDialog).behavior.state = BottomSheetBehavior.STATE_EXPANDED
        val credentials = getCredentialsToSave()
        val webMessageRequest = getWebMessageRequest()
        val updateType = getUpdateType()
        Timber.v("Update type is $updateType")

        configureDialogTitle(binding, updateType)
        configureCloseButtons(binding)
        configureUpdatedFieldPreview(binding, credentials, updateType)
        configureUpdateButton(binding, webMessageRequest, credentials, updateType)
    }

    private fun configureDialogTitle(
        binding: ContentAutofillUpdateExistingCredentialsBinding,
        updateType: CredentialUpdateType,
    ) {
        binding.dialogTitle.text = when (updateType) {
            CredentialUpdateType.Username -> getString(R.string.updateLoginDialogTitleUpdateUsername)
            CredentialUpdateType.Password -> {
                val username = (getCredentialsToSave().username ?: "")
                val usernameLine = viewModel.ellipsizeIfNecessary(username)
                getString(R.string.updateLoginDialogTitleLine, usernameLine)
            }
        }
    }

    private fun configureUpdateButton(
        binding: ContentAutofillUpdateExistingCredentialsBinding,
        autofillWebMessageRequest: AutofillWebMessageRequest,
        credentials: LoginCredentials,
        updateType: CredentialUpdateType,
    ) {
        binding.updateCredentialsButton.text = when (updateType) {
            CredentialUpdateType.Username -> getString(R.string.updateLoginDialogButtonUpdateUsername)
            CredentialUpdateType.Password -> getString(R.string.updateLoginDialogButtonUpdatePassword)
        }

        binding.updateCredentialsButton.setOnClickListener {
            pixelNameDialogEvent(Updated)?.let { pixel.fire(it) }

            val result = Bundle().also {
                it.putParcelable(KEY_URL, autofillWebMessageRequest)
                it.putParcelable(KEY_CREDENTIALS, credentials)
                it.putParcelable(KEY_CREDENTIAL_UPDATE_TYPE, getUpdateType())
            }
            parentFragment?.setFragmentResult(CredentialUpdateExistingCredentialsDialog.resultKeyCredentialUpdated(getTabId()), result)

            ignoreCancellationEvents = true
            animateClosed()
        }
    }

    private fun configureUpdatedFieldPreview(
        binding: ContentAutofillUpdateExistingCredentialsBinding,
        credentials: LoginCredentials,
        updateType: CredentialUpdateType,
    ) {
        binding.dialogSubtitle.text = when (updateType) {
            CredentialUpdateType.Username -> credentials.username
            CredentialUpdateType.Password -> getString(R.string.updateLoginUpdatePasswordExplanation)
        }
    }

    private fun configureCloseButtons(binding: ContentAutofillUpdateExistingCredentialsBinding) {
        binding.closeButton.setOnClickListener { animateClosed() }
        binding.cancelButton.setOnClickListener { animateClosed() }
    }

    private fun animateClosed() {
        (dialog as BottomSheetDialog).animateClosed()
    }

    override fun onCancel(dialog: DialogInterface) {
        if (ignoreCancellationEvents) {
            Timber.v("onCancel: Ignoring cancellation event")
            return
        }

        Timber.v("onCancel: AutofillUpdatingExistingCredentialsDialogFragment. User declined to update credentials")
        autofillFireproofDialogSuppressor.autofillSaveOrUpdateDialogVisibilityChanged(visible = false)
        pixelNameDialogEvent(Dismissed)?.let { pixel.fire(it) }
    }

    private fun pixelNameDialogEvent(dialogEvent: DialogEvent): AutofillPixelNames? {
        return when (dialogEvent) {
            is Shown -> AUTOFILL_UPDATE_LOGIN_PROMPT_SHOWN
            is Dismissed -> AUTOFILL_UPDATE_LOGIN_PROMPT_DISMISSED
            is Updated -> AUTOFILL_UPDATE_LOGIN_PROMPT_SAVED
            else -> null
        }
    }

    private interface DialogEvent {
        object Shown : DialogEvent
        object Dismissed : DialogEvent
        object Updated : DialogEvent
    }

    private fun getCredentialsToSave() = BundleCompat.getParcelable(requireArguments(), KEY_CREDENTIALS, LoginCredentials::class.java)!!
    private fun getTabId() = arguments?.getString(KEY_TAB_ID)!!
    private fun getWebMessageRequest() = BundleCompat.getParcelable(requireArguments(), KEY_URL, AutofillWebMessageRequest::class.java)!!
    private fun getUpdateType() = BundleCompat.getParcelable(requireArguments(), KEY_CREDENTIAL_UPDATE_TYPE, CredentialUpdateType::class.java)!!

    companion object {

        fun instance(
            autofillWebMessageRequest: AutofillWebMessageRequest,
            credentials: LoginCredentials,
            tabId: String,
            credentialUpdateType: CredentialUpdateType,
        ): AutofillUpdatingExistingCredentialsDialogFragment {
            val fragment = AutofillUpdatingExistingCredentialsDialogFragment()
            fragment.arguments =
                Bundle().also {
                    it.putParcelable(KEY_URL, autofillWebMessageRequest)
                    it.putParcelable(KEY_CREDENTIALS, credentials)
                    it.putString(KEY_TAB_ID, tabId)
                    it.putParcelable(KEY_CREDENTIAL_UPDATE_TYPE, credentialUpdateType)
                }
            return fragment
        }
    }
}
