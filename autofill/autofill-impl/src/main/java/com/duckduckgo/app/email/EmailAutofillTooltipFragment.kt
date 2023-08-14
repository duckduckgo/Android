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

package com.duckduckgo.app.email

import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.setFragmentResult
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.global.extensions.html
import com.duckduckgo.autofill.api.EmailProtectionChooserDialog
import com.duckduckgo.autofill.api.EmailProtectionChooserDialog.UseEmailResultType
import com.duckduckgo.autofill.impl.R
import com.duckduckgo.autofill.impl.databinding.ContentAutofillTooltipBinding
import com.duckduckgo.di.scopes.FragmentScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.android.support.AndroidSupportInjection
import timber.log.Timber

@InjectWith(FragmentScope::class)
class EmailAutofillTooltipFragment : BottomSheetDialogFragment(), EmailProtectionChooserDialog {

    override fun getTheme(): Int = R.style.AutofillBottomSheetDialogTheme

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
        val binding = ContentAutofillTooltipBinding.inflate(inflater, container, false)
        configureViews(binding)
        return binding.root
    }

    private fun configureViews(binding: ContentAutofillTooltipBinding) {
        (dialog as BottomSheetDialog).behavior.state = BottomSheetBehavior.STATE_EXPANDED
        configureCloseButton(binding)
        configureEmailButtons(binding)
    }

    private fun configureEmailButtons(binding: ContentAutofillTooltipBinding) {
        context?.let {
            binding.primaryCta.setPrimaryText(getPersonalAddress().html(it).toString())

            binding.secondaryCta.setOnClickListener {
                returnResult(UseEmailResultType.UsePrivateAliasAddress)
            }

            binding.primaryCta.setOnClickListener {
                returnResult(UseEmailResultType.UsePersonalEmailAddress)
            }
        }
    }

    private fun returnResult(resultType: UseEmailResultType) {
        Timber.v("User action: %s", resultType::class.java.simpleName)

        val result = Bundle().also {
            it.putString(EmailProtectionChooserDialog.KEY_URL, getOriginalUrl())
            it.putParcelable(EmailProtectionChooserDialog.KEY_RESULT, resultType)
        }

        parentFragment?.setFragmentResult(EmailProtectionChooserDialog.resultKey(getTabId()), result)
        dismiss()
    }

    private fun configureCloseButton(binding: ContentAutofillTooltipBinding) {
        binding.closeButton.setOnClickListener {
            returnResult(UseEmailResultType.DoNotUseEmailProtection)
        }
    }

    override fun onCancel(dialog: DialogInterface) {
        Timber.v("onCancel: EmailAutofillTooltipFragment. User declined to use Email Protection")
        returnResult(UseEmailResultType.DoNotUseEmailProtection)
    }

    private fun getPersonalAddress() = arguments?.getString(KEY_ADDRESS)!!
    private fun getOriginalUrl() = arguments?.getString(EmailProtectionChooserDialog.KEY_URL)!!
    private fun getTabId() = arguments?.getString(KEY_TAB_ID)!!

    companion object {
        fun instance(
            personalDuckAddress: String,
            url: String,
            tabId: String,
        ): EmailAutofillTooltipFragment {
            val fragment = EmailAutofillTooltipFragment()
            fragment.arguments = Bundle().also {
                it.putString(KEY_ADDRESS, personalDuckAddress)
                it.putString(EmailProtectionChooserDialog.KEY_URL, url)
                it.putString(KEY_TAB_ID, tabId)
            }
            return fragment
        }

        private const val KEY_TAB_ID = "tabId"
        private const val KEY_ADDRESS = "address"
    }
}
