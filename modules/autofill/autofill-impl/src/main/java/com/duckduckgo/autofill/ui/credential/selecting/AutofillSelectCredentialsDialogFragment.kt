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
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.global.extractDomain
import com.duckduckgo.autofill.CredentialAutofillPickerDialog
import com.duckduckgo.autofill.CredentialAutofillPickerDialog.Companion.RESULT_KEY_CREDENTIAL_PICKER
import com.duckduckgo.autofill.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.databinding.ContentAutofillSelectCredentialsTooltipBinding
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.mobile.android.ui.view.toPx
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.launch
import javax.inject.Inject

@InjectWith(FragmentScope::class)
class AutofillSelectCredentialsDialogFragment : BottomSheetDialogFragment(), CredentialAutofillPickerDialog {

    @Inject
    lateinit var faviconManager: FaviconManager

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = ContentAutofillSelectCredentialsTooltipBinding.inflate(inflater, container, false)
        configureViews(binding)
        return binding.root
    }

    private fun configureViews(binding: ContentAutofillSelectCredentialsTooltipBinding) {
        dialog?.setOnShowListener {
            val bottomSheetDialog = it as BottomSheetDialog
            val sheet: FrameLayout = bottomSheetDialog.findViewById(com.google.android.material.R.id.design_bottom_sheet)!!

            BottomSheetBehavior.from(sheet).setPeekHeight(600.toPx(), true)
        }

        configureSiteDetails(binding)

        configureRecyclerView(binding)
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
        binding.availableCredentialsRecycler.adapter =
            CredentialsPickerRecyclerAdapter(this, faviconManager, getAvailableCredentials()) { selectedCredentials ->
                val result =
                    Bundle().also {
                        it.putString("url", getOriginalUrl())
                        it.putParcelable("creds", selectedCredentials)
                    }
                parentFragment?.setFragmentResult(RESULT_KEY_CREDENTIAL_PICKER, result)
                dismiss()
            }
    }

    private fun getAvailableCredentials() = arguments?.getParcelableArrayList<LoginCredentials>("creds")!!

    private fun getOriginalUrl() = arguments?.getString("url")!!

    // needed to avoid an untyped cast when wanting to show DialogFragment, as outside this module
    // it is known by its interface CredentialAutofillPickerDialog, not as a DialogFragment.
    override fun asDialogFragment(): DialogFragment = this

    companion object {

        fun instance(
            url: String,
            credentials: List<LoginCredentials>
        ): AutofillSelectCredentialsDialogFragment {

            val cr = ArrayList<LoginCredentials>(credentials)

            val fragment = AutofillSelectCredentialsDialogFragment()
            fragment.arguments =
                Bundle().also {
                    it.putString("url", url)
                    it.putParcelableArrayList("creds", cr)
                }
            return fragment
        }
    }
}
