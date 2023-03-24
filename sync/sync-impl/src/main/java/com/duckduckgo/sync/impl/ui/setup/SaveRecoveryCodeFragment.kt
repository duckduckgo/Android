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

import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.global.DuckDuckGoFragment
import com.duckduckgo.app.global.FragmentViewModelFactory
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.mobile.android.ui.view.hide
import com.duckduckgo.mobile.android.ui.view.show
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.databinding.FragmentRecoveryCodeBinding
import com.duckduckgo.sync.impl.ui.setup.SaveRecoveryCodeViewModel.Command
import com.duckduckgo.sync.impl.ui.setup.SaveRecoveryCodeViewModel.Command.Error
import com.duckduckgo.sync.impl.ui.setup.SaveRecoveryCodeViewModel.Command.Finish
import com.duckduckgo.sync.impl.ui.setup.SaveRecoveryCodeViewModel.ViewMode.AccountCreated
import com.duckduckgo.sync.impl.ui.setup.SaveRecoveryCodeViewModel.ViewMode.CreatingAccount
import com.duckduckgo.sync.impl.ui.setup.SaveRecoveryCodeViewModel.ViewState
import com.google.zxing.BarcodeFormat.QR_CODE
import com.google.zxing.EncodeHintType
import com.journeyapps.barcodescanner.BarcodeEncoder
import javax.inject.*
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@InjectWith(FragmentScope::class)
class SaveRecoveryCodeFragment : DuckDuckGoFragment(R.layout.fragment_recovery_code) {
    @Inject
    lateinit var viewModelFactory: FragmentViewModelFactory

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
    }

    private fun configureListeners() {
        binding.footerPrimaryButton.setOnClickListener {
        }
        binding.footerSecondaryButton.setOnClickListener {
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
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)
        binding.recoveryCodeSkeleton.startShimmer()
    }

    private fun processCommand(it: Command) {
        when (it) {
            Error -> requireActivity().finish()
            Finish -> requireActivity().finish()
        }
    }

    private fun renderViewState(viewState: ViewState) {
        when (val viewMode = viewState.viewMode) {
            is AccountCreated -> {
                binding.recoveryCodeSkeleton.stopShimmer()
                binding.recoveryCodeSkeleton.hide()
                binding.recoverCodeContainer.show()

                val barcodeEncoder = BarcodeEncoder()
                val bitmap: Bitmap = barcodeEncoder.encodeBitmap(
                    viewMode.loginQRCode,
                    QR_CODE,
                    resources.getDimensionPixelSize(R.dimen.qrSizeSmall),
                    resources.getDimensionPixelSize(R.dimen.qrSizeSmall),
                    mapOf(EncodeHintType.MARGIN to 0)
                )
                binding.qrCodeImageView.show()
                binding.qrCodeImageView.setImageBitmap(bitmap)
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
