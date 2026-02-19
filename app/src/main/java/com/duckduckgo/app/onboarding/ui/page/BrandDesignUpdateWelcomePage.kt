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

package com.duckduckgo.app.onboarding.ui.page

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.animation.PathInterpolator
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ContentOnboardingWelcomePageUpdateBinding
import com.duckduckgo.app.browser.omnibar.OmnibarType
import com.duckduckgo.app.onboarding.ui.page.PreOnboardingDialogType.ADDRESS_BAR_POSITION
import com.duckduckgo.app.onboarding.ui.page.PreOnboardingDialogType.COMPARISON_CHART
import com.duckduckgo.app.onboarding.ui.page.PreOnboardingDialogType.INITIAL
import com.duckduckgo.app.onboarding.ui.page.PreOnboardingDialogType.INITIAL_REINSTALL_USER
import com.duckduckgo.app.onboarding.ui.page.PreOnboardingDialogType.INPUT_SCREEN
import com.duckduckgo.app.onboarding.ui.page.PreOnboardingDialogType.SKIP_ONBOARDING_OPTION
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.ui.store.AppTheme
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.FragmentViewModelFactory
import com.duckduckgo.di.scopes.FragmentScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import com.duckduckgo.mobile.android.R as CommonR

@InjectWith(FragmentScope::class)
class BrandDesignUpdateWelcomePage : OnboardingPageFragment(R.layout.content_onboarding_welcome_page_update) {

    @Inject
    lateinit var viewModelFactory: FragmentViewModelFactory

    @Inject
    lateinit var appBuildConfig: AppBuildConfig

    @Inject
    lateinit var appTheme: AppTheme

    private val binding: ContentOnboardingWelcomePageUpdateBinding by viewBinding()
    private val viewModel by lazy {
        ViewModelProvider(this, viewModelFactory)[BrandDesignUpdatePageViewModel::class.java]
    }

    private var introAnimatorSet: AnimatorSet? = null
    private var outroAnimatorSet: AnimatorSet? = null
    private var outroRunnable: Runnable? = null
    private var welcomeAnimationFinished = false
    private var backgroundIntroAnimatorSet: AnimatorSet? = null
    private var textIntroScale = 1f

