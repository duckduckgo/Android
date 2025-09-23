/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.autofill.impl.ui.credential.management.importbookmark.google.preimport

import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.BundleCompat
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.autofill.api.AutofillImportBookmarksLaunchSource
import com.duckduckgo.autofill.api.AutofillImportBookmarksLaunchSource.Unknown
import com.duckduckgo.autofill.impl.R
import com.duckduckgo.autofill.impl.databinding.ContentImportBookmarksFromGooglePreimportDialogBinding
import com.duckduckgo.autofill.impl.deviceauth.AutofillAuthorizationGracePeriod
import com.duckduckgo.autofill.impl.ui.credential.dialog.animateClosed
import com.duckduckgo.autofill.impl.ui.credential.management.importbookmark.google.preimport.ImportFromGoogleBookmarksPreImportDialogViewModel.ViewMode.FlowTerminated
import com.duckduckgo.autofill.impl.ui.credential.management.importbookmark.google.preimport.ImportFromGoogleBookmarksPreImportDialogViewModel.ViewMode.ImportError
import com.duckduckgo.autofill.impl.ui.credential.management.importbookmark.google.preimport.ImportFromGoogleBookmarksPreImportDialogViewModel.ViewMode.ImportSuccess
import com.duckduckgo.common.utils.FragmentViewModelFactory
import com.duckduckgo.di.scopes.FragmentScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import logcat.LogPriority.ERROR
import logcat.LogPriority.VERBOSE
import logcat.asLog
import logcat.logcat
import javax.inject.Inject

@InjectWith(FragmentScope::class)
class ImportFromGoogleBookmarksPreImportDialog : BottomSheetDialogFragment() {
    /**
     * To capture all the ways the BottomSheet can be dismissed, we might end up with onCancel being called when we don't want it
     * This flag is set to true when taking an action which dismisses the dialog, but should not be treated as a cancellation.
     */
    private var ignoreCancellationEvents = false

    override fun getTheme(): Int = R.style.AutofillBottomSheetDialogTheme

    @Inject
    lateinit var faviconManager: FaviconManager

    @Inject
    lateinit var authorizationGracePeriod: AutofillAuthorizationGracePeriod

    private var _binding: ContentImportBookmarksFromGooglePreimportDialogBinding? = null

    val binding get() = _binding!!

    @Inject
    lateinit var viewModelFactory: FragmentViewModelFactory

    private val viewModel by bindViewModel<ImportFromGoogleBookmarksPreImportDialogViewModel>()

    private var successImport = false

    private var importClickedCallback: (() -> Unit)? = null

    fun setImportClickedCallback(callback: () -> Unit) {
        importClickedCallback = callback
    }

    private fun getTabId(): String? = arguments?.getString(ImportBookmarksDialog.Companion.KEY_TAB_ID)

