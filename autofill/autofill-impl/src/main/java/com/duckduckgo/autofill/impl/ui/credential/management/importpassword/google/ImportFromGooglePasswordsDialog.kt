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
import androidx.core.os.BundleCompat
import androidx.core.view.isEmpty
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.withResumed
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.autofill.api.AutofillImportLaunchSource
import com.duckduckgo.autofill.api.AutofillImportLaunchSource.PasswordManagementPromo
import com.duckduckgo.autofill.api.AutofillImportLaunchSource.Unknown
import com.duckduckgo.autofill.api.AutofillScreens.AutofillImportPasswordsScreen
import com.duckduckgo.autofill.api.promotion.PasswordsImportScreenPromotionPlugin
import com.duckduckgo.autofill.impl.R
import com.duckduckgo.autofill.impl.databinding.ContentImportFromGooglePasswordDialogBinding
import com.duckduckgo.autofill.impl.deviceauth.AutofillAuthorizationGracePeriod
import com.duckduckgo.autofill.impl.importing.CredentialImporter
import com.duckduckgo.autofill.impl.importing.gpm.webflow.ImportGooglePasswordResult
import com.duckduckgo.autofill.impl.importing.resolvePasswordsKeychainAnimationAsset
import com.duckduckgo.autofill.impl.ui.credential.dialog.animateClosed
import com.duckduckgo.autofill.impl.ui.credential.management.importpassword.ImportPasswordsPixelSender
import com.duckduckgo.autofill.impl.ui.credential.management.importpassword.google.ImportFromGooglePasswordsDialog.ImportPasswordsDialog.Companion.KEY_IMPORT_SUCCESS
import com.duckduckgo.autofill.impl.ui.credential.management.importpassword.google.ImportFromGooglePasswordsDialog.ImportPasswordsDialog.Companion.KEY_TAB_ID
import com.duckduckgo.autofill.impl.ui.credential.management.importpassword.google.ImportFromGooglePasswordsDialog.ImportPasswordsDialog.Companion.KEY_URL
import com.duckduckgo.autofill.impl.ui.credential.management.importpassword.google.ImportFromGooglePasswordsDialogViewModel.Command
import com.duckduckgo.autofill.impl.ui.credential.management.importpassword.google.ImportFromGooglePasswordsDialogViewModel.ViewMode
import com.duckduckgo.autofill.impl.ui.credential.management.importpassword.google.ImportFromGooglePasswordsDialogViewModel.ViewMode.BrowserPromoPreImport
import com.duckduckgo.autofill.impl.ui.credential.management.importpassword.google.ImportFromGooglePasswordsDialogViewModel.ViewMode.DeterminingFirstView
import com.duckduckgo.autofill.impl.ui.credential.management.importpassword.google.ImportFromGooglePasswordsDialogViewModel.ViewMode.FlowTerminated
import com.duckduckgo.autofill.impl.ui.credential.management.importpassword.google.ImportFromGooglePasswordsDialogViewModel.ViewMode.ImportError
import com.duckduckgo.autofill.impl.ui.credential.management.importpassword.google.ImportFromGooglePasswordsDialogViewModel.ViewMode.ImportSuccess
import com.duckduckgo.autofill.impl.ui.credential.management.importpassword.google.ImportFromGooglePasswordsDialogViewModel.ViewMode.Importing
import com.duckduckgo.autofill.impl.ui.credential.management.importpassword.google.ImportFromGooglePasswordsDialogViewModel.ViewMode.PreImport
import com.duckduckgo.autofill.impl.ui.credential.management.importpassword.google.ImportFromGooglePasswordsDialogViewModel.ViewState
import com.duckduckgo.codegen.annotations.InjectWith
import com.duckduckgo.common.ui.applyBottomSystemBarInsetPadding
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.prependIconToText
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.utils.FragmentViewModelFactory
import com.duckduckgo.common.utils.extensions.html
import com.duckduckgo.credentialexchange.api.CredentialExchangeLauncher
import com.duckduckgo.di.android.support.AndroidSupportInjection
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import logcat.LogPriority.ERROR
import logcat.LogPriority.VERBOSE
import logcat.asLog
import logcat.logcat
import javax.inject.Inject
import com.google.android.material.R as MaterialR

