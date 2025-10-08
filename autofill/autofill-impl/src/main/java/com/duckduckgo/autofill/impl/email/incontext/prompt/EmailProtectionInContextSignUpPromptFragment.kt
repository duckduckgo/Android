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

package com.duckduckgo.autofill.impl.email.incontext.prompt

import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autofill.api.EmailProtectionInContextSignUpDialog
import com.duckduckgo.autofill.impl.R
import com.duckduckgo.autofill.impl.databinding.DialogEmailProtectionInContextSignUpBinding
import com.duckduckgo.autofill.impl.email.incontext.prompt.EmailProtectionInContextSignUpPromptViewModel.Command.FinishWithResult
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames
import com.duckduckgo.common.utils.FragmentViewModelFactory
import com.duckduckgo.di.scopes.FragmentScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import logcat.LogPriority.VERBOSE
import logcat.logcat
import javax.inject.Inject

@InjectWith(FragmentScope::class)
class EmailProtectionInContextSignUpPromptFragment : BottomSheetDialogFragment(), EmailProtectionInContextSignUpDialog {

    override fun getTheme(): Int = R.style.AutofillBottomSheetDialogTheme

    @Inject
    lateinit var pixel: Pixel

    @Inject
    lateinit var viewModelFactory: FragmentViewModelFactory

    private val viewModel by lazy {
        ViewModelProvider(this, viewModelFactory)[EmailProtectionInContextSignUpPromptViewModel::class.java]
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
        pixel.fire(AutofillPixelNames.EMAIL_PROTECTION_IN_CONTEXT_PROMPT_DISPLAYED)

        val binding = DialogEmailProtectionInContextSignUpBinding.inflate(inflater, container, false)
        configureViews(binding)
        observeViewModel()
        return binding.root
    }

    private fun observeViewModel() {
        viewModel.commands.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).onEach {
            when (it) {
                is FinishWithResult -> returnResult(it.result)
            }
        }.launchIn(lifecycleScope)
    }

    private fun configureViews(binding: DialogEmailProtectionInContextSignUpBinding) {
        (dialog as BottomSheetDialog).behavior.state = BottomSheetBehavior.STATE_EXPANDED
        configureDialogButtons(binding)
    }

    private fun configureDialogButtons(binding: DialogEmailProtectionInContextSignUpBinding) {
        context?.let {
            binding.protectMyEmailButton.setOnClickListener {
                viewModel.onProtectEmailButtonPressed()
            }

            binding.closeButton.setOnClickListener {
                viewModel.onCloseButtonPressed()
            }

            binding.doNotShowAgainButton.setOnClickListener {
                viewModel.onDoNotShowAgainButtonPressed()
            }
        }
    }

    private fun returnResult(resultType: EmailProtectionInContextSignUpDialog.EmailProtectionInContextSignUpResult) {
        logcat(VERBOSE) { "User action: ${resultType::class.java.simpleName}" }

        val result = Bundle().also {
            it.putParcelable(EmailProtectionInContextSignUpDialog.KEY_RESULT, resultType)
        }

        parentFragment?.setFragmentResult(EmailProtectionInContextSignUpDialog.resultKey(getTabId()), result)
        dismiss()
    }

    override fun onCancel(dialog: DialogInterface) {
        pixel.fire(AutofillPixelNames.EMAIL_PROTECTION_IN_CONTEXT_PROMPT_DISMISSED)
        returnResult(EmailProtectionInContextSignUpDialog.EmailProtectionInContextSignUpResult.Cancel)
    }

    private fun getTabId() = arguments?.getString(KEY_TAB_ID)!!

    companion object {
        fun instance(
            tabId: String,
        ): EmailProtectionInContextSignUpPromptFragment {
            val fragment = EmailProtectionInContextSignUpPromptFragment()
            fragment.arguments = Bundle().also {
                it.putString(KEY_TAB_ID, tabId)
            }
            return fragment
        }

        private const val KEY_TAB_ID = "tabId"
    }
}