    private fun getLaunchSource() =
        BundleCompat.getParcelable(arguments ?: Bundle(), KEY_LAUNCH_SOURCE, AutofillImportBookmarksLaunchSource::class.java) ?: Unknown

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState != null) {
            // If being created after a configuration change, dismiss the dialog as the WebView will be re-created too
            dismiss()
            return
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = ContentImportBookmarksFromGooglePreimportDialogBinding.inflate(inflater, container, false)
        configureViews(binding)
        observeViewModel()
        logcat { "Creating ImportFromGooglePasswordsDialog with launch source: ${getLaunchSource()}" }
        return binding.root
    }

    private fun observeViewModel() {
        viewModel.viewState
            .flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
            .onEach { viewState -> renderViewState(viewState) }
            .launchIn(lifecycleScope)
    }

    private fun renderViewState(viewState: ImportFromGoogleBookmarksPreImportDialogViewModel.ViewState) {
        when (viewState.viewMode) {
            is ImportFromGoogleBookmarksPreImportDialogViewModel.ViewMode.PreImport -> {
                // Initial state, nothing to do
            }
            is ImportError -> {
                // TODO: Handle error state
            }
            is ImportSuccess -> {
                // TODO: Handle success state
            }
            is FlowTerminated -> dismiss()
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        logcat { "Dismissing ${javaClass.simpleName}, successful import? $successImport" }
        setResult(successImport)
    }

    private fun setResult(successImport: Boolean) {
        logcat { "Autofill-import: Setting result for ${javaClass.simpleName}, successful import? $successImport. tabId=${getTabId()}" }

        getTabId()?.let { tabId ->
            val result =
                Bundle().also {
                    it.putBoolean(ImportBookmarksDialog.Companion.KEY_IMPORT_SUCCESS, successImport)
                    it.putString(ImportBookmarksDialog.Companion.KEY_URL, getOriginalUrl())
                }

            val resultKey = ImportBookmarksDialog.resultKey(tabId)
            findCorrectFragmentManager()?.setFragmentResult(resultKey, result)
        }
    }

    private fun findCorrectFragmentManager(): FragmentManager? {
        val result =
            runCatching {
                parentFragment?.parentFragmentManager ?: parentFragmentManager
            }.onFailure { logcat(ERROR) { "Autofill-import: Failed to find a valid fragment manager ${it.asLog()}" } }

        return result.getOrNull()
    }

    override fun onDestroyView() {
        _binding = null
        authorizationGracePeriod.removeRequestForExtendedGracePeriod()
        super.onDestroyView()
    }

    private fun configureViews(binding: ContentImportBookmarksFromGooglePreimportDialogBinding) {
        (dialog as BottomSheetDialog).behavior.state = BottomSheetBehavior.STATE_EXPANDED
        configureCloseButton(binding)

        with(binding.importButton) {
            setOnClickListener { onImportButtonClicked() }
        }
    }

    private fun onImportButtonClicked() {
        importClickedCallback?.invoke()
    }

    override fun onCancel(dialog: DialogInterface) {
        logcat {
            "Cancelling ImportFromGooglePasswordsDialog, " +
                "successful import? $successImport. " +
                "ignore cancellation events: $ignoreCancellationEvents"
        }

        if (ignoreCancellationEvents) {
            logcat(VERBOSE) { "onCancel: Ignoring cancellation event" }
            return
        }
    }

    private fun configureCloseButton(binding: ContentImportBookmarksFromGooglePreimportDialogBinding) {
        binding.closeButton.setOnClickListener { (dialog as BottomSheetDialog).animateClosed() }
    }

    private fun getOriginalUrl() = arguments?.getString(ImportBookmarksDialog.Companion.KEY_URL)

    private inline fun <reified V : ViewModel> bindViewModel() = lazy { ViewModelProvider(this, viewModelFactory)[V::class.java] }

    companion object {
        private const val KEY_LAUNCH_SOURCE = "launchSource"

        fun instance(
            importSource: AutofillImportBookmarksLaunchSource,
            tabId: String? = null,
            originalUrl: String? = null,
        ): ImportFromGoogleBookmarksPreImportDialog {
            val fragment = ImportFromGoogleBookmarksPreImportDialog()
            fragment.arguments =
                Bundle().apply {
                    putParcelable(KEY_LAUNCH_SOURCE, importSource)
                    putString(ImportBookmarksDialog.Companion.KEY_TAB_ID, tabId)
                    putString(ImportBookmarksDialog.Companion.KEY_URL, originalUrl)
                }
            return fragment
        }
    }

    interface ImportBookmarksDialog {
        companion object {
            fun resultKey(tabId: String) = "${prefix(tabId, TAG)}/Result"

            const val TAG = "ImportBookmarksDialog"
            const val KEY_URL = "url"
            const val KEY_IMPORT_SUCCESS = "importSuccess"
            const val KEY_TAB_ID = "tabId"

            private fun prefix(
                tabId: String,
                tag: String,
            ): String = "$tabId/$tag"
        }
    }
}