    private val requestPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { permissionGranted ->
        if (permissionGranted) {
            viewModel.notificationRuntimePermissionGranted()
        }
        if (view?.windowVisibility == View.VISIBLE) {
            scheduleWelcomeAnimation(ANIMATION_DELAY_AFTER_NOTIFICATIONS_PERMISSIONS_HANDLED)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireActivity().apply {
            enableEdgeToEdge()
        }
    }

    private fun setAddressBarPositionOptions(selectedOption: OmnibarType) {
        context?.let { ctx ->
            // TODO
        }
    }

    private fun buildIntroAnimatorSet(): AnimatorSet {
        val layout = binding.welcomeTitle.layout
        val maxLineWidth = (0 until layout.lineCount).maxOf { layout.getLineWidth(it) }
        textIntroScale = (binding.welcomeTitle.width.toFloat() / maxLineWidth).coerceAtMost(MAX_TEXT_INTRO_SCALE)

        with(binding.logoAnimation) {
            scaleX = LOGO_INTRO_SCALE
            scaleY = LOGO_INTRO_SCALE
        }

        with(binding.welcomeTitle) {
            scaleX = textIntroScale
            scaleY = textIntroScale
        }

        val textFadeInterpolator = PathInterpolator(0.33f, 0.00f, 0.67f, 1.00f)
        val textSlideInterpolator = PathInterpolator(0.40f, 0.00f, 0.74f, 1.00f)
        val scaleInterpolator = PathInterpolator(0.33f, 0.00f, 0.67f, 1.00f)

        val alphaAnimator = ObjectAnimator.ofFloat(binding.welcomeTitle, View.ALPHA, 0f, 1f).apply {
            startDelay = TEXT_INTRO_DELAY
            duration = TEXT_INTRO_OPACITY_DURATION
            interpolator = textFadeInterpolator
        }

        val guidelineAnimator = ObjectAnimator.ofFloat(
            binding.textGuideline,
            "guidelinePercent",
            GUIDELINE_START_PERCENT,
            GUIDELINE_END_PERCENT,
        ).apply {
            startDelay = TEXT_INTRO_DELAY
            duration = TEXT_INTRO_TRANSLATE_DURATION
            interpolator = textSlideInterpolator
        }

        val logoScaleX = ObjectAnimator.ofFloat(binding.logoAnimation, View.SCALE_X, LOGO_INTRO_SCALE, 1f).apply {
            startDelay = TEXT_INTRO_DELAY
            duration = LOGO_SCALE_DURATION
            interpolator = scaleInterpolator
        }

        val logoScaleY = ObjectAnimator.ofFloat(binding.logoAnimation, View.SCALE_Y, LOGO_INTRO_SCALE, 1f).apply {
            startDelay = TEXT_INTRO_DELAY
            duration = LOGO_SCALE_DURATION
            interpolator = scaleInterpolator
        }

        val textScaleX = ObjectAnimator.ofFloat(binding.welcomeTitle, View.SCALE_X, textIntroScale, 1f).apply {
            startDelay = TEXT_INTRO_DELAY + TEXT_INTRO_SCALE_DELAY
            duration = TEXT_INTRO_TRANSLATE_DURATION
            interpolator = textSlideInterpolator
        }

        val textScaleY = ObjectAnimator.ofFloat(binding.welcomeTitle, View.SCALE_Y, textIntroScale, 1f).apply {
            startDelay = TEXT_INTRO_DELAY + TEXT_INTRO_SCALE_DELAY
            duration = TEXT_INTRO_TRANSLATE_DURATION
            interpolator = textSlideInterpolator
        }

        return AnimatorSet().apply {
            playTogether(alphaAnimator, guidelineAnimator, logoScaleX, logoScaleY, textScaleX, textScaleY)
        }
    }

    private fun buildOutroAnimatorSet(): AnimatorSet {
        val fadeEasing = PathInterpolator(0.33f, 0.00f, 0.67f, 1.00f)
        val bgSlideEasing = PathInterpolator(0.10f, 0.85f, 0.64f, 0.99f)

        val logoFade = ObjectAnimator.ofFloat(binding.logoAnimation, View.ALPHA, 1f, 0f).apply {
            duration = OUTRO_FADE_DURATION
            interpolator = fadeEasing
        }

        val textFade = ObjectAnimator.ofFloat(binding.welcomeTitle, View.ALPHA, 1f, 0f).apply {
            duration = OUTRO_FADE_DURATION
            interpolator = fadeEasing
        }

        val screenWidth = binding.root.width.toFloat()

        val bgFade = ObjectAnimator.ofFloat(binding.backgroundAnimation, View.ALPHA, 1f, 0f).apply {
            startDelay = OUTRO_BG_DELAY
            duration = OUTRO_BG_OPACITY_DURATION
            interpolator = fadeEasing
        }

        val bgTranslateX = ObjectAnimator.ofFloat(
            binding.backgroundAnimation,
            View.TRANSLATION_X,
            0f,
            -(screenWidth * OUTRO_BG_SLIDE_PERCENT),
        ).apply {
            startDelay = OUTRO_BG_DELAY
            duration = OUTRO_BG_TRANSLATE_DURATION
            interpolator = bgSlideEasing
        }

        return AnimatorSet().apply {
            playTogether(logoFade, textFade, bgFade, bgTranslateX)
        }
    }

    private fun buildBackgroundIntroAnimatorSet(): AnimatorSet {
        val slideDistance = resources.displayMetrics.heightPixels * BACKGROUND_SLIDE_UP_SCREEN_PERCENT
        val easing = PathInterpolator(0.33f, 0.00f, 0.67f, 1.00f)

        with(binding.backgroundAnimation) {
            translationY = slideDistance
            scaleX = BACKGROUND_INTRO_SCALE
            scaleY = BACKGROUND_INTRO_SCALE
        }

        val slideUp = ObjectAnimator.ofFloat(binding.backgroundAnimation, View.TRANSLATION_Y, slideDistance, 0f).apply {
            duration = BACKGROUND_SLIDE_UP_DURATION
            interpolator = easing
        }
        val scaleX = ObjectAnimator.ofFloat(binding.backgroundAnimation, View.SCALE_X, BACKGROUND_INTRO_SCALE, 1f).apply {
            duration = BACKGROUND_SLIDE_UP_DURATION
            interpolator = easing
        }
        val scaleY = ObjectAnimator.ofFloat(binding.backgroundAnimation, View.SCALE_Y, BACKGROUND_INTRO_SCALE, 1f).apply {
            duration = BACKGROUND_SLIDE_UP_DURATION
            interpolator = easing
        }

        return AnimatorSet().apply {
            playTogether(slideUp, scaleX, scaleY)
        }
    }

    private fun playIntroAnimation() {
        binding.backgroundAnimation.setMinFrame(BACKGROUND_MIN_FRAME)

        backgroundIntroAnimatorSet = buildBackgroundIntroAnimatorSet()

        binding.logoAnimation.apply {
            var bgStarted = false
            addAnimatorUpdateListener {
                // Start background animation once when logo reaches the "drop" frame
                if (!bgStarted && frame >= BACKGROUND_TRIGGER_LOGO_FRAME) {
                    bgStarted = true
                    binding.backgroundAnimation.playAnimation()
                    backgroundIntroAnimatorSet?.start()
                }
            }
            addAnimatorListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    viewModel.onIntroAnimationFinished()
                }
            })
            playAnimation()
        }
        introAnimatorSet = buildIntroAnimatorSet().apply {
            start()
        }
    }

    private fun snapToIntroEndState() {
        introAnimatorSet?.cancel()
        backgroundIntroAnimatorSet?.cancel()

        with(binding.welcomeTitle) {
            alpha = 1f
            scaleX = 1f
            scaleY = 1f
        }
        binding.textGuideline.setGuidelinePercent(GUIDELINE_END_PERCENT)

        with(binding.logoAnimation) {
            alpha = 1f
            scaleX = 1f
            scaleY = 1f
            setMinFrame(BACKGROUND_MIN_FRAME)
            progress = 1f
        }

        with(binding.backgroundAnimation) {
            alpha = 1f
            translationY = 0f
            scaleX = 1f
            scaleY = 1f
            setMinFrame(BACKGROUND_MIN_FRAME)
            progress = 1f
        }
    }

    // TODO play outro and open welcome dialog
    private fun playOutroAnimation() {
        outroAnimatorSet = buildOutroAnimatorSet().apply {
            doOnEnd {
                viewModel.loadDaxDialog()
            }
            start()
        }
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        binding.logoAnimation.apply {
            enableMergePathsForKitKatAndAbove(true)
            setMaxFrame(60) // If we go past frame 60 the logo disappears
            repeatCount = 0
        }

        binding.backgroundAnimation.enableMergePathsForKitKatAndAbove(true)

        viewModel.viewState
            .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
            .onEach { state ->
                state.currentDialog?.let { dialogType ->
                    configureDaxCta(dialogType, state.showSplitOption)
                }
                // TODO: react to state.selectedAddressBarPosition for address bar toggle UI
            }
            .launchIn(viewLifecycleOwner.lifecycleScope)

        viewModel.commands
            .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
            .onEach { command ->
                when (command) {
                    is BrandDesignUpdatePageViewModel.Command.ShowDefaultBrowserDialog -> showDefaultBrowserDialog(command.intent)
                    is BrandDesignUpdatePageViewModel.Command.Finish -> onContinuePressed()
                    is BrandDesignUpdatePageViewModel.Command.OnboardingSkipped -> onSkipPressed()
                }
            }
            .launchIn(viewLifecycleOwner.lifecycleScope)

        playIntroAnimation()
        requestNotificationsPermissions()
    }

    override fun onDestroyView() {
        super.onDestroyView()

        introAnimatorSet?.cancel()
        outroAnimatorSet?.cancel()
        backgroundIntroAnimatorSet?.cancel()

        binding.logoAnimation.apply {
            removeAllAnimatorListeners()
            removeAllUpdateListeners()
            cancelAnimation()
        }
        binding.backgroundAnimation.cancelAnimation()
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ) {
        if (requestCode == DEFAULT_BROWSER_ROLE_MANAGER_DIALOG) {
            if (resultCode == Activity.RESULT_OK) {
                viewModel.onDefaultBrowserSet()
            } else {
                viewModel.onDefaultBrowserNotSet()
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    @SuppressLint("InlinedApi")
    private fun requestNotificationsPermissions() {
        if (appBuildConfig.sdkInt >= Build.VERSION_CODES.TIRAMISU) {
            viewModel.notificationRuntimePermissionRequested()
            requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            scheduleWelcomeAnimation()
        }
    }

    private fun configureDaxCta(
        onboardingDialogType: PreOnboardingDialogType,
        showSplitOption: Boolean = false,
    ) {
        context?.let {
            // var afterAnimation: () -> Unit = {}
            when (onboardingDialogType) {
                INITIAL_REINSTALL_USER -> {
                    // TODO
                }

                INITIAL -> {
                    // TODO
                }

                COMPARISON_CHART -> {
                    // TODO
                }

                SKIP_ONBOARDING_OPTION -> {
                    // TODO
                }

                ADDRESS_BAR_POSITION -> {
                    // TODO
                }

                INPUT_SCREEN -> {
                    // TODO
                }
            }
        }
    }

    private fun showDefaultBrowserDialog(intent: Intent) {
        startActivityForResult(intent, DEFAULT_BROWSER_ROLE_MANAGER_DIALOG)
    }

    private fun updateAiChatToggleState(
        binding: ContentOnboardingWelcomePageUpdateBinding,
        isLightMode: Boolean,
        withAi: Boolean,
    ) {
        // TODO
    }

    private sealed class OmnibarTypeToggleButton(
        isActive: Boolean,
    ) {
        abstract val imageRes: Int

        val checkRes: Int =
            if (isActive) {
                CommonR.drawable.ic_check_accent_24
            } else {
                CommonR.drawable.ic_shape_circle_disabled_24
            }

        class Top(
            isActive: Boolean,
            isLightMode: Boolean,
        ) : OmnibarTypeToggleButton(isActive) {
            override val imageRes: Int =
                when {
                    isActive && isLightMode -> R.drawable.mobile_toolbar_top_selected_light
                    isActive && !isLightMode -> R.drawable.mobile_toolbar_top_selected_dark
                    !isActive && isLightMode -> R.drawable.mobile_toolbar_top_unselected_light
                    else -> R.drawable.mobile_toolbar_top_unselected_dark
                }
        }

        class Bottom(
            isActive: Boolean,
            isLightMode: Boolean,
        ) : OmnibarTypeToggleButton(isActive) {
            override val imageRes: Int =
                when {
                    isActive && isLightMode -> R.drawable.mobile_toolbar_bottom_selected_light
                    isActive && !isLightMode -> R.drawable.mobile_toolbar_bottom_selected_dark
                    !isActive && isLightMode -> R.drawable.mobile_toolbar_bottom_unselected_light
                    else -> R.drawable.mobile_toolbar_bottom_unselected_dark
                }
        }

        class Split(
            isActive: Boolean,
            isLightMode: Boolean,
        ) : OmnibarTypeToggleButton(isActive) {
            override val imageRes: Int =
                when {
                    isActive && isLightMode -> R.drawable.mobile_toolbar_split_selected_light
                    isActive && !isLightMode -> R.drawable.mobile_toolbar_split_selected_dark
                    !isActive && isLightMode -> R.drawable.mobile_toolbar_split_unselected_light
                    else -> R.drawable.mobile_toolbar_split_unselected_dark
                }
        }
    }

    companion object {
        private const val GUIDELINE_START_PERCENT = 0.5f
        private const val GUIDELINE_END_PERCENT = 0.39125f

        private const val TEXT_INTRO_DELAY = 400L
        private const val TEXT_INTRO_OPACITY_DURATION = 400L
        private const val TEXT_INTRO_TRANSLATE_DURATION = 600L
        private const val TEXT_INTRO_SCALE_DELAY = 200L
        private const val MAX_TEXT_INTRO_SCALE = 1.5f

        private const val LOGO_INTRO_SCALE = 2.5f
        private const val LOGO_SCALE_DURATION = 600L

        private const val BACKGROUND_MIN_FRAME = 27
        private const val BACKGROUND_TRIGGER_LOGO_FRAME = 6
        private const val BACKGROUND_SLIDE_UP_DURATION = 500L
        private const val BACKGROUND_SLIDE_UP_SCREEN_PERCENT = 0.15f
        private const val BACKGROUND_INTRO_SCALE = 2.5f

        private const val OUTRO_FADE_DURATION = 300L
        private const val OUTRO_BG_DELAY = 300L
        private const val OUTRO_BG_OPACITY_DURATION = 400L
        private const val OUTRO_BG_TRANSLATE_DURATION = 700L
        private const val OUTRO_BG_SLIDE_PERCENT = 0.25f

        private const val DEFAULT_BROWSER_ROLE_MANAGER_DIALOG = 101
    }
}
