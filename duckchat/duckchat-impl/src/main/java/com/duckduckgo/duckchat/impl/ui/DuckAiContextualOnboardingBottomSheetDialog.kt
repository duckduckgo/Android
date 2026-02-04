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

package com.duckduckgo.duckchat.impl.ui

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.ui.setRoundCorners
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.duckchat.api.DuckChatNativeSettingsNoParams
import com.duckduckgo.duckchat.impl.databinding.BottomSheetDuckAiContextualOnboardingBinding
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@SuppressLint("NoBottomSheetDialog")
class DuckAiContextualOnboardingBottomSheetDialog(
    context: Context,
    private val viewModel: DuckAiContextualOnboardingViewModel,
    private val globalActivityStarter: GlobalActivityStarter,
    dispatcherProvider: DispatcherProvider,
    private val pixel: Pixel,
) : BottomSheetDialog(context) {

    private val binding: BottomSheetDuckAiContextualOnboardingBinding =
        BottomSheetDuckAiContextualOnboardingBinding.inflate(LayoutInflater.from(context))

    private val mainCoroutineScope = CoroutineScope(SupervisorJob() + dispatcherProvider.main())

    var eventListener: EventListener? = null

    init {
        setContentView(binding.root)

        setCancelable(false)
        setCanceledOnTouchOutside(false)
        behavior.isDraggable = false

        behavior.state = BottomSheetBehavior.STATE_EXPANDED

        setOnShowListener { dialogInterface ->
            pixel.fire(DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_ONBOARDING_DISPLAYED)
            (dialogInterface as BottomSheetDialog).setRoundCorners()
        }

        setOnDismissListener { mainCoroutineScope.cancel() }

        viewModel.commands
            .onEach { command ->
                when (command) {
                    is DuckAiContextualOnboardingViewModel.Command.OnboardingCompleted -> {
                        pixel.fire(DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_ONBOARDING_CONFIRM_PRESSED)
                        eventListener?.onConfirmed()
                        dismiss()
                    }
                }
            }
            .launchIn(mainCoroutineScope)

        binding.duckAiContextualOnboardingPrimaryButton.setOnClickListener {
            viewModel.completeOnboarding()
        }

        binding.duckAiContextualOnboardingSecondaryButton.setOnClickListener {
            pixel.fire(DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_ONBOARDING_SETTINGS_PRESSED)
            globalActivityStarter.start(context, DuckChatNativeSettingsNoParams)
            dismiss()
        }
    }

    interface EventListener {
        fun onConfirmed()
    }
}