@InjectWith(FragmentScope::class)
class ImportFromGooglePasswordsDialog : BottomSheetDialogFragment() {

    @Inject
    lateinit var importPasswordsPixelSender: ImportPasswordsPixelSender

    @Inject
    lateinit var faviconManager: FaviconManager

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    @Inject
    lateinit var authorizationGracePeriod: AutofillAuthorizationGracePeriod

    @Inject
    lateinit var credentialExchangeLauncher: CredentialExchangeLauncher

    @Inject
    lateinit var externalImportAuthSuppressor: ExternalImportAuthSuppressor

    @Inject
    lateinit var viewModelFactory: FragmentViewModelFactory

    private val viewModel by bindViewModel<ImportFromGooglePasswordsDialogViewModel>()

    private var _binding: ContentImportFromGooglePasswordDialogBinding? = null

    private val binding get() = _binding!!

    /**
     * To capture all the ways the BottomSheet can be dismissed, we might end up with onCancel being called when we don't want it
     * This flag is set to true when taking an action which dismisses the dialog, but should not be treated as a cancellation.
     */
    private var ignoreCancellationEvents = false

    private var successImport = false

    private val promoCallback = object : PasswordsImportScreenPromotionPlugin.Callback {
        override fun dismissHost() {
            dismiss()
        }
    }

    private val importGooglePasswordsFlowLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { activityResult ->
        if (activityResult.resultCode == Activity.RESULT_OK) {
            viewLifecycleOwner.lifecycleScope.launch {
                activityResult.data?.let { data ->
                    processImportFlowResult(data)
                }
            }
        }
    }

