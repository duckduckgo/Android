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

package com.duckduckgo.autofill.ui.credential.updating

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.global.FragmentViewModelFactory
import com.duckduckgo.app.global.extractDomain
import com.duckduckgo.autofill.CredentialUpdateExistingCredentialsDialog
import com.duckduckgo.autofill.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.R
import com.duckduckgo.autofill.impl.databinding.ContentAutofillUpdateExistingCredentialsBinding
import com.duckduckgo.autofill.ui.credential.dialog.animateClosed
import com.duckduckgo.di.scopes.FragmentScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.launch
import javax.inject.Inject

@InjectWith(FragmentScope::class)
class AutofillUpdatingExistingCredentialsDialogFragment : BottomSheetDialogFragment(), CredentialUpdateExistingCredentialsDialog {

    override fun getTheme(): Int = R.style.AutofillBottomSheetDialogTheme

    @Inject
    lateinit var faviconManager: FaviconManager

    @Inject
    lateinit var viewModelFactory: FragmentViewModelFactory

    private val viewModel by lazy {
        ViewModelProvider(this, viewModelFactory)[AutofillUpdatingExistingCredentialViewModel::class.java]
    }

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = ContentAutofillUpdateExistingCredentialsBinding.inflate(inflater, container, false)
        configureViews(binding)
        return binding.root
    }

    private fun configureViews(binding: ContentAutofillUpdateExistingCredentialsBinding) {
        val credentials = getCredentialsToSave()
        val originalUrl = getOriginalUrl()

        configureSiteDetails(binding, originalUrl)
        configureCloseButtons(binding)
        configurePasswordOutline(binding, credentials)

        binding.updatePasswordButton.setOnClickListener {
            val result = Bundle().also {
                it.putString(CredentialUpdateExistingCredentialsDialog.KEY_URL, originalUrl)
                it.putParcelable(CredentialUpdateExistingCredentialsDialog.KEY_CREDENTIALS, credentials)
            }
            parentFragment?.setFragmentResult(CredentialUpdateExistingCredentialsDialog.RESULT_KEY_CREDENTIAL_RESULT_UPDATE, result)
            (dialog as BottomSheetDialog).animateClosed()
        }
    }

    private fun configurePasswordOutline(binding: ContentAutofillUpdateExistingCredentialsBinding, credentials: LoginCredentials) {
        val maskedPassword = viewModel.convertPasswordToMaskedView(credentials)
        binding.passwordOutline.text = maskedPassword
    }

    private fun configureCloseButtons(binding: ContentAutofillUpdateExistingCredentialsBinding) {
        binding.closeButton.setOnClickListener {
            (dialog as BottomSheetDialog).animateClosed()
        }
        binding.cancelButton.setOnClickListener {
            (dialog as BottomSheetDialog).animateClosed()
        }
    }

    private fun configureSiteDetails(binding: ContentAutofillUpdateExistingCredentialsBinding, originalUrl: String) {
        val url = originalUrl.extractDomain() ?: originalUrl

        binding.siteName.text = url

        lifecycleScope.launch {
            faviconManager.loadToViewFromLocalOrFallback(url = url, view = binding.favicon)
        }
    }

    private fun getCredentialsToSave() = arguments?.getParcelable<LoginCredentials>(CredentialUpdateExistingCredentialsDialog.KEY_CREDENTIALS)!!

    private fun getOriginalUrl() = arguments?.getString(CredentialUpdateExistingCredentialsDialog.KEY_URL)!!

    override fun asDialogFragment(): DialogFragment = this

    companion object {

        fun instance(url: String, credentials: LoginCredentials): AutofillUpdatingExistingCredentialsDialogFragment {

            val fragment = AutofillUpdatingExistingCredentialsDialogFragment()
            fragment.arguments =
                Bundle().also {
                    it.putString(CredentialUpdateExistingCredentialsDialog.KEY_URL, url)
                    it.putParcelable(CredentialUpdateExistingCredentialsDialog.KEY_CREDENTIALS, credentials)
                }
            return fragment
        }
    }
}
