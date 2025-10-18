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
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.autofill.api.AutofillImportBookmarksLaunchSource
import com.duckduckgo.autofill.api.AutofillImportBookmarksLaunchSource.Unknown
import com.duckduckgo.autofill.impl.R
import com.duckduckgo.autofill.impl.databinding.ContentImportBookmarksFromGooglePreimportDialogBinding
import com.duckduckgo.autofill.impl.ui.credential.dialog.animateClosed
import com.duckduckgo.autofill.impl.ui.credential.management.importbookmark.google.preimport.ImportFromGoogleBookmarksPreImportDialog.ImportBookmarksDialog.Companion.KEY_TAB_ID
import com.duckduckgo.common.utils.FragmentViewModelFactory
import com.duckduckgo.di.scopes.FragmentScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.android.support.AndroidSupportInjection
import logcat.LogPriority.VERBOSE
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

    private var _binding: ContentImportBookmarksFromGooglePreimportDialogBinding? = null

    val binding get() = _binding!!

    @Inject
    lateinit var viewModelFactory: FragmentViewModelFactory

    private var importClickedCallback: (() -> Unit)? = null

    fun setImportClickedCallback(callback: () -> Unit) {
        importClickedCallback = callback
    }

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
        logcat { "Creating ${javaClass.simpleName} with launch source: ${getLaunchSource()}" }
        return binding.root
    }

    override fun onDestroyView() {
        _binding = null
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
        if (ignoreCancellationEvents) {
            logcat(VERBOSE) { "onCancel: Ignoring cancellation event" }
            return
        }
    }

    private fun configureCloseButton(binding: ContentImportBookmarksFromGooglePreimportDialogBinding) {
        binding.closeButton.setOnClickListener { (dialog as BottomSheetDialog).animateClosed() }
    }

    companion object {
        private const val KEY_LAUNCH_SOURCE = "launchSource"

        fun instance(
            importSource: AutofillImportBookmarksLaunchSource,
            tabId: String? = null,
        ): ImportFromGoogleBookmarksPreImportDialog {
            val fragment = ImportFromGoogleBookmarksPreImportDialog()
            fragment.arguments =
                Bundle().apply {
                    putParcelable(KEY_LAUNCH_SOURCE, importSource)
                    putString(KEY_TAB_ID, tabId)
                }
            return fragment
        }
    }

    interface ImportBookmarksDialog {
        companion object {
            const val KEY_TAB_ID = "tabId"
        }
    }
}
