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

package com.duckduckgo.autofill.impl.ui.credential.management.viewing

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.IntentCompat
import androidx.core.os.BundleCompat
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autofill.api.AutofillScreens.ImportGooglePassword
import com.duckduckgo.autofill.api.AutofillScreens.ImportGooglePassword.Result as ImportResult
import com.duckduckgo.autofill.impl.R
import com.duckduckgo.autofill.impl.databinding.ContentChooseImportPasswordMethodBinding
import com.duckduckgo.autofill.impl.importing.CsvPasswordImporter
import com.duckduckgo.autofill.impl.importing.CsvPasswordImporter.ImportResult.Error
import com.duckduckgo.autofill.impl.importing.CsvPasswordImporter.ImportResult.Success
import com.duckduckgo.autofill.impl.ui.credential.dialog.animateClosed
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.ImportPasswordConfig
import com.duckduckgo.common.ui.view.prependIconToText
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import timber.log.Timber

@InjectWith(FragmentScope::class)
class SelectImportPasswordMethodDialog : BottomSheetDialogFragment() {

    @Inject
    lateinit var pixel: Pixel

    /**
     * To capture all the ways the BottomSheet can be dismissed, we might end up with onCancel being called when we don't want it
     * This flag is set to true when taking an action which dismisses the dialog, but should not be treated as a cancellation.
     */
    private var ignoreCancellationEvents = false

    override fun getTheme(): Int = R.style.AutofillBottomSheetDialogTheme

    @Inject
    lateinit var faviconManager: FaviconManager

    @Inject
    lateinit var csvPasswordImporter: CsvPasswordImporter

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    private val importGooglePasswordsFlowLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        Timber.i("cdr onActivityResult for Google Password Manager import flow. resultCode=${result.resultCode}")

        if (result.resultCode == Activity.RESULT_OK) {
            when (val resultDetails = parseGooglePasswordImportResultDetails(result)) {
                is ImportResult.Success -> setResult(Result.UserChoseGcmImport(resultDetails.importedCount))
                is ImportResult.Error -> setResult(Result.ErrorDuringImport)
                is ImportResult.UserCancelled -> {}
            }
        }
    }

    private val importCsvLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data: Intent? = result.data
            val fileUrl = data?.data

            Timber.i("cdr onActivityResult for CSV file request. resultCode=${result.resultCode}. uri=$fileUrl")

            if (fileUrl != null) {
                lifecycleScope.launch {
                    when (val importResult = csvPasswordImporter.importCsv(fileUrl)) {
                        is Success -> {
                            setResult(Result.UserChoseCsvImport(importResult.passwordIdsImported.size))
                        }

                        Error -> setResult(Result.ErrorDuringImport)
                    }
                }
            }
        }
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
        val binding = ContentChooseImportPasswordMethodBinding.inflate(inflater, container, false)
        configureViews(binding)
        return binding.root
    }

    private fun configureViews(binding: ContentChooseImportPasswordMethodBinding) {
        (dialog as BottomSheetDialog).behavior.state = BottomSheetBehavior.STATE_EXPANDED
        configureCloseButton(binding)

        val config = BundleCompat.getParcelable(requireArguments(), INPUT_KEY_CONFIG, ImportPasswordConfig::class.java)!!

        with(binding.onboardingSubtitle) {
            text = context.prependIconToText(R.string.importPasswordsChooseMethodDialogSubtitle, R.drawable.ic_lock_solid_12)
        }

        with(binding.importGcmButton) {
            visibility = if (config.canImportFromGooglePasswordManager) VISIBLE else GONE
            setOnClickListener { onImportGcmButtonClicked() }
        }

        with(binding.csvButton) {
            visibility = if (config.canImportFromCsv) VISIBLE else GONE
            setOnClickListener { onImportCsvButtonClicked() }
        }
    }

    private fun onImportGcmButtonClicked() {
        launchImportGcmFlow()
    }

    private fun launchImportGcmFlow() {
        val intent = globalActivityStarter.startIntent(requireContext(), ImportGooglePassword.AutofillImportViaGooglePasswordManagerScreen)
        importGooglePasswordsFlowLauncher.launch(intent)
    }

    private fun onImportCsvButtonClicked() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        importCsvLauncher.launch(intent)
    }

    private fun onImportDesktopSyncButtonClicked() {
        setResult(Result.UserChoseDesktopSyncImport)
    }

    private fun setResult(result: Result) {
        val resultBundle = Bundle().apply {
            putParcelable(RESULT_KEY_DETAILS, result)
        }
        setFragmentResult(RESULT_KEY, resultBundle)
        dismiss()
    }

    override fun onCancel(dialog: DialogInterface) {
        if (ignoreCancellationEvents) {
            Timber.v("onCancel: Ignoring cancellation event")
            return
        }
        setResult(Result.UserCancelled)
    }

    private fun configureCloseButton(binding: ContentChooseImportPasswordMethodBinding) {
        binding.closeButton.setOnClickListener { (dialog as BottomSheetDialog).animateClosed() }
    }

    private fun parseGooglePasswordImportResultDetails(result: ActivityResult): ImportResult {
        return IntentCompat.getParcelableExtra(result.data!!, ImportResult.RESULT_KEY_DETAILS, ImportResult::class.java)!!
    }

    companion object {

        fun instance(configuration: ImportPasswordConfig): SelectImportPasswordMethodDialog {
            val fragment = SelectImportPasswordMethodDialog()
            fragment.arguments = Bundle().also {
                it.putParcelable(INPUT_KEY_CONFIG, configuration)
            }
            return fragment
        }

        const val RESULT_KEY = "SelectImportPasswordMethodDialogResult"
        const val RESULT_KEY_DETAILS = "SelectImportPasswordMethodDialogResultDetails"

        private const val INPUT_KEY_CONFIG = "config"

        sealed interface Result : Parcelable {
            @Parcelize
            data class UserChoseGcmImport(val numberImported: Int) : Result

            @Parcelize
            data class UserChoseCsvImport(val numberImported: Int) : Result

            @Parcelize
            data object UserChoseDesktopSyncImport : Result

            @Parcelize
            data object UserCancelled : Result

            @Parcelize
            data object ErrorDuringImport : Result
        }
    }
}
