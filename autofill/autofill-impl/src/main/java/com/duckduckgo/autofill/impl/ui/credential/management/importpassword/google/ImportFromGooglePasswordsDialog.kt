/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.autofill.impl.ui.credential.management.importpassword.google

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.autofill.impl.R
import com.duckduckgo.autofill.impl.databinding.ContentImportFromGooglePasswordDialogBinding
import com.duckduckgo.autofill.impl.deviceauth.AutofillAuthorizationGracePeriod
import com.duckduckgo.autofill.impl.importing.CredentialImporter
import com.duckduckgo.autofill.impl.importing.gpm.webflow.ImportGooglePassword
import com.duckduckgo.autofill.impl.importing.gpm.webflow.ImportGooglePasswordResult
import com.duckduckgo.autofill.impl.ui.credential.dialog.animateClosed
import com.duckduckgo.autofill.impl.ui.credential.management.importpassword.ImportPasswordsPixelSender
import com.duckduckgo.autofill.impl.ui.credential.management.importpassword.google.ImportFromGooglePasswordsDialogViewModel.ViewMode.ImportError
import com.duckduckgo.autofill.impl.ui.credential.management.importpassword.google.ImportFromGooglePasswordsDialogViewModel.ViewMode.ImportSuccess
import com.duckduckgo.autofill.impl.ui.credential.management.importpassword.google.ImportFromGooglePasswordsDialogViewModel.ViewMode.Importing
import com.duckduckgo.autofill.impl.ui.credential.management.importpassword.google.ImportFromGooglePasswordsDialogViewModel.ViewMode.PreImport
import com.duckduckgo.autofill.impl.ui.credential.management.importpassword.google.ImportFromGooglePasswordsDialogViewModel.ViewState
import com.duckduckgo.common.utils.FragmentViewModelFactory
import com.duckduckgo.common.utils.extensions.html
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import logcat.LogPriority.VERBOSE
import logcat.logcat

@InjectWith(FragmentScope::class)
class ImportFromGooglePasswordsDialog : BottomSheetDialogFragment() {

    @Inject
    lateinit var importPasswordsPixelSender: ImportPasswordsPixelSender

    /**
     * To capture all the ways the BottomSheet can be dismissed, we might end up with onCancel being called when we don't want it
     * This flag is set to true when taking an action which dismisses the dialog, but should not be treated as a cancellation.
     */
    private var ignoreCancellationEvents = false

    override fun getTheme(): Int = R.style.AutofillBottomSheetDialogTheme

    @Inject
    lateinit var faviconManager: FaviconManager

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    @Inject
    lateinit var authorizationGracePeriod: AutofillAuthorizationGracePeriod

    private var _binding: ContentImportFromGooglePasswordDialogBinding? = null

    private val binding get() = _binding!!

    @Inject
    lateinit var viewModelFactory: FragmentViewModelFactory

    private val viewModel by bindViewModel<ImportFromGooglePasswordsDialogViewModel>()

    private val importGooglePasswordsFlowLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { activityResult ->
        if (activityResult.resultCode == Activity.RESULT_OK) {
            lifecycleScope.launch {
                activityResult.data?.let { data ->
                    processImportFlowResult(data)
                }
            }
        }
    }

    private fun ImportFromGooglePasswordsDialog.processImportFlowResult(data: Intent) {
        (IntentCompat.getParcelableExtra(data, ImportGooglePasswordResult.RESULT_KEY_DETAILS, ImportGooglePasswordResult::class.java)).let {
            when (it) {
                is ImportGooglePasswordResult.Success -> viewModel.onImportFlowFinishedSuccessfully()
                is ImportGooglePasswordResult.Error -> viewModel.onImportFlowFinishedWithError(it.reason)
                is ImportGooglePasswordResult.UserCancelled -> viewModel.onImportFlowCancelledByUser(it.stage)
                else -> {}
            }
        }
    }

    private fun switchDialogShowImportInProgressView() {
        binding.prePostViewSwitcher.displayedChild = 1
        binding.postflow.inProgressFinishedViewSwitcher.displayedChild = 0
    }

    private fun switchDialogShowImportResultsView() {
        binding.prePostViewSwitcher.displayedChild = 1
        binding.postflow.inProgressFinishedViewSwitcher.displayedChild = 1
    }

    private fun switchDialogShowPreImportView() {
        binding.prePostViewSwitcher.displayedChild = 0
    }

