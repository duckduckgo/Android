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

package com.duckduckgo.sync.impl.ui.setup

import android.app.Activity
import android.os.Bundle
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.DuckDuckGoFragment
import com.duckduckgo.app.global.FragmentViewModelFactory
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.mobile.android.ui.view.gone
import com.duckduckgo.mobile.android.ui.view.hide
import com.duckduckgo.mobile.android.ui.view.makeSnackbarWithNoBottomInset
import com.duckduckgo.mobile.android.ui.view.show
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import com.duckduckgo.sync.impl.PermissionRequest
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.RecoveryCodePDF
import com.duckduckgo.sync.impl.ShareAction
import com.duckduckgo.sync.impl.databinding.FragmentRecoveryCodeBinding
import com.duckduckgo.sync.impl.ui.setup.SaveRecoveryCodeViewModel.Command
import com.duckduckgo.sync.impl.ui.setup.SaveRecoveryCodeViewModel.Command.CheckIfUserHasStoragePermission
import com.duckduckgo.sync.impl.ui.setup.SaveRecoveryCodeViewModel.Command.Error
import com.duckduckgo.sync.impl.ui.setup.SaveRecoveryCodeViewModel.Command.Finish
import com.duckduckgo.sync.impl.ui.setup.SaveRecoveryCodeViewModel.Command.RecoveryCodePDFSuccess
import com.duckduckgo.sync.impl.ui.setup.SaveRecoveryCodeViewModel.Command.ShowMessage
import com.duckduckgo.sync.impl.ui.setup.SaveRecoveryCodeViewModel.ViewMode.CreatingAccount
import com.duckduckgo.sync.impl.ui.setup.SaveRecoveryCodeViewModel.ViewMode.SignedIn
import com.duckduckgo.sync.impl.ui.setup.SaveRecoveryCodeViewModel.ViewState
import com.google.android.material.snackbar.Snackbar
import javax.inject.*
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@InjectWith(FragmentScope::class)
class SaveRecoveryCodeFragment : DuckDuckGoFragment(R.layout.fragment_recovery_code) {
    @Inject
    lateinit var viewModelFactory: FragmentViewModelFactory

    @Inject
    lateinit var dispatcherProvider: DispatcherProvider

    @Inject
    lateinit var storagePermission: PermissionRequest

    @Inject
    lateinit var recoveryCodePDF: RecoveryCodePDF

    @Inject
    lateinit var shareAction: ShareAction

    private val binding: FragmentRecoveryCodeBinding by viewBinding()

    private val viewModel: SaveRecoveryCodeViewModel by lazy {
        ViewModelProvider(this, viewModelFactory)[SaveRecoveryCodeViewModel::class.java]
    }

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
        binding.footerPrimaryButton.setOnClickListener {
            viewModel.onSaveRecoveryCodeClicked()
        }
        binding.footerSecondaryButton.setOnClickListener {
            viewModel.onCopyCodeClicked()
        }
        binding.footerNextButton.setOnClickListener {
            viewModel.onNextClicked()
        }
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
            Error -> {
                requireActivity().setResult(Activity.RESULT_CANCELED)
                requireActivity().finish()
            }
            is Finish -> {
                requireActivity().setResult(Activity.RESULT_OK)
                requireActivity().finish()
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
        }
    }

    private fun renderViewState(viewState: ViewState) {
        when (val viewMode = viewState.viewMode) {
            is SignedIn -> {
                binding.recoveryCodeSkeleton.stopShimmer()
                binding.recoveryCodeSkeleton.gone()
                binding.recoverCodeContainer.show()
                binding.qrCodeImageView.show()
                binding.qrCodeImageView.setImageBitmap(viewMode.loginQRCode)
                binding.recoveryCodeText.text = viewMode.b64RecoveryCode
            }

            CreatingAccount -> {
                binding.recoverCodeContainer.hide()
                binding.recoveryCodeSkeleton.startShimmer()
            }
        }
    }

    companion object {
        fun instance() = SaveRecoveryCodeFragment()
    }
}
