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

package com.duckduckgo.autofill.ui.credential.selecting

import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.global.extractDomain
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autofill.CredentialAutofillPickerDialog
import com.duckduckgo.autofill.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.AutofillPixelNames
import com.duckduckgo.autofill.impl.R
import com.duckduckgo.autofill.impl.databinding.ContentAutofillSelectCredentialsTooltipBinding
import com.duckduckgo.autofill.ui.credential.dialog.animateClosed
import com.duckduckgo.di.scopes.FragmentScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.launch
import javax.inject.Inject

@InjectWith(FragmentScope::class)
class AutofillSelectCredentialsDialogFragment : BottomSheetDialogFragment(), CredentialAutofillPickerDialog {
    @Inject
    lateinit var pixel: Pixel

    override fun getTheme(): Int = R.style.AutofillBottomSheetDialogTheme

    @Inject
    lateinit var faviconManager: FaviconManager

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = ContentAutofillSelectCredentialsTooltipBinding.inflate(inflater, container, false)
        configureViews(binding)
        return binding.root
    }

    private fun configureViews(binding: ContentAutofillSelectCredentialsTooltipBinding) {
        configureSiteDetails(binding)
        configureRecyclerView(binding)
        configureCloseButton(binding)
    }

    private fun configureCloseButton(binding: ContentAutofillSelectCredentialsTooltipBinding) {
        binding.closeButton.setOnClickListener {
            (dialog as BottomSheetDialog).animateClosed()
        }
    }

    private fun configureSiteDetails(binding: ContentAutofillSelectCredentialsTooltipBinding) {
        val originalUrl = getOriginalUrl()
        val url = originalUrl.extractDomain() ?: originalUrl

        binding.siteName.text = url

        lifecycleScope.launch {
            faviconManager.loadToViewFromLocalOrFallback(url = url, view = binding.favicon)
        }
    }

    private fun configureRecyclerView(binding: ContentAutofillSelectCredentialsTooltipBinding) {
        binding.availableCredentialsRecycler.adapter = configureAdapter()
    }

    private fun configureAdapter(): CredentialsPickerRecyclerAdapter {
        return CredentialsPickerRecyclerAdapter(
            lifecycleOwner = this,
            faviconManager = faviconManager,
            credentialTextExtractor = CredentialTextExtractor(requireContext()),
            credentials = getAvailableCredentials()
        ) { selectedCredentials ->
            val result = Bundle().also {
                it.putBoolean(CredentialAutofillPickerDialog.KEY_CANCELLED, false)
                it.putString(CredentialAutofillPickerDialog.KEY_URL, getOriginalUrl())
                it.putParcelable(CredentialAutofillPickerDialog.KEY_CREDENTIALS, selectedCredentials)
            }
            parentFragment?.setFragmentResult(CredentialAutofillPickerDialog.RESULT_KEY_CREDENTIAL_PICKER, result)
            dismiss()
        }
    }

    override fun onCancel(dialog: DialogInterface) {
        val result = Bundle().also {
            it.putBoolean(CredentialAutofillPickerDialog.KEY_CANCELLED, true)
            it.putString(CredentialAutofillPickerDialog.KEY_URL, getOriginalUrl())
        }
        pixel.fire(AutofillPixelNames.AUTOFILL_LOGINS_AUTOPROMPT_DISMISSED)
        parentFragment?.setFragmentResult(CredentialAutofillPickerDialog.RESULT_KEY_CREDENTIAL_PICKER, result)
    }

    private fun getAvailableCredentials() = arguments?.getParcelableArrayList<LoginCredentials>(CredentialAutofillPickerDialog.KEY_CREDENTIALS)!!

    private fun getOriginalUrl() = arguments?.getString(CredentialAutofillPickerDialog.KEY_URL)!!

    companion object {

        fun instance(url: String, credentials: List<LoginCredentials>, isAutoprompt: Boolean): AutofillSelectCredentialsDialogFragment {

            val cr = ArrayList<LoginCredentials>(credentials)

            val fragment = AutofillSelectCredentialsDialogFragment()
            fragment.arguments =
                Bundle().also {
                    it.putString(CredentialAutofillPickerDialog.KEY_URL, url)
                    it.putParcelableArrayList(CredentialAutofillPickerDialog.KEY_CREDENTIALS, cr)
                    it.putBoolean(CredentialAutofillPickerDialog.IS_AUTOPROMPT, isAutoprompt)
                }
            return fragment
        }
    }
}
