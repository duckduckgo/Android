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
import android.graphics.Rect
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.view.LayoutInflater
import android.view.TouchDelegate
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.setFragmentResult
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.autofill.api.UseGeneratedPasswordDialog
import com.duckduckgo.autofill.impl.R
import com.duckduckgo.autofill.impl.databinding.ContentAutofillGeneratePasswordDialogBinding
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_PASSWORD_GENERATION_ACCEPTED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_PASSWORD_GENERATION_PROMPT_DISMISSED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_PASSWORD_GENERATION_PROMPT_SHOWN
import com.duckduckgo.autofill.impl.ui.credential.dialog.animateClosed
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillClipboardInteractor
import com.duckduckgo.autofill.impl.ui.credential.passwordgeneration.AutofillUseGeneratedPasswordDialogFragment.DialogEvent.Dismissed
import com.duckduckgo.autofill.impl.ui.credential.passwordgeneration.AutofillUseGeneratedPasswordDialogFragment.DialogEvent.GeneratedPasswordAccepted
import com.duckduckgo.autofill.impl.ui.credential.passwordgeneration.AutofillUseGeneratedPasswordDialogFragment.DialogEvent.Shown
import com.duckduckgo.common.ui.view.prependIconToText
import com.duckduckgo.common.ui.view.toPx
import com.duckduckgo.di.scopes.FragmentScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject
import logcat.LogPriority.VERBOSE
import logcat.logcat

@InjectWith(FragmentScope::class)
class AutofillUseGeneratedPasswordDialogFragment : BottomSheetDialogFragment(), UseGeneratedPasswordDialog {

    @Inject
    lateinit var pixel: Pixel

    @Inject
    lateinit var clipboardInteractor: AutofillClipboardInteractor

    @Inject
    lateinit var appBuildConfig: AppBuildConfig

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
        configureSubtitleText(binding)
    }

    private fun configureSubtitleText(binding: ContentAutofillGeneratePasswordDialogBinding) {
        binding.dialogSubtitle.text = binding.root.context.prependIconToText(R.string.saveLoginDialogSubtitle, R.drawable.ic_lock_solid_12)
    }

    private fun configurePasswordField(binding: ContentAutofillGeneratePasswordDialogBinding) {
        binding.generatedPassword.text = getGeneratedPassword()

        binding.copyPasswordButton.setOnClickListener {
            clipboardInteractor.copyToClipboard(binding.generatedPassword.text.toString(), isSensitive = true)
            if (shouldShowCopiedTextToast()) {
                Toast.makeText(context, R.string.autofillManagementPasswordCopied, Toast.LENGTH_SHORT).show()
            }
        }

        with(binding.copyPasswordButton) {
            post {
                // actual size is 24dp, but we want to increase touch area to a more comfortable 48dp; adding 12dp to each side
                val touchableArea = Rect()
                getHitRect(touchableArea)
                val extraSpace = 12.toPx()
                touchableArea.top -= extraSpace
                touchableArea.bottom += extraSpace
                touchableArea.left -= extraSpace
                touchableArea.right += extraSpace
                (parent as View).touchDelegate = TouchDelegate(touchableArea, this)
            }
        }
    }

    private fun shouldShowCopiedTextToast(): Boolean {
        // Samsung on Android 12 shows its own toast when copying text, so we don't want to show our own
        if (appBuildConfig.manufacturer == "samsung" && (appBuildConfig.sdkInt == VERSION_CODES.S || appBuildConfig.sdkInt == VERSION_CODES.S_V2)) {
            return false
        }

        // From Android 13, the system shows its own toast when copying text, so we don't want to show our own
        return appBuildConfig.sdkInt <= VERSION_CODES.S_V2
    }

    private fun configureGeneratePasswordButton(
        binding: ContentAutofillGeneratePasswordDialogBinding,
        originalUrl: String,
    ) {
        binding.useSecurePasswordButton.setOnClickListener {
            pixelNameDialogEvent(GeneratedPasswordAccepted)?.let { pixel.fire(it) }

            val result = Bundle().also {
                it.putString(UseGeneratedPasswordDialog.KEY_URL, originalUrl)
                it.putBoolean(UseGeneratedPasswordDialog.KEY_ACCEPTED, true)
                it.putString(UseGeneratedPasswordDialog.KEY_USERNAME, getUsername())
                it.putString(UseGeneratedPasswordDialog.KEY_PASSWORD, getGeneratedPassword())
            }
            parentFragment?.setFragmentResult(UseGeneratedPasswordDialog.resultKey(getTabId()), result)

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
            logcat(VERBOSE) { "onCancel: Ignoring cancellation event" }
            return
        }

        logcat(VERBOSE) { "onCancel: GeneratePasswordDialogCancel. User declined to use generated password" }

        pixelNameDialogEvent(Dismissed)?.let { pixel.fire(it) }

        val result = Bundle().also {
            it.putBoolean(UseGeneratedPasswordDialog.KEY_ACCEPTED, false)
            it.putString(UseGeneratedPasswordDialog.KEY_URL, getOriginalUrl())
        }

        parentFragment?.setFragmentResult(UseGeneratedPasswordDialog.resultKey(getTabId()), result)
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

    private fun getOriginalUrl() = arguments?.getString(UseGeneratedPasswordDialog.KEY_URL)!!
    private fun getUsername() = arguments?.getString(UseGeneratedPasswordDialog.KEY_USERNAME)
    private fun getGeneratedPassword() = arguments?.getString(UseGeneratedPasswordDialog.KEY_PASSWORD)!!
    private fun getTabId() = arguments?.getString(UseGeneratedPasswordDialog.KEY_TAB_ID)!!

    companion object {

        fun instance(
            url: String,
            username: String?,
            generatedPassword: String,
            tabId: String,
        ): AutofillUseGeneratedPasswordDialogFragment {
            val fragment = AutofillUseGeneratedPasswordDialogFragment()
            fragment.arguments =
                Bundle().also {
                    it.putString(UseGeneratedPasswordDialog.KEY_URL, url)
                    it.putString(UseGeneratedPasswordDialog.KEY_USERNAME, username)
                    it.putString(UseGeneratedPasswordDialog.KEY_PASSWORD, generatedPassword)
                    it.putString(UseGeneratedPasswordDialog.KEY_TAB_ID, tabId)
                }
            return fragment
        }
    }
}
