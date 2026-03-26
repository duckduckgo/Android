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
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.drawable.AnimatedVectorDrawable
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.ViewGroupCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.core.view.postDelayed
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.transition.TransitionListenerAdapter
import androidx.transition.TransitionManager
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
import com.duckduckgo.app.onboarding.ui.page.PreOnboardingDialogType.SYNC_RESTORE
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.ui.store.AppTheme
import com.duckduckgo.common.ui.view.TypeAnimationTextView
import com.duckduckgo.common.ui.view.toPx
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
    private var backgroundIntroAnimatorSet: AnimatorSet? = null
    private var walkingDaxAnimatorSet: AnimatorSet? = null
    private var walkingDaxDelayedRunnable: Runnable? = null
    private var comparisonChartFadeInAnimatorSet: AnimatorSet? = null
    private var comparisonChartDetailAnimatorSet: AnimatorSet? = null
    private var backgroundAnimator: OnboardingBackgroundAnimator? = null
    private var textIntroScale = 1f
    private var isAnimating = false

    private val requestPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { permissionGranted ->
        if (permissionGranted) {
            viewModel.notificationRuntimePermissionGranted()
        }
        if (view?.windowVisibility == View.VISIBLE) {
            viewModel.loadDaxDialog()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireActivity().apply {
            enableEdgeToEdge()
        }
    }

    override fun onGetLayoutInflater(savedInstanceState: Bundle?): LayoutInflater {
        val inflater = super.onGetLayoutInflater(savedInstanceState)
        val themeRes = if (appTheme.isLightModeEnabled()) {
            CommonR.style.Theme_DuckDuckGo_Light_Onboarding
        } else {
            CommonR.style.Theme_DuckDuckGo_Dark_Onboarding
        }
        val contextThemeWrapper = ContextThemeWrapper(inflater.context, themeRes)
        return inflater.cloneInContext(contextThemeWrapper)
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
            startDelay = TEXT_INTRO_DELAY
            duration = TEXT_INTRO_TRANSLATE_DURATION
            interpolator = textSlideInterpolator
        }

        val textScaleY = ObjectAnimator.ofFloat(binding.welcomeTitle, View.SCALE_Y, textIntroScale, 1f).apply {
            startDelay = TEXT_INTRO_DELAY
            duration = TEXT_INTRO_TRANSLATE_DURATION
            interpolator = textSlideInterpolator
        }

        return AnimatorSet().apply {
            playTogether(alphaAnimator, guidelineAnimator, logoScaleX, logoScaleY, textScaleX, textScaleY)
        }
    }

    private fun buildOutroAnimatorSet(): AnimatorSet {
        val fadeEasing = PathInterpolator(0.33f, 0.00f, 0.67f, 1.00f)

        val logoFade = ObjectAnimator.ofFloat(binding.logoAnimation, View.ALPHA, 1f, 0f).apply {
            duration = OUTRO_FADE_DURATION
            interpolator = fadeEasing
        }

        val textFade = ObjectAnimator.ofFloat(binding.welcomeTitle, View.ALPHA, 1f, 0f).apply {
            duration = OUTRO_FADE_DURATION
            interpolator = fadeEasing
        }

        return AnimatorSet().apply {
            playTogether(logoFade, textFade)
        }
    }

    private fun buildBackgroundIntroAnimatorSet(): AnimatorSet {
        val slideDistance = resources.displayMetrics.heightPixels * BACKGROUND_SLIDE_UP_SCREEN_PERCENT
        val easing = PathInterpolator(0.33f, 0.00f, 0.67f, 1.00f)

        with(binding.backgroundPrimary) {
            translationY = slideDistance
            scaleX = BACKGROUND_INTRO_SCALE
            scaleY = BACKGROUND_INTRO_SCALE
        }

        val slideUp = ObjectAnimator.ofFloat(binding.backgroundPrimary, View.TRANSLATION_Y, slideDistance, 0f).apply {
            duration = BACKGROUND_SLIDE_UP_DURATION
            interpolator = easing
        }
        val scaleX = ObjectAnimator.ofFloat(binding.backgroundPrimary, View.SCALE_X, BACKGROUND_INTRO_SCALE, 1f).apply {
            duration = BACKGROUND_SLIDE_UP_DURATION
            interpolator = easing
        }
        val scaleY = ObjectAnimator.ofFloat(binding.backgroundPrimary, View.SCALE_Y, BACKGROUND_INTRO_SCALE, 1f).apply {
            duration = BACKGROUND_SLIDE_UP_DURATION
            interpolator = easing
        }

        return AnimatorSet().apply {
            playTogether(slideUp, scaleX, scaleY)
        }
    }

    private fun playIntroAnimation() {
        binding.backgroundPrimary.setMinFrame(BACKGROUND_MIN_FRAME)

        backgroundIntroAnimatorSet = buildBackgroundIntroAnimatorSet()

        binding.logoAnimation.apply {
            var bgStarted = false
            addAnimatorUpdateListener {
                // Start background animation once when logo reaches the "drop" frame
                if (!bgStarted && frame >= BACKGROUND_TRIGGER_LOGO_FRAME) {
                    bgStarted = true
                    binding.backgroundPrimary.playAnimation()
                    backgroundIntroAnimatorSet?.start()
                }
            }
            addAnimatorListener(object : AnimatorListenerAdapter() {
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

        with(binding.backgroundPrimary) {
            alpha = 1f
            translationY = 0f
            scaleX = 1f
            scaleY = 1f
            setMinFrame(BACKGROUND_MIN_FRAME)
            progress = 1f
        }
    }

    private fun playOutroAnimation(
        nextStep: OnboardingBackgroundStep,
        onAnimationStart: () -> Unit,
        onAnimationEnd: () -> Unit,
    ) {
        outroAnimatorSet = buildOutroAnimatorSet().apply { start() }

        val enterStartX = if (resources.configuration.smallestScreenWidthDp >= 600) {
            binding.daxDialogCta.root.right.toFloat()
        } else {
            null
        }

        backgroundAnimator?.transitionTo(
            step = nextStep,
            enterStartX = enterStartX,
            onAnimationStarted = onAnimationStart,
            onAnimationEnd = onAnimationEnd,
        )
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        ViewGroupCompat.installCompatInsetsDispatch(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.daxDialogCta.root) { v, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = insets.top
            }
            windowInsets
        }

        binding.logoAnimation.apply {
            enableMergePathsForKitKatAndAbove(true)
            setMaxFrame(60) // If we go past frame 60 the logo disappears
            repeatCount = 0
        }

        binding.backgroundPrimary.enableMergePathsForKitKatAndAbove(true)

        backgroundAnimator = OnboardingBackgroundAnimator(
            backgroundPrimary = binding.backgroundPrimary,
            backgroundSecondary = binding.backgroundSecondary,
        )

        viewModel.viewState
            .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
            .onEach { state ->
                when {
                    !state.hasPlayedIntroAnimation -> binding.root.doOnLayout { playIntroAnimation() }
                    state.hasPlayedIntroAnimation && state.currentDialog == null -> snapToIntroEndState()
                    isAnimating -> { /* animation in progress — ignore re-emissions from onDialogAnimationStarted() */ }
                    state.hasAnimatedCurrentDialog -> {
                        val dialog = state.currentDialog ?: return@onEach
                        showDialogWithoutAnimation(dialog, state.showSplitOption)
                    }
                    else -> {
                        val dialog = state.currentDialog ?: return@onEach
                        configureDaxCta(dialog, state.showSplitOption)
                    }
                }
                // TODO: react to state.selectedAddressBarPosition for address bar toggle UI
            }
            .launchIn(viewLifecycleOwner.lifecycleScope)

        viewModel.commands
            .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
            .onEach { command ->
                when (command) {
                    BrandDesignUpdatePageViewModel.Command.RequestNotificationPermissions -> requestNotificationsPermissions()
                    is BrandDesignUpdatePageViewModel.Command.ShowDefaultBrowserDialog -> showDefaultBrowserDialog(command.intent)
                    is BrandDesignUpdatePageViewModel.Command.Finish -> onContinuePressed()
                    is BrandDesignUpdatePageViewModel.Command.OnboardingSkipped -> onSkipPressed()
                }
            }
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    override fun onDestroyView() {
        super.onDestroyView()

        introAnimatorSet?.cancel()
        outroAnimatorSet?.cancel()
        backgroundIntroAnimatorSet?.cancel()
        walkingDaxAnimatorSet?.cancel()
        walkingDaxAnimatorSet = null
        walkingDaxDelayedRunnable?.let { binding.welcomeScreenWalkingDax.removeCallbacks(it) }
        walkingDaxDelayedRunnable = null
        comparisonChartFadeInAnimatorSet?.cancel()
        comparisonChartFadeInAnimatorSet = null
        comparisonChartDetailAnimatorSet?.cancel()
        comparisonChartDetailAnimatorSet = null
        binding.daxDialogCta.comparisonChartContent.comparisonChartTitle.cancelAnimation()
        backgroundAnimator?.cancel()
        backgroundAnimator = null
        isAnimating = false

        binding.daxDialogCta.daxCtaContainer.animate().cancel()
        binding.daxDialogCta.welcomeContent.titleText.cancelAnimation()

        binding.logoAnimation.apply {
            removeAllAnimatorListeners()
            removeAllUpdateListeners()
            cancelAnimation()
        }
        binding.backgroundPrimary.cancelAnimation()
        binding.welcomeScreenWalkingDax.cancelAnimation()
        binding.bottomWingAnimation?.cancelAnimation()
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
        if (appBuildConfig.sdkInt >= 33) {
            viewModel.notificationRuntimePermissionRequested()
            requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.loadDaxDialog()
        }
    }

    private fun configureDaxCta(
        onboardingDialogType: PreOnboardingDialogType,
        showSplitOption: Boolean = false,
    ) {
        context?.let {
            isAnimating = true
            viewModel.onDialogAnimationStarted()
            when (onboardingDialogType) {
                INITIAL, INITIAL_REINSTALL_USER -> {
                    val showSecondaryCta = onboardingDialogType == INITIAL_REINSTALL_USER
                    if (showSecondaryCta) {
                        // Pin the title at its current position before the secondaryCta
                        // visibility change. The CL is wrap_content in landscape, so
                        // GONE→INVISIBLE increases its height and shifts percentage
                        // guidelines. Detaching the title prevents the visible shift;
                        // the logo stays put because it's constrained above the title.
                        (binding.welcomeTitle.layoutParams as ConstraintLayout.LayoutParams).apply {
                            bottomToTop = ConstraintLayout.LayoutParams.UNSET
                            topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                            topMargin = binding.welcomeTitle.top
                        }
                    }
                    binding.daxDialogCta.secondaryCta.visibility = if (showSecondaryCta) View.INVISIBLE else View.GONE

                    val showWalkingDax = BrandDesignUpdateOnboardingLayoutHelper.hasSpaceForAnimation(
                        rootView = binding.root,
                        dialogView = binding.daxDialogCta.root,
                        decorationView = binding.welcomeScreenWalkingDax,
                    )
                    if (!showWalkingDax) {
                        binding.welcomeScreenWalkingDax.isVisible = false
                        (binding.daxDialogCta.root.layoutParams as ConstraintLayout.LayoutParams).apply {
                            verticalBias = 0f
                            bottomToTop = ConstraintLayout.LayoutParams.UNSET
                            bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                        }
                    }

                    playOutroAnimation(
                        nextStep = OnboardingBackgroundStep.Welcome,
                        onAnimationStart = {
                            if (showWalkingDax) playWalkingDaxAnimation()
                        },
                        onAnimationEnd = {
                            fadeInDialog {
                                binding.daxDialogCta.welcomeContent.titleText.startOnboardingTypingAnimation(
                                    getString(R.string.preOnboardingWelcomeDialogTitle),
                                ) {
                                    val animators = mutableListOf<Animator>(
                                        ObjectAnimator.ofFloat(binding.daxDialogCta.welcomeContent.bodyText, View.ALPHA, 1f)
                                            .setDuration(DIALOG_CONTENT_FADE_IN_DURATION),
                                        ObjectAnimator.ofFloat(binding.daxDialogCta.primaryCta, View.ALPHA, 1f)
                                            .setDuration(DIALOG_CONTENT_FADE_IN_DURATION),
                                    )
                                    if (showSecondaryCta) {
                                        binding.daxDialogCta.secondaryCta.isVisible = true
                                        animators += ObjectAnimator.ofFloat(binding.daxDialogCta.secondaryCta, View.ALPHA, 1f)
                                            .setDuration(DIALOG_CONTENT_FADE_IN_DURATION)
                                    }
                                    AnimatorSet().apply {
                                        playTogether(animators)
                                        addListener(object : AnimatorListenerAdapter() {
                                            override fun onAnimationEnd(animation: Animator) {
                                                isAnimating = false
                                                binding.daxDialogCta.primaryCta.setOnClickListener { viewModel.onPrimaryCtaClicked() }
                                                if (showSecondaryCta) {
                                                    binding.daxDialogCta.secondaryCta.setOnClickListener { viewModel.onSecondaryCtaClicked() }
                                                }
                                            }
                                        })
                                        start()
                                    }
                                }
                            }
                        },
                    )
                }

                SYNC_RESTORE -> {
                    // TODO - SyncRestore: add dialog UI
                }

                COMPARISON_CHART -> {
                    val transition = androidx.transition.ChangeBounds().apply {
                        duration = DIALOG_TRANSITION_DURATION
                    }
                    transition.addListener(object : TransitionListenerAdapter() {
                        override fun onTransitionEnd(transition: androidx.transition.Transition) {
                            binding.daxDialogCta.comparisonChartContent.comparisonChartTitle.startOnboardingTypingAnimation(
                                getString(R.string.preOnboardingDaxDialog2Title),
                            ) {
                                comparisonChartFadeInAnimatorSet = AnimatorSet().apply {
                                    playTogether(
                                        ObjectAnimator.ofFloat(
                                            binding.daxDialogCta.comparisonChartContent.comparisonTable,
                                            View.ALPHA,
                                            1f,
                                        ).setDuration(DIALOG_CONTENT_FADE_IN_DURATION),
                                        ObjectAnimator.ofFloat(binding.daxDialogCta.primaryCta, View.ALPHA, 1f)
                                            .setDuration(DIALOG_CONTENT_FADE_IN_DURATION),
                                    )
                                    addListener(object : AnimatorListenerAdapter() {
                                        override fun onAnimationEnd(animation: Animator) {
                                            playCheckIconAnimation()
                                        }
                                    })
                                    start()
                                }
                            }
                        }
                    })
                    binding.daxDialogCta.stepIndicator.setSteps(viewModel.getMaxPageCount(), 1)
                    binding.daxDialogCta.stepIndicator.isVisible = true
                    binding.daxDialogCta.stepIndicator.alpha = 0f
                    TransitionManager.beginDelayedTransition(binding.root as ViewGroup, transition)

                    val cardView = binding.daxDialogCta.cardView
                    cardView.setArrowAnimationTarget(ARROW_TARGET_OFFSET_END_DP.toPx().toFloat())
                    android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
                        duration = DIALOG_TRANSITION_DURATION
                        interpolator = androidx.interpolator.view.animation.FastOutSlowInInterpolator()
                        addUpdateListener {
                            cardView.setArrowAnimationFraction(it.animatedValue as Float)
                        }
                        start()
                    }

                    playBottomWingAnimation()

                    binding.welcomeScreenWalkingDax.isVisible = false
                    (binding.daxDialogCta.root.layoutParams as ConstraintLayout.LayoutParams).apply {
                        verticalBias = 0f
                        bottomToTop = ConstraintLayout.LayoutParams.UNSET
                        bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                    }

                    binding.daxDialogCta.welcomeContent.root.isVisible = false
                    binding.daxDialogCta.secondaryCta.isVisible = false

                    binding.daxDialogCta.comparisonChartContent.root.isVisible = true

                    binding.daxDialogCta.primaryCta.text = getString(R.string.preOnboardingDaxDialog2Button)
                    binding.daxDialogCta.primaryCta.setOnClickListener { viewModel.onPrimaryCtaClicked() }
                    binding.daxDialogCta.primaryCta.alpha = 0f
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

    private fun showDialogWithoutAnimation(
        onboardingDialogType: PreOnboardingDialogType,
        showSplitOption: Boolean = false,
    ) {
        snapToIntroEndState()

        when (onboardingDialogType) {
            INITIAL, INITIAL_REINSTALL_USER -> {
                binding.logoAnimation.alpha = 0f
                binding.welcomeTitle.alpha = 0f

                backgroundAnimator?.snapTo(OnboardingBackgroundStep.Welcome)

                val showWalkingDax = BrandDesignUpdateOnboardingLayoutHelper.hasSpaceForAnimation(
                    rootView = binding.root,
                    dialogView = binding.daxDialogCta.root,
                    decorationView = binding.welcomeScreenWalkingDax,
                )
                if (showWalkingDax) {
                    with(binding.welcomeScreenWalkingDax) {
                        cancelAnimation()
                        progress = 1f
                        alpha = 1f
                        translationX = -WALKING_DAX_FINAL_X_DP.toPx().toFloat()
                    }
                } else {
                    binding.welcomeScreenWalkingDax.isVisible = false
                    (binding.daxDialogCta.root.layoutParams as ConstraintLayout.LayoutParams).apply {
                        verticalBias = 0f
                        bottomToTop = ConstraintLayout.LayoutParams.UNSET
                        bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                    }
                }

                binding.daxDialogCta.root.isVisible = true
                binding.daxDialogCta.daxCtaContainer.alpha = 1f
                binding.daxDialogCta.welcomeContent.root.alpha = 1f
                binding.daxDialogCta.welcomeContent.titleText.cancelAnimation()
                binding.daxDialogCta.welcomeContent.titleText.text = getString(R.string.preOnboardingWelcomeDialogTitle)
                binding.daxDialogCta.welcomeContent.bodyText.alpha = 1f
                binding.daxDialogCta.primaryCta.alpha = 1f
                binding.daxDialogCta.primaryCta.setOnClickListener { viewModel.onPrimaryCtaClicked() }
                if (onboardingDialogType == INITIAL_REINSTALL_USER) {
                    binding.daxDialogCta.secondaryCta.isVisible = true
                    binding.daxDialogCta.secondaryCta.alpha = 1f
                    binding.daxDialogCta.secondaryCta.setOnClickListener { viewModel.onSecondaryCtaClicked() }
                }
            }

            SYNC_RESTORE -> {
                // TODO - SyncRestore: add dialog UI
            }

            COMPARISON_CHART -> {
                binding.logoAnimation.alpha = 0f
                binding.welcomeTitle.alpha = 0f

                backgroundAnimator?.snapTo(OnboardingBackgroundStep.Welcome)

                binding.welcomeScreenWalkingDax.isVisible = false
                (binding.daxDialogCta.root.layoutParams as ConstraintLayout.LayoutParams).apply {
                    verticalBias = 0f
                    bottomToTop = ConstraintLayout.LayoutParams.UNSET
                    bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                }
                val cardView = binding.daxDialogCta.cardView
                cardView.setArrowAnimationTarget(ARROW_TARGET_OFFSET_END_DP.toPx().toFloat())
                cardView.setArrowAnimationFraction(1f)
                binding.bottomWingAnimation?.apply {
                    isVisible = true
                    alpha = 1f
                    progress = WING_STOP_PROGRESS
                }
                binding.daxDialogCta.welcomeContent.root.isVisible = false
                binding.daxDialogCta.secondaryCta.isVisible = false

                binding.daxDialogCta.comparisonChartContent.root.isVisible = true
                binding.daxDialogCta.comparisonChartContent.comparisonChartTitle.cancelAnimation()
                binding.daxDialogCta.comparisonChartContent.comparisonChartTitle.text =
                    getString(R.string.preOnboardingDaxDialog2Title)
                binding.daxDialogCta.comparisonChartContent.comparisonTable.alpha = 1f
                listOf(
                    binding.daxDialogCta.comparisonChartContent.check1,
                    binding.daxDialogCta.comparisonChartContent.check2,
                    binding.daxDialogCta.comparisonChartContent.check3,
                    binding.daxDialogCta.comparisonChartContent.check4,
                    binding.daxDialogCta.comparisonChartContent.check5,
                ).forEach { checkView ->
                    checkView.alpha = 1f
                    checkView.scaleX = 1f
                    checkView.scaleY = 1f
                    checkView.setImageResource(CommonR.drawable.ic_check_green_24)
                }

                binding.daxDialogCta.stepIndicator.isVisible = true
                binding.daxDialogCta.stepIndicator.alpha = 1f
                binding.daxDialogCta.stepIndicator.setSteps(viewModel.getMaxPageCount(), 1)
                binding.daxDialogCta.primaryCta.alpha = 1f
                binding.daxDialogCta.primaryCta.text = getString(R.string.preOnboardingDaxDialog2Button)
                binding.daxDialogCta.primaryCta.setOnClickListener { viewModel.onPrimaryCtaClicked() }

                binding.daxDialogCta.root.isVisible = true
                binding.daxDialogCta.daxCtaContainer.alpha = 1f
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

    private fun playWalkingDaxAnimation() {
        walkingDaxDelayedRunnable = binding.welcomeScreenWalkingDax.postDelayed(WALKING_DAX_DELAY) {
            walkingDaxAnimatorSet = AnimatorSet().apply {
                interpolator = WELCOME_DAX_INTERPOLATOR
                playTogether(
                    ObjectAnimator.ofFloat(binding.welcomeScreenWalkingDax, View.ALPHA, 0f, 1f)
                        .setDuration(WALKING_DAX_FADE_DURATION),
                    ObjectAnimator.ofFloat(
                        binding.welcomeScreenWalkingDax,
                        View.TRANSLATION_X,
                        -WALKING_DAX_START_X_DP.toPx().toFloat(),
                        -WALKING_DAX_FINAL_X_DP.toPx().toFloat(),
                    ).setDuration(WALKING_DAX_SLIDE_DURATION),
                )
                start()
            }
            binding.welcomeScreenWalkingDax.playAnimation()
        }
    }

    private fun fadeInDialog(onAnimationEnd: () -> Unit) {
        binding.daxDialogCta.root.isVisible = true
        binding.daxDialogCta.daxCtaContainer.animate()
            .alpha(1f)
            .setDuration(DIALOG_FADE_IN_DURATION)
            .setStartDelay(200L)
            .withEndAction {
                onAnimationEnd()
            }
            .start()
    }

    private fun TypeAnimationTextView.startOnboardingTypingAnimation(
        text: String,
        afterAnimation: () -> Unit = {},
    ) {
        typingDelayInMs = TYPING_DELAY_MS
        delayAfterAnimationInMs = TYPING_POST_DELAY_MS
        startTypingAnimation(text, isCancellable = true, afterAnimation = afterAnimation)
    }

    private fun playCheckIconAnimation() {
        val overshoot = OvershootInterpolator(CHECK_ICON_OVERSHOOT_TENSION)
        val comparisonTable = binding.daxDialogCta.comparisonChartContent.comparisonTable
        val checkViews = listOf(
            binding.daxDialogCta.comparisonChartContent.check1,
            binding.daxDialogCta.comparisonChartContent.check2,
            binding.daxDialogCta.comparisonChartContent.check3,
            binding.daxDialogCta.comparisonChartContent.check4,
            binding.daxDialogCta.comparisonChartContent.check5,
        ).sortedBy { comparisonTable.indexOfChild(it.parent as View) }

        val iconAnimators = checkViews.mapIndexed { index, checkView ->
            AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(checkView, View.ALPHA, 0f, 1f).apply {
                        duration = CHECK_ICON_FADE_DURATION
                    },
                    ObjectAnimator.ofFloat(checkView, View.SCALE_X, 0f, 1f).apply {
                        duration = CHECK_ICON_ANIMATION_DURATION
                        interpolator = overshoot
                    },
                    ObjectAnimator.ofFloat(checkView, View.SCALE_Y, 0f, 1f).apply {
                        duration = CHECK_ICON_ANIMATION_DURATION
                        interpolator = overshoot
                    },
                )
                startDelay = index * CHECK_ICON_STAGGER_DELAY
            }
        }

        checkViews.forEachIndexed { index, checkView ->
            checkView.postDelayed(index * CHECK_ICON_STAGGER_DELAY + CHECK_ICON_AVD_START_DELAY) {
                (checkView.drawable as? AnimatedVectorDrawable)?.start()
            }
        }

        val stepIndicatorFadeIn = ObjectAnimator.ofFloat(binding.daxDialogCta.stepIndicator, View.ALPHA, 0f, 1f).apply {
            duration = DIALOG_CONTENT_FADE_IN_DURATION
        }

        comparisonChartDetailAnimatorSet = AnimatorSet().apply {
            playTogether(iconAnimators + stepIndicatorFadeIn)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    isAnimating = false
                }
            })
            start()
        }
    }

    private fun playBottomWingAnimation() {
        binding.bottomWingAnimation?.apply {
            isVisible = true
            alpha = 0f
            setMaxProgress(WING_STOP_PROGRESS)
            postDelayed(WING_START_DELAY) {
                animate()
                    .alpha(1f)
                    .setDuration(WING_FADE_IN_DURATION)
                    .start()
                playAnimation()
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
        private const val MAX_TEXT_INTRO_SCALE = 1.3f

        private const val LOGO_INTRO_SCALE = 2.5f
        private const val LOGO_SCALE_DURATION = 600L

        private const val BACKGROUND_MIN_FRAME = 27
        private const val BACKGROUND_TRIGGER_LOGO_FRAME = 6
        private const val BACKGROUND_SLIDE_UP_DURATION = 500L
        private const val BACKGROUND_SLIDE_UP_SCREEN_PERCENT = 0.15f
        private const val BACKGROUND_INTRO_SCALE = 2.5f

        private const val OUTRO_FADE_DURATION = 300L

        private const val DIALOG_FADE_IN_DURATION = 400L
        private const val DIALOG_CONTENT_FADE_IN_DURATION = 200L

        private const val TYPING_DELAY_MS = 20L
        private const val TYPING_POST_DELAY_MS = 20L

        private const val CHECK_ICON_ANIMATION_DURATION = 400L
        private const val CHECK_ICON_FADE_DURATION = 130L
        private const val CHECK_ICON_STAGGER_DELAY = 130L
        private const val CHECK_ICON_OVERSHOOT_TENSION = 2.4f
        private const val CHECK_ICON_AVD_START_DELAY = 180L

        private const val DIALOG_TRANSITION_DURATION = 400L
        private const val ARROW_TARGET_OFFSET_END_DP = 80

        private const val WING_START_DELAY = 300L
        private const val WING_FADE_IN_DURATION = 150L
        private const val WING_STOP_PROGRESS = 0.5f

        private const val WALKING_DAX_DELAY = 400L
        private const val WALKING_DAX_FADE_DURATION = 100L
        private const val WALKING_DAX_SLIDE_DURATION = 600L
        private const val WALKING_DAX_START_X_DP = 48
        private const val WALKING_DAX_FINAL_X_DP = 22

        private const val DEFAULT_BROWSER_ROLE_MANAGER_DIALOG = 101

        private val WELCOME_DAX_INTERPOLATOR = PathInterpolator(0.33f, 0f, 0.67f, 1f)
    }
}