    override fun getTheme(): Int = R.style.AutofillBottomSheetDialogThemeEdgeToEdge

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
        _binding = ContentImportFromGooglePasswordDialogBinding.inflate(inflater, container, false)
        configureViews(binding)
        binding.legacy.legacyContent.applyBottomSystemBarInsetPadding()
        binding.redesign.applyBottomSystemBarInsetPadding()
        observeViewModel()
        logcat { "Creating ImportFromGooglePasswordsDialog with launch source: ${getLaunchSource()}" }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (savedInstanceState != null) return

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.commands().collect { processCommand(it) }
            }
        }

        val launchSource = getLaunchSource()
        viewModel.determineInitialUi(launchSource, canShowPreImportDialog(launchSource))
    }

    override fun onDestroyView() {
        _binding = null
        authorizationGracePeriod.removeRequestForExtendedGracePeriod()
        super.onDestroyView()
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

        importPasswordsPixelSender.onUserCancelledImportPasswordsDialog(getLaunchSource())
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        logcat { "Dismissing ImportFromGooglePasswordsDialog, successful import? $successImport" }
        setResult(successImport)
    }

    private fun configureViews(binding: ContentImportFromGooglePasswordDialogBinding) {
        (dialog as BottomSheetDialog).behavior.state = BottomSheetBehavior.STATE_EXPANDED
        configureCloseButton(binding)

        with(binding.legacy.preflow.importGcmButton) {
            setOnClickListener { onImportGcmButtonClicked() }
        }

        with(binding.legacy.postflow.importFinished.primaryCtaButton) {
            setOnClickListener {
                dismiss()
            }
        }

        binding.legacy.preflow.declineButton.setOnClickListener {
            viewModel.onInBrowserPromoDismissed()
        }

        binding.preImport.preImportButton.setOnClickListener { onImportGcmButtonClicked() }
        binding.browserPromoPreImport.browserPromoImportButton.setOnClickListener { onImportGcmButtonClicked() }
        binding.browserPromoPreImport.browserPromoDeclineButton.setOnClickListener { viewModel.onInBrowserPromoDismissed() }
        binding.importing.importingCancelButton.setOnClickListener { animateClosed() }
        binding.importSuccess.successDoneButton.setOnClickListener { dismiss() }
        binding.importSuccess.successDoneSecondaryButton.setOnClickListener { dismiss() }
        binding.importError.errorDoneButton.setOnClickListener { dismiss() }
    }

    private fun configureCloseButton(binding: ContentImportFromGooglePasswordDialogBinding) {
        binding.legacy.closeButton.setOnClickListener { animateClosed() }
        binding.redesignCloseButton.setOnClickListener { animateClosed() }
    }

    private fun animateClosed() {
        (dialog as BottomSheetDialog).animateClosed()
    }

    private fun processCommand(command: Command) {
        when (command) {
            Command.StartWebFlow -> startImportWebFlow()
            Command.StartCredentialExchange -> startCredentialExchangeImport()
        }
    }

    private fun observeViewModel() {
        viewModel.viewState.flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
            .onEach { viewState -> renderViewState(viewState) }
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    private fun renderViewState(viewState: ViewState) {
        logcat { "Autofill-import: rendering ${viewState.viewMode}, redesign enabled: ${viewState.isRedesignEnabled}" }
        handleViewStateSideEffects(viewState)
        renderContentVisibility(viewState)
        if (viewState.isRedesignEnabled) {
            renderRedesignViewState(viewState)
        } else {
            renderLegacyViewState(viewState)
        }
    }

    private fun handleViewStateSideEffects(viewState: ViewState) {
        when (viewState.viewMode) {
            // a cancel event from here on is the dialog closing after import started, not the user backing out of it
            is Importing -> ignoreCancellationEvents = true
            is ImportSuccess -> successImport = true
            else -> Unit
        }
    }

    private fun renderContentVisibility(viewState: ViewState) {
        val hasContent = viewState.viewMode !is DeterminingFirstView
        binding.legacy.root.isVisible = hasContent && !viewState.isRedesignEnabled
        binding.redesign.isVisible = hasContent && viewState.isRedesignEnabled
        if (binding.redesign.isVisible && viewState.viewMode.usesFullHeightSheet()) {
            expandToFullHeight()
        }
    }

    private fun ViewMode.usesFullHeightSheet() = this is Importing || this is ImportSuccess || this is ImportError

    private fun expandToFullHeight() {
        val bottomSheetDialog = dialog as BottomSheetDialog
        bottomSheetDialog.findViewById<View>(MaterialR.id.design_bottom_sheet)?.updateLayoutParams { height = ViewGroup.LayoutParams.MATCH_PARENT }
        bottomSheetDialog.behavior.skipCollapsed = true
        bottomSheetDialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    private fun renderRedesignViewState(viewState: ViewState) {
        binding.redesignCloseButton.isInvisible = viewState.viewMode !is PreImport && viewState.viewMode !is BrowserPromoPreImport
        when (viewState.viewMode) {
            is PreImport -> redesignShowPreImportView(viewState.usesCredentialExchange)
            is BrowserPromoPreImport -> redesignShowInBrowserPromoPreImportView(
                viewState.usesCredentialExchange,
                viewState.isPictogramsEnabled,
            )
            is ImportError -> redesignSelectViewToShow(binding.importError.root)
            is ImportSuccess -> redesignShowImportSuccessView(viewState.viewMode)
            is Importing -> redesignSelectViewToShow(binding.importing.root)
            is DeterminingFirstView -> Unit
            is FlowTerminated -> dismiss()
        }
    }

    private fun redesignShowPreImportView(usesCredentialExchange: Boolean) {
        val subtitle = if (usesCredentialExchange) {
            R.string.importPasswordsChooseMethodDialogSubtitleDeviceAuth
        } else {
            R.string.importPasswordsChooseMethodDialogSubtitle
        }
        binding.preImport.preImportSubtitle.text = getString(subtitle)
        redesignSelectViewToShow(binding.preImport.root)
    }

    private fun redesignShowInBrowserPromoPreImportView(usesCredentialExchange: Boolean, isPictogramsEnabled: Boolean) {
        with(binding.browserPromoPreImport) {
            val (title, subtitle) = if (usesCredentialExchange) {
                R.string.passwords_import_promo_in_browser_title_device_auth to R.string.passwords_import_promo_subtitle_device_auth
            } else {
                R.string.passwords_import_promo_in_browser_title to R.string.passwords_import_promo_subtitle
            }
            browserPromoTitle.text = getString(title)
            browserPromoSubtitle.text = binding.root.context.prependIconToText(subtitle, R.drawable.ic_lock_solid_12)
            val animationAsset = resolvePasswordsKeychainAnimationAsset(isPictogramsEnabled)
            animationAsset.staticRes?.let { staticRes ->
                browserPromoIllustration.cancelAnimation()
                browserPromoIllustration.setImageResource(staticRes)
            } ?: run {
                browserPromoIllustration.setAnimation(animationAsset.animationRes)
                browserPromoIllustration.playAnimation()
            }
        }
        redesignSelectViewToShow(binding.browserPromoPreImport.root)
    }

    private fun redesignShowImportSuccessView(viewMode: ImportSuccess) {
        val result = viewMode.importResult
        with(binding.importSuccess) {
            val imported = getString(R.string.importPasswordsProcessingResultDialogResultPasswordsImported, result.savedCredentials)
            successTitle.text = imported.html(binding.root.context)
            val failed = getString(R.string.importPasswordsProcessingResultDialogResultDuplicatesSkipped, result.numberSkipped)
            successFailedRow.setPrimaryText(failed.html(binding.root.context))
            successResults.isVisible = result.numberSkipped > 0

            if (successPromoContainer.isEmpty()) {
                val promo = viewMode.promo?.createView(requireContext(), promoCallback)
                promo?.let(successPromoContainer::addView)
                successPromoContainer.isVisible = promo != null
            }
            val hasPromo = successPromoContainer.isVisible
            successIcon.isVisible = !hasPromo
            successDoneButton.isVisible = !hasPromo
            successDoneSecondaryButton.isVisible = hasPromo
        }
        redesignSelectViewToShow(binding.importSuccess.root)
    }

    private fun redesignSelectViewToShow(toShow: View) {
        listOf(
            binding.preImport.root,
            binding.browserPromoPreImport.root,
            binding.importing.root,
            binding.importSuccess.root,
            binding.importError.root,
        ).forEach { view -> view.isVisible = view == toShow }
    }

    private fun renderLegacyViewState(viewState: ViewState) {
        when (viewState.viewMode) {
            is PreImport -> legacySwitchDialogShowPreImportView(viewState.usesCredentialExchange)
            is BrowserPromoPreImport -> legacySwitchDialogShowInBrowserPromoPreImportView(
                viewState.usesCredentialExchange,
                viewState.isPictogramsEnabled,
            )
            is ImportError -> legacyProcessErrorResult()
            is ImportSuccess -> legacyProcessSuccessResult(viewState.viewMode.importResult)
            is Importing -> legacySwitchDialogShowImportInProgressView()
            is DeterminingFirstView -> Unit
            is FlowTerminated -> dismiss()
        }
    }

    private fun legacySwitchDialogShowPreImportView(usesCredentialExchange: Boolean) {
        with(binding.legacy.preflow) {
            dialogTitle.text = getString(R.string.importPasswordsChooseMethodDialogTitle)
            val subtitle = if (usesCredentialExchange) {
                R.string.importPasswordsChooseMethodDialogSubtitleDeviceAuth
            } else {
                R.string.importPasswordsChooseMethodDialogSubtitle
            }
            onboardingSubtitle.text = getString(subtitle)
            declineButton.gone()
            topIllustrationAnimated.gone()
            appIcon.show()
        }
        binding.legacy.prePostViewSwitcher.displayedChild = 0
    }

    private fun legacySwitchDialogShowInBrowserPromoPreImportView(usesCredentialExchange: Boolean, isPictogramsEnabled: Boolean) {
        with(binding.legacy.preflow) {
            val (title, subtitle) = if (usesCredentialExchange) {
                R.string.passwords_import_promo_in_browser_title_device_auth to R.string.passwords_import_promo_subtitle_device_auth
            } else {
                R.string.passwords_import_promo_in_browser_title to R.string.passwords_import_promo_subtitle
            }
            dialogTitle.text = getString(title)
            onboardingSubtitle.text = binding.root.context.prependIconToText(subtitle, R.drawable.ic_lock_solid_12)
            declineButton.show()
            val animationAsset = resolvePasswordsKeychainAnimationAsset(isPictogramsEnabled)
            animationAsset.staticRes?.let { staticRes ->
                topIllustrationAnimated.cancelAnimation()
                topIllustrationAnimated.setImageResource(staticRes)
            } ?: run {
                topIllustrationAnimated.setAnimation(animationAsset.animationRes)
            }
            topIllustrationAnimated.show()
            if (animationAsset.staticRes == null) {
                topIllustrationAnimated.playAnimation()
            }
            appIcon.gone()
        }
        binding.legacy.prePostViewSwitcher.displayedChild = 0
    }

    private fun legacySwitchDialogShowImportInProgressView() {
        binding.legacy.prePostViewSwitcher.displayedChild = 1
        binding.legacy.postflow.inProgressFinishedViewSwitcher.displayedChild = 0
    }

    private fun legacySwitchDialogShowImportResultsView() {
        binding.legacy.prePostViewSwitcher.displayedChild = 1
        binding.legacy.postflow.inProgressFinishedViewSwitcher.displayedChild = 1
    }

    private fun legacyProcessSuccessResult(result: CredentialImporter.ImportResult.Finished) {
        binding.legacy.postflow.importFinished.errorNotImported.visibility = View.GONE
        binding.legacy.postflow.appIcon.setImageDrawable(
            ContextCompat.getDrawable(
                binding.root.context,
                R.drawable.success_128,
            ),
        )
        binding.legacy.postflow.dialogTitle.text = getString(R.string.importPasswordsProcessingResultDialogTitleUponSuccess)

        with(binding.legacy.postflow.importFinished.resultsImported) {
            val output = getString(R.string.importPasswordsProcessingResultDialogResultPasswordsImported, result.savedCredentials)
            setPrimaryText(output.html(binding.root.context))
        }

        with(binding.legacy.postflow.importFinished.duplicatesNotImported) {
            val output = getString(R.string.importPasswordsProcessingResultDialogResultDuplicatesSkipped, result.numberSkipped)
            setPrimaryText(output.html(binding.root.context))
            visibility = if (result.numberSkipped > 0) View.VISIBLE else View.GONE
        }

        legacySwitchDialogShowImportResultsView()
    }

    private fun legacyProcessErrorResult() {
        binding.legacy.postflow.importFinished.resultsImported.visibility = View.GONE
        binding.legacy.postflow.importFinished.duplicatesNotImported.visibility = View.GONE
        binding.legacy.postflow.importFinished.errorNotImported.visibility = View.VISIBLE

        binding.legacy.postflow.dialogTitle.text = getString(R.string.importPasswordsProcessingResultDialogTitleBeforeSuccess)
        binding.legacy.postflow.appIcon.setImageDrawable(
            ContextCompat.getDrawable(
                binding.root.context,
                R.drawable.passwords_import_128,
            ),
        )

        legacySwitchDialogShowImportResultsView()
    }

    private fun onImportGcmButtonClicked() {
        viewModel.onImportButtonClicked(getLaunchSource())
        importPasswordsPixelSender.onImportPasswordsDialogImportButtonClicked(getLaunchSource())
    }

    private fun startImportWebFlow() {
        authorizationGracePeriod.requestExtendedGracePeriod()

        val intent = globalActivityStarter.startIntent(
            requireContext(),
            AutofillImportPasswordsScreen(getLaunchSource()),
        )
        importGooglePasswordsFlowLauncher.launch(intent)

        // we don't want the eventual dismissal of this dialog to count as a cancellation
        ignoreCancellationEvents = true
    }

    private fun startCredentialExchangeImport() {
        viewLifecycleOwner.lifecycleScope.launch {
            externalImportAuthSuppressor.suppressAuthPromptWhileRunning {
                val exchangeResult = credentialExchangeLauncher.launchImportFlow()
                // the result can arrive before the host restarts; staying in the block until resumed keeps the suppression available to its onStart
                viewLifecycleOwner.lifecycle.withResumed {
                    val launchSource = getLaunchSource()
                    viewModel.onCredentialExchangeFinished(exchangeResult, launchSource, canShowPreImportDialog(launchSource))
                }
            }
        }
    }

    private fun ImportFromGooglePasswordsDialog.processImportFlowResult(data: Intent) {
        (IntentCompat.getParcelableExtra(data, ImportGooglePasswordResult.RESULT_KEY_DETAILS, ImportGooglePasswordResult::class.java)).let {
            when (it) {
                is ImportGooglePasswordResult.Success -> viewModel.onWebFlowFinishedSuccessfully()
                is ImportGooglePasswordResult.Error -> viewModel.onWebFlowFinishedWithError(it.reason)
                is ImportGooglePasswordResult.UserCancelled -> viewModel.onWebFlowCancelled(canShowPreImportDialog(getLaunchSource()))
                else -> {}
            }
        }
    }

    private fun setResult(successImport: Boolean) {
        logcat { "Autofill-import: Setting result for ImportFromGooglePasswordsDialog, successful import? $successImport. tabId=${getTabId()}" }

        getTabId()?.let { tabId ->
            val result = Bundle().also {
                it.putBoolean(KEY_IMPORT_SUCCESS, successImport)
                it.putString(KEY_URL, getOriginalUrl())
            }

            val resultKey = ImportPasswordsDialog.resultKey(tabId)
            findCorrectFragmentManager()?.setFragmentResult(resultKey, result)
        }
    }

    private fun findCorrectFragmentManager(): FragmentManager? {
        val result = kotlin.runCatching {
            parentFragment?.parentFragmentManager ?: parentFragmentManager
        }.onFailure { logcat(ERROR) { "Autofill-import: Failed to find a valid fragment manager ${it.asLog()}" } }

        return result.getOrNull()
    }

    private fun getLaunchSource() =
        BundleCompat.getParcelable(arguments ?: Bundle(), KEY_LAUNCH_SOURCE, AutofillImportLaunchSource::class.java) ?: Unknown

    private fun getTabId(): String? = arguments?.getString(KEY_TAB_ID)

    private fun getOriginalUrl() = arguments?.getString(KEY_URL)

    private fun canShowPreImportDialog(launchSource: AutofillImportLaunchSource): Boolean {
        return when (launchSource) {
            PasswordManagementPromo -> false
            else -> true
        }
    }

    private inline fun <reified V : ViewModel> bindViewModel() = lazy { ViewModelProvider(this, viewModelFactory)[V::class.java] }

    companion object {

        private const val KEY_LAUNCH_SOURCE = "launchSource"

        fun instance(importSource: AutofillImportLaunchSource, tabId: String? = null, originalUrl: String? = null): ImportFromGooglePasswordsDialog {
            val fragment = ImportFromGooglePasswordsDialog()
            fragment.arguments = Bundle().apply {
                putParcelable(KEY_LAUNCH_SOURCE, importSource)
                putString(KEY_TAB_ID, tabId)
                putString(KEY_URL, originalUrl)
            }
            return fragment
        }
    }

    interface ImportPasswordsDialog {

        companion object {

            fun resultKey(tabId: String) = "${prefix(tabId, TAG)}/Result"

            const val TAG = "ImportPasswordsDialog"
            const val KEY_URL = "url"
            const val KEY_IMPORT_SUCCESS = "importSuccess"
            const val KEY_TAB_ID = "tabId"

            private fun prefix(tabId: String, tag: String): String {
                return "$tabId/$tag"
            }
        }
    }
}
