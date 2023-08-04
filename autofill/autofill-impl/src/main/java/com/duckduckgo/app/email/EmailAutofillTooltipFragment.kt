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
import com.duckduckgo.autofill.impl.ui.credential.dialog.animateClosed
import com.duckduckgo.di.scopes.FragmentScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.android.support.AndroidSupportInjection
import timber.log.Timber

@InjectWith(FragmentScope::class)
class EmailAutofillTooltipFragment : BottomSheetDialogFragment(), EmailProtectionChooserDialog {

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    /**
     * To capture all the ways the BottomSheet can be dismissed, we might end up with onCancel being called when we don't want it
     * This flag is set to true when taking an action which dismisses the dialog, but should not be treated as a cancellation.
     */
    private var ignoreCancellationEvents = false

    override fun getTheme(): Int = R.style.AutofillBottomSheetDialogTheme

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
                dismiss()
            }

            binding.primaryCta.setOnClickListener {
                returnResult(UseEmailResultType.UsePersonalEmailAddress)
                dismiss()
            }
        }
    }

    private fun returnResult(resultType: UseEmailResultType) {
        val result = Bundle().also {
            it.putString(EmailProtectionChooserDialog.KEY_URL, getOriginalUrl())
            it.putParcelable(EmailProtectionChooserDialog.KEY_RESULT, resultType)
        }

        parentFragment?.setFragmentResult(EmailProtectionChooserDialog.resultKey(getTabId()), result)
    }

    private fun configureCloseButton(binding: ContentAutofillTooltipBinding) {
        binding.closeButton.setOnClickListener { animateClosed() }
        returnResult(UseEmailResultType.DoNotUseEmailProtection)
    }

    private fun animateClosed() {
        (dialog as BottomSheetDialog).animateClosed()
    }

    override fun onCancel(dialog: DialogInterface) {
        if (ignoreCancellationEvents) {
            Timber.v("onCancel: Ignoring cancellation event")
            return
        }

        Timber.v("onCancel: EmailAutofillTooltipFragment. User declined to use Email Protection")

        returnResult(UseEmailResultType.DoNotUseEmailProtection)
    }

    private fun getPersonalAddress() = arguments?.getString(EmailProtectionChooserDialog.KEY_ADDRESS)!!
    private fun getOriginalUrl() = arguments?.getString(EmailProtectionChooserDialog.KEY_URL)!!
    private fun getTabId() = arguments?.getString(EmailProtectionChooserDialog.KEY_TAB_ID)!!

    companion object {
        fun instance(personalDuckAddress: String, url: String, tabId: String): EmailAutofillTooltipFragment {
            val fragment = EmailAutofillTooltipFragment()
            fragment.arguments = Bundle().also {
                it.putString(EmailProtectionChooserDialog.KEY_ADDRESS, personalDuckAddress)
                it.putString(EmailProtectionChooserDialog.KEY_URL, url)
                it.putString(EmailProtectionChooserDialog.KEY_TAB_ID, tabId)
            }
            return fragment
        }
    }
}
