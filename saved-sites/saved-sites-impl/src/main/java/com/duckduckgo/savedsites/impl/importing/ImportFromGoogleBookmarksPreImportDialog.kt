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

package com.duckduckgo.savedsites.impl.importing

import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.setFragmentResult
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.utils.FragmentViewModelFactory
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.saved.sites.impl.R
import com.duckduckgo.saved.sites.impl.databinding.ContentImportBookmarksFromGooglePreimportDialogBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.android.support.AndroidSupportInjection
import kotlinx.parcelize.Parcelize
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

    override fun getTheme(): Int = R.style.BookmarksImportBottomSheetDialogTheme

    private var _binding: ContentImportBookmarksFromGooglePreimportDialogBinding? = null

    val binding get() = _binding!!

    @Inject
    lateinit var viewModelFactory: FragmentViewModelFactory

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

        with(binding.selectFileButton) {
            setOnClickListener { onSelectFileButtonClicked() }
        }
    }

    private fun onImportButtonClicked() {
        ignoreCancellationEvents = true

        // Send fragment result
        val result = Bundle().apply {
            putParcelable(BUNDLE_RESULT_KEY, ImportBookmarksPreImportResult.ImportBookmarksFromGoogle)
        }
        setFragmentResult(FRAGMENT_RESULT_KEY, result)
    }

    private fun onSelectFileButtonClicked() {
        ignoreCancellationEvents = true

        val result = Bundle().apply {
            putParcelable(BUNDLE_RESULT_KEY, ImportBookmarksPreImportResult.SelectBookmarksFile)
        }
        setFragmentResult(FRAGMENT_RESULT_KEY, result)
    }

    override fun onCancel(dialog: DialogInterface) {
        if (ignoreCancellationEvents) {
            logcat(VERBOSE) { "onCancel: Ignoring cancellation event" }
            return
        }

        val result = Bundle().apply {
            putParcelable(BUNDLE_RESULT_KEY, ImportBookmarksPreImportResult.Cancel)
        }
        setFragmentResult(FRAGMENT_RESULT_KEY, result)
    }

    private fun configureCloseButton(binding: ContentImportBookmarksFromGooglePreimportDialogBinding) {
        binding.closeButton.setOnClickListener {
            ignoreCancellationEvents = true

            val result = Bundle().apply {
                putParcelable(BUNDLE_RESULT_KEY, ImportBookmarksPreImportResult.Cancel)
            }
            setFragmentResult(FRAGMENT_RESULT_KEY, result)

            (dialog as BottomSheetDialog).behavior.state = BottomSheetBehavior.STATE_HIDDEN
        }
    }

    companion object {
        const val FRAGMENT_RESULT_KEY = "ImportBookmarksPreImportDialog"
        const val BUNDLE_RESULT_KEY = "result"

        fun instance(): ImportFromGoogleBookmarksPreImportDialog {
            return ImportFromGoogleBookmarksPreImportDialog()
        }
    }

    /**
     * Result of the dialog, as determined by which button the user pressed or if they cancelled the dialog
     * This is available in the `Bundle` with key=BUNDLE_RESULT_KEY
     *
     * Example usage:
     * ```
     * supportFragmentManager.setFragmentResultListener(FRAGMENT_RESULT_KEY, this) { _, bundle ->
     *     val result = BundleCompat.getParcelable(bundle, BUNDLE_RESULT_KEY, ImportBookmarksPreImportResult::class.java)
     *     when (result) {
     *         ImportBookmarksPreImportResult.ImportBookmarksFromGoogle -> // Handle Google import
     *         ImportBookmarksPreImportResult.SelectBookmarksFile -> // Handle file selection
     *         ImportBookmarksPreImportResult.Cancel -> // Handle cancellation
     *     }
     * }
     * ```
     */
    sealed interface ImportBookmarksPreImportResult : Parcelable {

        /**
         * User chose to proceed with the Google import
         */
        @Parcelize
        data object ImportBookmarksFromGoogle : ImportBookmarksPreImportResult

        /**
         * User chose to select a bookmarks file
         */
        @Parcelize
        data object SelectBookmarksFile : ImportBookmarksPreImportResult

        /**
         * User cancelled the dialog
         */
        @Parcelize
        data object Cancel : ImportBookmarksPreImportResult
    }
}