    private fun processSuccessResult(result: CredentialImporter.ImportResult.Finished) {
        binding.postflow.importFinished.errorNotImported.visibility = View.GONE
        binding.postflow.appIcon.setImageDrawable(
            ContextCompat.getDrawable(
                binding.root.context,
                R.drawable.ic_success_128,
            ),
        )
        binding.postflow.dialogTitle.text = getString(R.string.importPasswordsProcessingResultDialogTitleUponSuccess)

        with(binding.postflow.importFinished.resultsImported) {
            val output = getString(R.string.importPasswordsProcessingResultDialogResultPasswordsImported, result.savedCredentials)
            setPrimaryText(output.html(binding.root.context))
        }

        with(binding.postflow.importFinished.duplicatesNotImported) {
            val output = getString(R.string.importPasswordsProcessingResultDialogResultDuplicatesSkipped, result.numberSkipped)
            setPrimaryText(output.html(binding.root.context))
            visibility = if (result.numberSkipped > 0) View.VISIBLE else View.GONE
        }

        switchDialogShowImportResultsView()
    }

    private fun processErrorResult() {
        binding.postflow.importFinished.resultsImported.visibility = View.GONE
        binding.postflow.importFinished.duplicatesNotImported.visibility = View.GONE
        binding.postflow.importFinished.errorNotImported.visibility = View.VISIBLE

        binding.postflow.dialogTitle.text = getString(R.string.importPasswordsProcessingResultDialogTitleBeforeSuccess)
        binding.postflow.appIcon.setImageDrawable(
            ContextCompat.getDrawable(
                binding.root.context,
                R.drawable.ic_passwords_import_128,
            ),
        )

        switchDialogShowImportResultsView()
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
        importPasswordsPixelSender.onImportPasswordsDialogDisplayed()

        _binding = ContentImportFromGooglePasswordDialogBinding.inflate(inflater, container, false)
        configureViews(binding)
        observeViewModel()
        return binding.root
    }

    private fun observeViewModel() {
        viewModel.viewState.flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
            .onEach { viewState -> renderViewState(viewState) }
            .launchIn(lifecycleScope)
    }

    private fun renderViewState(viewState: ViewState) {
        when (viewState.viewMode) {
            is PreImport -> switchDialogShowPreImportView()
            is ImportError -> processErrorResult()
            is ImportSuccess -> processSuccessResult(viewState.viewMode.importResult)
            is Importing -> switchDialogShowImportInProgressView()
        }
    }

    override fun onDestroyView() {
        _binding = null
        authorizationGracePeriod.removeRequestForExtendedGracePeriod()
        super.onDestroyView()
    }

    private fun configureViews(binding: ContentImportFromGooglePasswordDialogBinding) {
        (dialog as BottomSheetDialog).behavior.state = BottomSheetBehavior.STATE_EXPANDED
        configureCloseButton(binding)

        with(binding.preflow.importGcmButton) {
            setOnClickListener { onImportGcmButtonClicked() }
        }

        with(binding.postflow.importFinished.primaryCtaButton) {
            setOnClickListener {
                dismiss()
            }
        }
    }

    private fun onImportGcmButtonClicked() {
        authorizationGracePeriod.requestExtendedGracePeriod()

        val intent = globalActivityStarter.startIntent(
            requireContext(),
            ImportGooglePassword.AutofillImportViaGooglePasswordManagerScreen,
        )
        importGooglePasswordsFlowLauncher.launch(intent)

        importPasswordsPixelSender.onImportPasswordsDialogImportButtonClicked()

        // we don't want the eventual dismissal of this dialog to count as a cancellation
        ignoreCancellationEvents = true
    }

    override fun onCancel(dialog: DialogInterface) {
        if (ignoreCancellationEvents) {
            logcat(VERBOSE) { "onCancel: Ignoring cancellation event" }
            return
        }

        importPasswordsPixelSender.onUserCancelledImportPasswordsDialog()

        dismiss()
    }

    private fun configureCloseButton(binding: ContentImportFromGooglePasswordDialogBinding) {
        binding.closeButton.setOnClickListener { (dialog as BottomSheetDialog).animateClosed() }
    }

    private inline fun <reified V : ViewModel> bindViewModel() = lazy { ViewModelProvider(this, viewModelFactory)[V::class.java] }

    companion object {

        fun instance(): ImportFromGooglePasswordsDialog {
            val fragment = ImportFromGooglePasswordsDialog()
            return fragment
        }
    }
}
