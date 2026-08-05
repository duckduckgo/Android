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
import android.animation.ValueAnimator
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.core.view.OneShotPreDrawListener
import androidx.core.view.doOnLayout
import androidx.core.view.doOnPreDraw
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoFragment
import com.duckduckgo.common.ui.view.toPx
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.FragmentViewModelFactory
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.sync.impl.SyncFeature
import com.duckduckgo.sync.impl.databinding.FragmentSyncV2ReadSyncCodeCameraBinding
import com.duckduckgo.sync.impl.ui.v2.ReadSyncCodeCameraIntroViewModel.Command
import com.duckduckgo.sync.impl.ui.v2.ReadSyncCodeCameraIntroViewModel.Command.ExpandScannerCutout
import com.duckduckgo.sync.impl.ui.v2.ReadSyncCodeCameraIntroViewModel.Command.PlayIntroAnimation
import com.duckduckgo.sync.impl.ui.v2.ReadSyncCodeCameraIntroViewModel.Command.RequestCameraPermission
import com.duckduckgo.sync.impl.ui.v2.ReadSyncCodeCameraIntroViewModel.Command.ResumeCamera
import com.duckduckgo.sync.impl.ui.v2.ReadSyncCodeCameraIntroViewModel.ViewMode
import com.duckduckgo.sync.impl.ui.v2.ReadSyncCodeCameraIntroViewModel.ViewState
import com.google.zxing.BarcodeFormat.QR_CODE
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@InjectWith(FragmentScope::class)
class ReadSyncCodeCameraFragment : DuckDuckGoFragment() {
    private var _binding: FragmentSyncV2ReadSyncCodeCameraBinding? = null
    private val binding
        get() = requireNotNull(_binding) {
            "Fragment $this tried to access ViewBinding outside of View's lifecycle."
        }

    @Inject
    lateinit var viewModelFactory: FragmentViewModelFactory

    @Inject
    lateinit var syncFeature: SyncFeature

    @Inject
    lateinit var dispatchers: DispatcherProvider

    private val animationViewModel by viewModels<ReadSyncCodeCameraIntroViewModel> { viewModelFactory }

    private val syncCodeViewModel by activityViewModels<ReadSyncCodeViewModel> { viewModelFactory }

    private val cameraPermissionLauncher = registerForActivityResult(
        RequestPermission(),
    ) {
        animationViewModel.onCameraPermissionResult()
    }

    private var resetAnimationListener: OneShotPreDrawListener? = null
    private var cutoutAnimator: ValueAnimator? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentSyncV2ReadSyncCodeCameraBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        configureIntroAnimation()
        configureReadyToScanButtons()
        configureGoToSettingsButton()
        configureScanner()
        configureCutout()

        observeUiEvents()
    }

    override fun onResume() {
        super.onResume()
        animationViewModel.refreshCameraPermissionState()
        animationViewModel.requestAnimationStart()
    }

    override fun onPause() {
        binding.includeCamera.barcodeView.pause()
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

        cutoutAnimator?.cancel()
        cutoutAnimator = null
        // Reset only on a pager page switch, which pauses just this fragment once its page is
        // already off-screen. Anything that pauses the whole activity (closing, minimizing,
        // system dialogs) keeps the view visible, where the resize would show as a jump.
        val isPageSwitch = requireActivity().lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        if (isPageSwitch) {
            binding.includeCamera.scannerOverlay.cutoutSizeFraction = CUTOUT_FRACTION_START
        }
        super.onPause()
    }

    override fun onDestroyView() {
        cutoutAnimator?.cancel()
        cutoutAnimator = null
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
        binding.includeNoPermission.root.isVisible = viewState.viewMode == ViewMode.NoCameraPermission
        binding.includeCamera.root.isVisible = viewState.viewMode == ViewMode.Camera
        binding.includeNoHardware.root.isVisible = viewState.viewMode == ViewMode.NoCameraAvailable

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

            ResumeCamera -> {
                if (isResumed) binding.includeCamera.barcodeView.resume()
            }

            ExpandScannerCutout -> {
                runCutoutAnimation()
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

    private fun configureGoToSettingsButton() {
        binding.includeNoPermission.goToPermissionsSettingsButton.setOnClickListener {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", requireContext().packageName, null),
            )
            startActivity(intent)
        }
    }

    private fun configureScanner() {
        viewLifecycleOwner.lifecycleScope.launch {
            val restrictToQr = withContext(dispatchers.io()) {
                syncFeature.restrictScannedBarcodesToQrTypes().isEnabled()
            }
            if (restrictToQr) {
                binding.includeCamera.barcodeView.decoderFactory = DefaultDecoderFactory(listOf(QR_CODE))
            }

            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                binding.includeCamera.barcodeView.decodeSingle { barcode ->
                    syncCodeViewModel.processScannedCode(barcode.text)
                }
                try {
                    awaitCancellation()
                } finally {
                    binding.includeCamera.barcodeView.stopDecoding()
                }
            }
        }
    }

    private fun configureCutout() {
        binding.includeCamera.scannerOverlay.cutoutSizeFraction = CUTOUT_FRACTION_START
    }

    private fun runCutoutAnimation() {
        if (cutoutAnimator?.isStarted == true) return

        binding.includeCamera.scannerHeader.doOnLayout {
            val overlay = _binding?.includeCamera?.scannerOverlay ?: return@doOnLayout
            val header = _binding?.includeCamera?.scannerHeader ?: return@doOnLayout
            if (cutoutAnimator?.isStarted == true) return@doOnLayout

            // On tall phone screens the expanded cutout never reaches the header. But on frames with
            // less free height (tablets, split screen, landscape) the square would collide with the
            // text, so its size is capped instead.
            val minDimension = minOf(overlay.width, overlay.height).toFloat()
            val clearance = header.bottom + CUTOUT_HEADER_GAP_DP.toPx()
            val maxSide = overlay.height - clearance / CameraScannerOverlayView.CUTOUT_TOP_SPACE_FRACTION
            val endFraction = CUTOUT_FRACTION_END
                .coerceAtMost(maxSide / minDimension)
                .coerceAtLeast(CUTOUT_MIN_SIDE_DP.toPx() / minDimension)
            val startFraction = CUTOUT_FRACTION_START.coerceAtMost(endFraction)

            if (overlay.cutoutSizeFraction == endFraction) return@doOnLayout
            if (startFraction == endFraction) {
                overlay.cutoutSizeFraction = endFraction
                return@doOnLayout
            }

            cutoutAnimator = ValueAnimator.ofFloat(startFraction, endFraction).apply {
                startDelay = CUTOUT_ANIMATION_START_DELAY_MS // Short delay to let the camera initialize
                duration = CUTOUT_ANIMATION_DURATION_MS
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener { animator ->
                    overlay.cutoutSizeFraction = animator.animatedValue as Float
                }
                start()
            }
        }
    }

    companion object {
        private const val CUTOUT_FRACTION_START = 0.53f
        private const val CUTOUT_FRACTION_END = 0.72f
        private const val CUTOUT_HEADER_GAP_DP = 16
        private const val CUTOUT_MIN_SIDE_DP = 140f

        private const val CUTOUT_ANIMATION_START_DELAY_MS = 100L
        private const val CUTOUT_ANIMATION_DURATION_MS = 450L
    }
}
