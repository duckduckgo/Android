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

package com.duckduckgo.autofill.ui.credential.saving

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
import com.duckduckgo.app.global.FragmentViewModelFactory
import com.duckduckgo.app.global.extractDomain
import com.duckduckgo.autofill.CredentialSavePickerDialog
import com.duckduckgo.autofill.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.R
import com.duckduckgo.autofill.impl.databinding.ContentAutofillSaveNewCredentialsBinding
import com.duckduckgo.autofill.ui.credential.dialog.animateClosed
import com.duckduckgo.autofill.ui.credential.saving.declines.AutofillDeclineCounter
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.mobile.android.ui.view.gone
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

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

    private val viewModel by lazy {
        ViewModelProvider(this, viewModelFactory)[AutofillSavingCredentialsViewModel::class.java]
    }

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = ContentAutofillSaveNewCredentialsBinding.inflate(inflater, container, false)
        configureViews(binding, getCredentialsToSave())
        return binding.root
    }

    private fun configureViews(binding: ContentAutofillSaveNewCredentialsBinding, credentials: LoginCredentials) {
        (dialog as BottomSheetDialog).behavior.state = BottomSheetBehavior.STATE_EXPANDED
        configureSiteDetails(binding)
        configureTitles(binding, credentials)
        configureCloseButtons(binding)
        configureSaveButton(binding)
    }

    private fun configureSaveButton(binding: ContentAutofillSaveNewCredentialsBinding) {
        binding.saveLoginButton.setOnClickListener {
            val result = Bundle().also {
                it.putString(CredentialSavePickerDialog.KEY_URL, getOriginalUrl())
                it.putParcelable(CredentialSavePickerDialog.KEY_CREDENTIALS, getCredentialsToSave())
            }
            parentFragment?.setFragmentResult(CredentialSavePickerDialog.resultKeyUserChoseToSaveCredentials(getTabId()), result)
            animateClosed()
        }
    }

    override fun onCancel(dialog: DialogInterface) {
        Timber.v("onCancel: AutofillSavingCredentialsDialogFragment. User declined to save credentials")

        appCoroutineScope.launch {
            autofillDeclineCounter.userDeclinedToSaveCredentials(getOriginalUrl().extractDomain())

            if (autofillDeclineCounter.shouldPromptToDisableAutofill()) {
                parentFragment?.setFragmentResult(CredentialSavePickerDialog.resultKeyUserDeclinedToSaveCredentials(getTabId()), Bundle())
            }
        }
    }

    private fun configureCloseButtons(binding: ContentAutofillSaveNewCredentialsBinding) {
        binding.closeButton.setOnClickListener { animateClosed() }
        binding.cancelButton.setOnClickListener { animateClosed() }
    }

    private fun animateClosed() {
        (dialog as BottomSheetDialog).animateClosed()
    }

    private fun configureTitles(binding: ContentAutofillSaveNewCredentialsBinding, credentials: LoginCredentials) {
        val resources = viewModel.determineTextResources(credentials)

        binding.dialogTitle.text = getString(resources.title)
        binding.saveLoginButton.text = getString(resources.ctaButton)

        if (!showOnboarding()) {
            binding.onboardingSubtitle.gone()
        }
    }

    private fun configureSiteDetails(binding: ContentAutofillSaveNewCredentialsBinding) {
        val originalUrl = getOriginalUrl()
        val url = originalUrl.extractDomain() ?: originalUrl

        binding.siteName.text = url

        lifecycleScope.launch {
            faviconManager.loadToViewFromLocalOrFallback(url = url, view = binding.favicon)
        }
    }

    private fun getCredentialsToSave() = arguments?.getParcelable<LoginCredentials>(CredentialSavePickerDialog.KEY_CREDENTIALS)!!
    private fun getTabId() = arguments?.getString(CredentialSavePickerDialog.KEY_TAB_ID)!!
    private fun getOriginalUrl() = arguments?.getString(CredentialSavePickerDialog.KEY_URL)!!

    private fun showOnboarding() = viewModel.showOnboarding()

    companion object {

        fun instance(url: String, credentials: LoginCredentials, tabId: String): AutofillSavingCredentialsDialogFragment {

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
}
