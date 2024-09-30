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
import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.IntentCompat
import androidx.core.os.BundleCompat
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autofill.api.AutofillScreens.ImportGooglePassword
import com.duckduckgo.autofill.impl.R
import com.duckduckgo.autofill.impl.databinding.ContentChooseImportPasswordMethodBinding
import com.duckduckgo.autofill.impl.deviceauth.AutofillAuthorizationGracePeriod
import com.duckduckgo.autofill.impl.importing.PasswordImporter
import com.duckduckgo.autofill.impl.importing.PasswordImporter.ImportResult
import com.duckduckgo.autofill.impl.importing.PasswordImporter.ImportResult.Finished
import com.duckduckgo.autofill.impl.importing.gpm.webflow.ImportGooglePasswordResult
import com.duckduckgo.autofill.impl.ui.credential.dialog.animateClosed
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.ImportPasswordConfig
import com.duckduckgo.autofill.impl.ui.credential.management.viewing.SelectImportPasswordMethodDialog.Companion.Result.UserChoseGcmImport
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

    @Inject
    lateinit var passwordImporter: PasswordImporter

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

    private var _binding: ContentChooseImportPasswordMethodBinding? = null

    private val binding get() = _binding!!

    private val importGooglePasswordsFlowLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { activityResult ->
        Timber.i("cdr onActivityResult for Google Password Manager import flow. resultCode=${activityResult.resultCode}")

        if (activityResult.resultCode == Activity.RESULT_OK) {
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    activityResult.data?.let { data ->
                        val resultDetails = IntentCompat.getParcelableExtra(
                            data,
                            ImportGooglePasswordResult.RESULT_KEY_DETAILS,
                            ImportGooglePasswordResult::class.java,
                        )
                        when (resultDetails) {
                            is ImportGooglePasswordResult.Success -> {
                                binding.prePostViewSwitcher.displayedChild = 1
                                observeImportJob(resultDetails.importJobId)
                            }
                            is ImportGooglePasswordResult.Error -> processErrorResult()
                            is ImportGooglePasswordResult.UserCancelled, null -> {
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun observeImportJob(jobId: String) {
        passwordImporter.getImportStatus(jobId).collect {
            when (it) {
                is ImportResult.InProgress -> {
                    // we can show the in-progress state, update duplicates etc...
                    Timber.d("cdr Import in progress: ${it.savedCredentialIds.size} of ${it.importListSize}")
                    binding.postflow.inProgressFinishedViewSwitcher.displayedChild = 0
                }

                is Finished -> {
                    Timber.d(
                        "cdr Import finished: " +
                            "${it.savedCredentialIds.size} imported. " +
                            "${it.duplicatedPasswords.size} duplicates. " +
                            "Total=${it.importListSize}",
                    )
                    processSuccessResult(it)
                }
            }
        }
    }

    private fun processSuccessResult(result: Finished) {
        binding.postflow.importFinished.errorNotImported.visibility = GONE

        with(binding.postflow.importFinished.resultsImported) {
            setSecondaryText(result.savedCredentialIds.size.toString())
        }

        with(binding.postflow.importFinished.duplicatesNotImported) {
            setSecondaryText(result.duplicatedPasswords.size.toString())
            visibility = if (result.duplicatedPasswords.isNotEmpty()) VISIBLE else GONE
        }

        with(binding.postflow.importFinished.primaryCtaButton) {
            setOnClickListener {
                setResult(UserChoseGcmImport(result))
                dismiss()
            }
            setText(R.string.importPasswordsProcessingResultDialogDoneButtonText)
        }

        binding.postflow.inProgressFinishedViewSwitcher.displayedChild = 1
    }

    private fun processErrorResult() {
        binding.postflow.importFinished.resultsImported.visibility = GONE
        binding.postflow.importFinished.duplicatesNotImported.visibility = GONE
        binding.postflow.importFinished.errorNotImported.visibility = VISIBLE

        with(binding.postflow.importFinished.primaryCtaButton) {
            setOnClickListener {
                launchImportGcmFlow()
            }
            text = getString(R.string.importPasswordsProcessingResultDialogRetryButtonText)
        }

        binding.prePostViewSwitcher.displayedChild = 1
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
        _binding = ContentChooseImportPasswordMethodBinding.inflate(inflater, container, false)
        configureViews(binding)
        return binding.root
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun configureViews(binding: ContentChooseImportPasswordMethodBinding) {
        (dialog as BottomSheetDialog).behavior.state = BottomSheetBehavior.STATE_EXPANDED
        configureCloseButton(binding)

        val config =
            BundleCompat.getParcelable(requireArguments(), INPUT_KEY_CONFIG, ImportPasswordConfig::class.java)!!

        with(binding.preflow.importGcmButton) {
            visibility = if (config.canImportFromGooglePasswordManager) VISIBLE else GONE
            setOnClickListener { onImportGcmButtonClicked() }
        }
    }

    private fun onImportGcmButtonClicked() {
        launchImportGcmFlow()
    }

    private fun launchImportGcmFlow() {
        authorizationGracePeriod.requestExtendedGracePeriod()

        val intent = globalActivityStarter.startIntent(requireContext(), ImportGooglePassword.AutofillImportViaGooglePasswordManagerScreen)
        importGooglePasswordsFlowLauncher.launch(intent)
    }

    private fun setResult(result: Result?) {
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
            data class UserChoseGcmImport(val importResult: ImportResult) : Result

            @Parcelize
            data object UserCancelled : Result

            @Parcelize
            data object ErrorDuringImport : Result
        }
    }
}
