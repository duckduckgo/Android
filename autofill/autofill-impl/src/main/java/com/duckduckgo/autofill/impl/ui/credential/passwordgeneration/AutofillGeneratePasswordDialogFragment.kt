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

package com.duckduckgo.autofill.impl.ui.credential.passwordgeneration

import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.setFragmentResult
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autofill.api.GenerateSecurePasswordDialog
import com.duckduckgo.autofill.impl.R
import com.duckduckgo.autofill.impl.databinding.ContentAutofillGeneratePasswordDialogBinding
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_PASSWORD_GENERATION_ACCEPTED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_PASSWORD_GENERATION_PROMPT_DISMISSED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_PASSWORD_GENERATION_PROMPT_SHOWN
import com.duckduckgo.autofill.impl.ui.credential.dialog.animateClosed
import com.duckduckgo.autofill.impl.ui.credential.passwordgeneration.AutofillGeneratePasswordDialogFragment.DialogEvent.Dismissed
import com.duckduckgo.autofill.impl.ui.credential.passwordgeneration.AutofillGeneratePasswordDialogFragment.DialogEvent.GeneratedPasswordAccepted
import com.duckduckgo.autofill.impl.ui.credential.passwordgeneration.AutofillGeneratePasswordDialogFragment.DialogEvent.Shown
import com.duckduckgo.di.scopes.FragmentScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject
import timber.log.Timber

@InjectWith(FragmentScope::class)
class AutofillGeneratePasswordDialogFragment : BottomSheetDialogFragment(), GenerateSecurePasswordDialog {

    @Inject
    lateinit var pixel: Pixel

    /**
     * To capture all the ways the BottomSheet can be dismissed, we might end up with onCancel being called when we don't want it
     * This flag is set to true when taking an action which dismisses the dialog, but should not be treated as a cancellation.
     */
    private var ignoreCancellationEvents = false

    override fun getTheme(): Int = R.style.AutofillBottomSheetDialogTheme

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        pixelNameDialogEvent(Shown)?.let { pixel.fire(it) }

        val binding = ContentAutofillGeneratePasswordDialogBinding.inflate(inflater, container, false)
        configureViews(binding)
        return binding.root
    }

    private fun configureViews(binding: ContentAutofillGeneratePasswordDialogBinding) {
        (dialog as BottomSheetDialog).behavior.state = BottomSheetBehavior.STATE_EXPANDED
        val originalUrl = getOriginalUrl()
        configureCloseButton(binding)
        configureGeneratePasswordButton(binding, originalUrl)
        configurePasswordField(binding)
    }

    private fun configurePasswordField(binding: ContentAutofillGeneratePasswordDialogBinding) {
        binding.generatedPassword.text = getGeneratedPassword()
    }

    private fun configureGeneratePasswordButton(
        binding: ContentAutofillGeneratePasswordDialogBinding,
        originalUrl: String,
    ) {
        binding.useSecurePasswordButton.setOnClickListener {
            pixelNameDialogEvent(GeneratedPasswordAccepted)?.let { pixel.fire(it) }

            val result = Bundle().also {
                it.putString(GenerateSecurePasswordDialog.KEY_URL, originalUrl)
                it.putBoolean(GenerateSecurePasswordDialog.KEY_ACCEPTED, true)
            }
            parentFragment?.setFragmentResult(GenerateSecurePasswordDialog.resultKey(getTabId()), result)

            ignoreCancellationEvents = true
            animateClosed()
        }
    }

    private fun configureCloseButton(binding: ContentAutofillGeneratePasswordDialogBinding) {
        binding.closeButton.setOnClickListener { (dialog as BottomSheetDialog).animateClosed() }
        binding.cancelButton.setOnClickListener { (dialog as BottomSheetDialog).animateClosed() }
    }

    override fun onCancel(dialog: DialogInterface) {
        if (ignoreCancellationEvents) {
            Timber.v("onCancel: Ignoring cancellation event")
            return
        }

        Timber.v("onCancel: GeneratePasswordDialogCancel. User declined to use generated password")

        pixelNameDialogEvent(Dismissed)?.let { pixel.fire(it) }

        val result = Bundle().also {
            it.putBoolean(GenerateSecurePasswordDialog.KEY_ACCEPTED, false)
            it.putString(GenerateSecurePasswordDialog.KEY_URL, getOriginalUrl())
        }

        parentFragment?.setFragmentResult(GenerateSecurePasswordDialog.resultKey(getTabId()), result)
    }

    private fun animateClosed() {
        (dialog as BottomSheetDialog).animateClosed()
    }

    private fun pixelNameDialogEvent(dialogEvent: DialogEvent): AutofillPixelNames? {
        return when (dialogEvent) {
            is Shown -> AUTOFILL_PASSWORD_GENERATION_PROMPT_SHOWN
            is GeneratedPasswordAccepted -> AUTOFILL_PASSWORD_GENERATION_ACCEPTED
            is Dismissed -> AUTOFILL_PASSWORD_GENERATION_PROMPT_DISMISSED
            else -> null
        }
    }

    private interface DialogEvent {
        object Shown : DialogEvent
        object Dismissed : DialogEvent
        object GeneratedPasswordAccepted : DialogEvent
    }

    private fun getOriginalUrl() = arguments?.getString(GenerateSecurePasswordDialog.KEY_URL)!!
    private fun getGeneratedPassword() = arguments?.getString(GenerateSecurePasswordDialog.KEY_PASSWORD)!!
    private fun getTabId() = arguments?.getString(GenerateSecurePasswordDialog.KEY_TAB_ID)!!

    companion object {

        fun instance(
            url: String,
            generatedPassword: String,
            tabId: String,
        ): AutofillGeneratePasswordDialogFragment {
            val fragment = AutofillGeneratePasswordDialogFragment()
            fragment.arguments =
                Bundle().also {
                    it.putString(GenerateSecurePasswordDialog.KEY_URL, url)
                    it.putString(GenerateSecurePasswordDialog.KEY_PASSWORD, generatedPassword)
                    it.putString(GenerateSecurePasswordDialog.KEY_TAB_ID, tabId)
                }
            return fragment
        }
    }
}
