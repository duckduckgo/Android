/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.sync.impl.ui.setup

import android.app.Activity
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoFragment
import com.duckduckgo.common.ui.view.dialog.TextAlertDialogBuilder
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.hide
import com.duckduckgo.common.ui.view.makeSnackbarWithNoBottomInset
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.FragmentViewModelFactory
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.sync.impl.PermissionRequest
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.ShareAction
import com.duckduckgo.sync.impl.databinding.FragmentRecoverDataBinding
import com.duckduckgo.sync.impl.ui.setup.RecoverDataViewModel.Command
import com.duckduckgo.sync.impl.ui.setup.RecoverDataViewModel.Command.CheckIfUserHasStoragePermission
import com.duckduckgo.sync.impl.ui.setup.RecoverDataViewModel.Command.Close
import com.duckduckgo.sync.impl.ui.setup.RecoverDataViewModel.Command.FinishWithError
import com.duckduckgo.sync.impl.ui.setup.RecoverDataViewModel.Command.Next
import com.duckduckgo.sync.impl.ui.setup.RecoverDataViewModel.Command.RecoveryCodePDFSuccess
import com.duckduckgo.sync.impl.ui.setup.RecoverDataViewModel.Command.ShowError
import com.duckduckgo.sync.impl.ui.setup.RecoverDataViewModel.Command.ShowMessage
import com.duckduckgo.sync.impl.ui.setup.RecoverDataViewModel.ViewMode.CreatingAccount
import com.duckduckgo.sync.impl.ui.setup.RecoverDataViewModel.ViewMode.SignedIn
import com.duckduckgo.sync.impl.ui.setup.RecoverDataViewModel.ViewState
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.*

@InjectWith(FragmentScope::class)
class RecoverDataFragment : DuckDuckGoFragment(R.layout.fragment_recover_data) {
    @Inject
    lateinit var viewModelFactory: FragmentViewModelFactory

    @Inject
    lateinit var dispatcherProvider: DispatcherProvider

    @Inject
    lateinit var storagePermission: PermissionRequest

    @Inject
    lateinit var shareAction: ShareAction

    private val binding: FragmentRecoverDataBinding by viewBinding()

    private val viewModel: RecoverDataViewModel by lazy {
        ViewModelProvider(this, viewModelFactory)[RecoverDataViewModel::class.java]
    }

    private val listener: SyncSetupNavigationFlowListener?
        get() = activity as? SyncSetupNavigationFlowListener

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        observeUiEvents()
        configureListeners()
        registerForPermission()
    }

    private fun registerForPermission() {
        storagePermission.registerResultsCallback(this) {
            binding.root.makeSnackbarWithNoBottomInset(R.string.sync_permission_required_store_recovery_code, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun configureListeners() {
        binding.downloadAsPdfButton.setOnClickListener {
            viewModel.onDownloadAsPdfClicked()
        }
        binding.footerNextButton.setOnClickListener {
            viewModel.onNextClicked()
        }
        binding.copyCodeButton.setOnClickListener {
            viewModel.onCopyCodeClicked()
        }
        binding.restoreOnReinstallToggle.setOnCheckedChangeListener { _, isChecked ->
            viewModel.onToggleChanged(isChecked)
        }
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    viewModel.onBackPressed()
                }
            },
        )
    }

    private fun observeUiEvents() {
        viewModel
            .viewState()
            .flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
            .onEach { viewState -> renderViewState(viewState) }
            .launchIn(lifecycleScope)

        viewModel
            .commands()
            .flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)
        binding.recoveryCodeSkeleton.startShimmer()
    }

    private fun processCommand(it: Command) {
        when (it) {
            FinishWithError -> {
                requireActivity().setResult(Activity.RESULT_CANCELED)
                requireActivity().finish()
            }
            Close -> {
                requireActivity().setResult(Activity.RESULT_CANCELED)
                requireActivity().finish()
            }
            is Next -> {
                listener?.launchDeviceConnectedScreen()
            }
            is RecoveryCodePDFSuccess -> {
                shareAction.shareFile(requireContext(), it.recoveryCodePDFFile)
            }
            CheckIfUserHasStoragePermission -> {
                storagePermission.invokeOrRequestPermission {
                    viewModel.generateRecoveryCode(requireContext())
                }
            }
            is ShowMessage -> {
                Snackbar.make(binding.root, it.message, Snackbar.LENGTH_LONG).show()
            }
            is ShowError -> showDialogError(it)
        }
    }

    private fun showDialogError(it: ShowError) {
        val context = context ?: return
        TextAlertDialogBuilder(context)
            .setTitle(R.string.sync_dialog_error_title)
            .setMessage(getString(it.message) + "\n" + it.reason)
            .setPositiveButton(R.string.sync_dialog_error_ok)
            .addEventListener(
                object : TextAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked() {
                        viewModel.onErrorDialogDismissed()
                    }
                },
            ).show()
    }

    private fun renderViewState(viewState: ViewState) {
        when (val viewMode = viewState.viewMode) {
            is SignedIn -> {
                binding.recoveryCodeSkeleton.stopShimmer()
                binding.recoveryCodeSkeleton.gone()
                binding.recoverCodeContainer.show()
                binding.recoveryCodeText.text = viewMode.b64RecoveryCode
            }

            CreatingAccount -> {
                binding.recoverCodeContainer.hide()
                binding.recoveryCodeSkeleton.startShimmer()
            }
        }
        if (viewState.showRestoreOnReinstall) {
            binding.restoreOnReinstallToggle.show()
            binding.restoreOnReinstallToggle.quietlySetIsChecked(viewState.restoreOnReinstallEnabled) { _, isChecked ->
                viewModel.onToggleChanged(isChecked)
            }
        } else {
            binding.restoreOnReinstallToggle.gone()
        }
    }

    companion object {
        fun instance() = RecoverDataFragment()
    }
}
