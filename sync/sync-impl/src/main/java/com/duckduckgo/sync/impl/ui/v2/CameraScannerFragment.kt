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

package com.duckduckgo.sync.impl.ui.v2

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.core.view.OneShotPreDrawListener
import androidx.core.view.doOnPreDraw
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoFragment
import com.duckduckgo.common.utils.FragmentViewModelFactory
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.sync.impl.databinding.FragmentSyncV2CameraScannerBinding
import com.duckduckgo.sync.impl.ui.v2.IntroAnimationViewModel.Command
import com.duckduckgo.sync.impl.ui.v2.IntroAnimationViewModel.Command.PlayIntroAnimation
import com.duckduckgo.sync.impl.ui.v2.IntroAnimationViewModel.Command.RequestCameraPermission
import com.duckduckgo.sync.impl.ui.v2.IntroAnimationViewModel.ViewMode
import com.duckduckgo.sync.impl.ui.v2.IntroAnimationViewModel.ViewState
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@InjectWith(FragmentScope::class)
class CameraScannerFragment : DuckDuckGoFragment() {
    private var _binding: FragmentSyncV2CameraScannerBinding? = null
    private val binding
        get() = requireNotNull(_binding) {
            "Fragment $this tried to access ViewBinding outside of View's lifecycle."
        }

    @Inject
    lateinit var viewModelFactory: FragmentViewModelFactory

    private val animationViewModel by viewModels<IntroAnimationViewModel> { viewModelFactory }

    private val cameraPermissionLauncher = registerForActivityResult(RequestPermission()) {
        animationViewModel.onCameraPermissionResult()
    }

    private var resetAnimationListener: OneShotPreDrawListener? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentSyncV2CameraScannerBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        configureIntroAnimation()
        configureReadyToScanButtons()

        observeUiEvents()
    }

    override fun onResume() {
        super.onResume()
        animationViewModel.refreshCameraPermissionState()
        animationViewModel.requestAnimationStart()
    }

    override fun onPause() {
        binding.includeIntro.introAnimation.pauseAnimation()
        // onPause can fire while the view is still on screen so rewinding immediately
        // would show a visible jump. By the next pre-draw the view is guaranteed
        // to be off-screen, so the rewind is never seen.
        resetAnimationListener?.removeListener()
        resetAnimationListener = binding.includeIntro.introAnimation.doOnPreDraw {
            resetAnimationListener = null
            val binding = _binding ?: return@doOnPreDraw
            if (!animationViewModel.viewState.value.animationFinished) {
                binding.includeIntro.introAnimation.progress = 0f
            }
        }
        super.onPause()
    }

    override fun onDestroyView() {
        resetAnimationListener = null
        _binding = null
        super.onDestroyView()
    }

    private fun observeUiEvents() {
        animationViewModel
            .viewState
            .flowWithLifecycle(viewLifecycleOwner.lifecycle)
            .onEach { renderIntroAnimationViewState(it) }
            .launchIn(viewLifecycleOwner.lifecycleScope)

        animationViewModel
            .commands
            .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.CREATED)
            .onEach { processIntroAnimationCommand(it) }
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    private fun renderIntroAnimationViewState(viewState: ViewState) {
        binding.includeIntro.root.isVisible = viewState.viewMode == ViewMode.Intro
        binding.noCameraPermissionContainer.isVisible = viewState.viewMode == ViewMode.NoCameraPermission
        binding.cameraContainer.isVisible = viewState.viewMode == ViewMode.Camera
        binding.noCameraAvailableContainer.isVisible = viewState.viewMode == ViewMode.NoCameraAvailable

        val isAnimationFinished = viewState.animationFinished
        if (isAnimationFinished) {
            binding.includeIntro.introAnimation.progress = 1f
        }
        binding.includeIntro.readyToScanSecondaryButton.isGone = isAnimationFinished
        binding.includeIntro.readyToScanPrimaryButton.isVisible = isAnimationFinished
    }

    private fun processIntroAnimationCommand(command: Command) {
        when (command) {
            PlayIntroAnimation -> {
                resetAnimationListener?.removeListener()
                resetAnimationListener = null
                binding.includeIntro.introAnimation.playAnimation()
            }

            RequestCameraPermission -> {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun configureIntroAnimation() {
        binding.includeIntro.introAnimation.addAnimatorListener(
            object : AnimatorListenerAdapter() {
                private var cancelled = false

                override fun onAnimationStart(animation: Animator) {
                    cancelled = false
                }

                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (!cancelled) {
                        animationViewModel.onAnimationFinished()
                    }
                }
            },
        )
    }

    private fun configureReadyToScanButtons() {
        val listener = View.OnClickListener {
            if (binding.includeIntro.introAnimation.isAnimating) {
                binding.includeIntro.introAnimation.cancelAnimation()
            }
            animationViewModel.onScanButtonClicked()
        }
        binding.includeIntro.readyToScanSecondaryButton.setOnClickListener(listener)
        binding.includeIntro.readyToScanPrimaryButton.setOnClickListener(listener)
    }
}
