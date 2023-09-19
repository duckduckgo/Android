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
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.FragmentViewModelFactory
import com.duckduckgo.app.global.extractDomain
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autofill.api.CredentialUpdateExistingCredentialsDialog
import com.duckduckgo.autofill.api.CredentialUpdateExistingCredentialsDialog.CredentialUpdateType
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
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
import com.duckduckgo.di.scopes.FragmentScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject
import kotlinx.coroutines.launch
import timber.log.Timber

@InjectWith(FragmentScope::class)
class AutofillUpdatingExistingCredentialsDialogFragment : BottomSheetDialogFragment(), CredentialUpdateExistingCredentialsDialog {

    override fun getTheme(): Int = R.style.AutofillBottomSheetDialogTheme

    @Inject
    lateinit var faviconManager: FaviconManager

    @Inject
    lateinit var viewModelFactory: FragmentViewModelFactory

    @Inject
    lateinit var pixel: Pixel

    @Inject
    lateinit var dispatcherProvider: DispatcherProvider

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

        val binding = ContentAutofillUpdateExistingCredentialsBinding.inflate(inflater, container, false)
        configureViews(binding)
        return binding.root
    }

    private fun configureViews(binding: ContentAutofillUpdateExistingCredentialsBinding) {
        (dialog as BottomSheetDialog).behavior.state = BottomSheetBehavior.STATE_EXPANDED
        val credentials = getCredentialsToSave()
        val originalUrl = getOriginalUrl()
        val updateType = getUpdateType()
        Timber.v("Update type is $updateType")

        configureDialogTitle(binding, updateType)
        configureSiteDetails(binding, originalUrl)
        configureCloseButtons(binding)
        configureUpdatedFieldPreview(binding, credentials, updateType)
        configureUpdateButton(binding, originalUrl, credentials, updateType)
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
        originalUrl: String,
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
                it.putString(CredentialUpdateExistingCredentialsDialog.KEY_URL, originalUrl)
                it.putParcelable(CredentialUpdateExistingCredentialsDialog.KEY_CREDENTIALS, credentials)
                it.putParcelable(CredentialUpdateExistingCredentialsDialog.KEY_CREDENTIAL_UPDATE_TYPE, getUpdateType())
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
        parentFragment?.setFragmentResult(CredentialUpdateExistingCredentialsDialog.resultKeyPromptDismissed(getTabId()), Bundle())
        pixelNameDialogEvent(Dismissed)?.let { pixel.fire(it) }
    }

    private fun configureSiteDetails(
        binding: ContentAutofillUpdateExistingCredentialsBinding,
        originalUrl: String,
    ) {
        val url = originalUrl.extractDomain() ?: originalUrl

        binding.siteName.text = url

        lifecycleScope.launch(dispatcherProvider.io()) {
            faviconManager.loadToViewFromLocalWithPlaceholder(url = url, view = binding.favicon)
        }
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

    private fun getCredentialsToSave() = arguments?.getParcelable<LoginCredentials>(CredentialUpdateExistingCredentialsDialog.KEY_CREDENTIALS)!!
    private fun getTabId() = arguments?.getString(CredentialUpdateExistingCredentialsDialog.KEY_TAB_ID)!!
    private fun getOriginalUrl() = arguments?.getString(CredentialUpdateExistingCredentialsDialog.KEY_URL)!!
    private fun getUpdateType() =
        arguments?.getParcelable<CredentialUpdateType>(CredentialUpdateExistingCredentialsDialog.KEY_CREDENTIAL_UPDATE_TYPE)!!

    companion object {

        fun instance(
            url: String,
            credentials: LoginCredentials,
            tabId: String,
            credentialUpdateType: CredentialUpdateType,
        ): AutofillUpdatingExistingCredentialsDialogFragment {
            val fragment = AutofillUpdatingExistingCredentialsDialogFragment()
            fragment.arguments =
                Bundle().also {
                    it.putString(CredentialUpdateExistingCredentialsDialog.KEY_URL, url)
                    it.putParcelable(CredentialUpdateExistingCredentialsDialog.KEY_CREDENTIALS, credentials)
                    it.putString(CredentialUpdateExistingCredentialsDialog.KEY_TAB_ID, tabId)
                    it.putParcelable(CredentialUpdateExistingCredentialsDialog.KEY_CREDENTIAL_UPDATE_TYPE, credentialUpdateType)
                }
            return fragment
        }
    }
}
