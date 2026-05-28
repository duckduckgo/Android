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
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.drawable.AnimatedVectorDrawable
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.ViewGroupCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.core.view.doOnLayout
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.view.postDelayed
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.transition.ChangeBounds
import androidx.transition.TransitionListenerAdapter
import androidx.transition.TransitionManager
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ContentOnboardingWelcomePageUpdateBinding
import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserSystemSettings
import com.duckduckgo.app.browser.omnibar.OmnibarType
import com.duckduckgo.app.cta.ui.DaxBubbleCta.DaxDialogIntroOption
import com.duckduckgo.app.onboarding.ui.OnboardingActivity
import com.duckduckgo.app.onboarding.ui.page.PreOnboardingDialogType.ADDRESS_BAR_POSITION
import com.duckduckgo.app.onboarding.ui.page.PreOnboardingDialogType.AI_COMPARISON_CHART
import com.duckduckgo.app.onboarding.ui.page.PreOnboardingDialogType.COMPARISON_CHART
import com.duckduckgo.app.onboarding.ui.page.PreOnboardingDialogType.INITIAL
import com.duckduckgo.app.onboarding.ui.page.PreOnboardingDialogType.INITIAL_REINSTALL_USER
import com.duckduckgo.app.onboarding.ui.page.PreOnboardingDialogType.INPUT_SCREEN
import com.duckduckgo.app.onboarding.ui.page.PreOnboardingDialogType.INPUT_SCREEN_PREVIEW
import com.duckduckgo.app.onboarding.ui.page.PreOnboardingDialogType.QUICK_SETUP
import com.duckduckgo.app.onboarding.ui.page.PreOnboardingDialogType.SKIP_ONBOARDING_OPTION
import com.duckduckgo.app.onboarding.ui.page.PreOnboardingDialogType.SYNC_RESTORE
import com.duckduckgo.app.onboardingquicksetup.ui.BrandDesignInputScreenPicker
import com.duckduckgo.app.onboardingquicksetup.ui.QuickSetupAddressBarPositionBottomSheet
import com.duckduckgo.app.onboardingquicksetup.ui.QuickSetupSearchOptionsBottomSheet
import com.duckduckgo.app.onboardingquicksetup.ui.RemoveWidgetInstructionsBottomSheet
import com.duckduckgo.app.widget.AddWidgetLauncher
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.ui.store.AppTheme
import com.duckduckgo.common.ui.view.TypeAnimationTextView
import com.duckduckgo.common.ui.view.addBottomShadow
import com.duckduckgo.common.ui.view.text.DaxTextView
import com.duckduckgo.common.ui.view.toPx
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.FragmentViewModelFactory
import com.duckduckgo.common.utils.device.DeviceInfo
import com.duckduckgo.common.utils.device.isTablet
import com.duckduckgo.common.utils.extensions.html
import com.duckduckgo.common.utils.extensions.preventWidows
import com.duckduckgo.common.utils.extensions.showKeyboard
import com.duckduckgo.di.scopes.FragmentScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import logcat.LogPriority.WARN
import logcat.asLog
import logcat.logcat
import javax.inject.Inject
import com.duckduckgo.mobile.android.R as CommonR

@InjectWith(FragmentScope::class)
class BrandDesignUpdateWelcomePage : OnboardingPageFragment(R.layout.content_onboarding_welcome_page_update) {

    @Inject
    lateinit var viewModelFactory: FragmentViewModelFactory

    @Inject
    lateinit var appBuildConfig: AppBuildConfig

    @Inject
    lateinit var deviceInfo: DeviceInfo

    @Inject
    lateinit var appTheme: AppTheme

    @Inject
    lateinit var addWidgetLauncher: AddWidgetLauncher

    private val binding: ContentOnboardingWelcomePageUpdateBinding by viewBinding()
    private val viewModel by lazy {
        ViewModelProvider(this, viewModelFactory)[BrandDesignUpdatePageViewModel::class.java]
    }

    private var introAnimatorSet: AnimatorSet? = null
    private var outroAnimatorSet: AnimatorSet? = null
    private var backgroundIntroAnimatorSet: AnimatorSet? = null
    private var walkingDaxAnimatorSet: AnimatorSet? = null
    private var walkingDaxDelayedRunnable: Runnable? = null
    private var bottomWingDelayedRunnable: Runnable? = null
    private var leftWingDelayedRunnable: Runnable? = null
    private var welcomeFadeInAnimatorSet: AnimatorSet? = null
    private var comparisonChartFadeInAnimatorSet: AnimatorSet? = null
    private var comparisonChartDetailAnimatorSet: AnimatorSet? = null
    private var skipOnboardingFadeOutAnimatorSet: AnimatorSet? = null
    private var skipOnboardingFadeInAnimatorSet: AnimatorSet? = null
    private var arrowSlideAnimator: android.animation.ValueAnimator? = null
    private var addressBarFadeInAnimatorSet: AnimatorSet? = null
    private var inputScreenFadeInAnimatorSet: AnimatorSet? = null
    private var inputScreenPreviewFadeInAnimatorSet: AnimatorSet? = null
    private var quickSetupFadeInAnimatorSet: AnimatorSet? = null
    private var quickSetupSelectionJob: Job? = null
    private var stepIndicatorFadeOutAnimator: ObjectAnimator? = null
    private var suggestionButtonsAnimatorSet: AnimatorSet? = null
    private var bobbingDaxAnimator: ValueAnimator? = null
    private var backgroundAnimator: OnboardingBackgroundAnimator? = null
    private var changeBoundsTransition: androidx.transition.Transition? = null
    private var changeBoundsTransitionListener: TransitionListenerAdapter? = null
    private var textIntroScale = 1f
    private var currentInputMode = InputMode.SEARCH
    private var isAnimating = false
        set(value) {
            field = value
            if (view != null) {
                binding.daxDialogCta.cardContainer.interceptChildTouches = value
            }
        }

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

    private fun updateAddressBarPositionOptions(selectedOption: OmnibarType, showSplitOption: Boolean = false, animate: Boolean = true) {
        with(binding.daxDialogCta.addressBarContent.addressBarPicker) {
            setLightMode(appTheme.isLightModeEnabled())
            isSplitOptionVisible = showSplitOption
            setSelection(selectedOption, animate = animate)
            setOnSelectionChangedListener { viewModel.onAddressBarPositionOptionSelected(it) }
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

        val enterStartX = if (deviceInfo.isTablet()) {
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
        if (deviceInfo.isTablet()) {
            ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, windowInsets ->
                val tappableInsets = windowInsets.getInsets(WindowInsetsCompat.Type.tappableElement())
                v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, tappableInsets.bottom)
                windowInsets
            }
        }
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

        binding.daxDialogCta.cardContainer.setOnClickListener { viewModel.onDialogTapped() }
        binding.root.setOnClickListener { viewModel.onBackgroundTapped() }

        viewModel.viewState
            .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
            .onEach { state ->
                when {
                    !state.hasPlayedIntroAnimation -> binding.root.doOnLayout { playIntroAnimation() }
                    state.hasPlayedIntroAnimation && state.currentDialog == null -> snapToIntroEndState()
                    isAnimating -> { /* animation in progress — ignore re-emissions from onDialogAnimationStarted() */ }
                    state.hasAnimatedCurrentDialog -> {
                        val dialog = state.currentDialog ?: return@onEach
                        binding.root.doOnLayout {
                            showDialogWithoutAnimation(
                                onboardingDialogType = dialog,
                                selectedAddressBarPosition = state.selectedAddressBarPosition,
                                showSplitOption = state.showSplitOption,
                                inputScreenSelected = state.inputScreenSelected,
                                maxPageCount = state.maxPageCount,
                                comparisonChartConfig = state.currentComparisonChartConfig(),
                            )
                        }
                    }
                    else -> {
                        val dialog = state.currentDialog ?: return@onEach
                        configureDaxCta(
                            onboardingDialogType = dialog,
                            selectedAddressBarPosition = state.selectedAddressBarPosition,
                            showSplitOption = state.showSplitOption,
                            inputScreenSelected = state.inputScreenSelected,
                            maxPageCount = state.maxPageCount,
                            comparisonChartConfig = state.currentComparisonChartConfig(),
                        )
                    }
                }
            }
            .launchIn(viewLifecycleOwner.lifecycleScope)

        viewModel.commands
            .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
            .onEach { command ->
                when (command) {
                    BrandDesignUpdatePageViewModel.Command.RequestNotificationPermissions -> requestNotificationsPermissions()
                    is BrandDesignUpdatePageViewModel.Command.ShowDefaultBrowserDialog -> showDefaultBrowserDialog(command.intent)
                    is BrandDesignUpdatePageViewModel.Command.Finish -> onContinuePressed()
                    is BrandDesignUpdatePageViewModel.Command.FinishAndSubmitSearchQuery -> {
                        (activity as? OnboardingActivity)?.finishAndSubmitSearchQuery(command.query)
                    }
                    is BrandDesignUpdatePageViewModel.Command.FinishAndSubmitChatPrompt -> {
                        (activity as? OnboardingActivity)?.finishAndSubmitChatPrompt(command.prompt)
                    }
                    is BrandDesignUpdatePageViewModel.Command.OnboardingSkipped -> onSkipPressed()
                    BrandDesignUpdatePageViewModel.Command.SkipDialogAnimation -> skipCurrentDialogAnimation()
                    is BrandDesignUpdatePageViewModel.Command.ShowQuickSetupAddressBarPositionBottomSheet -> {
                        showQuickSetupAddressBarPositionBottomSheet(
                            initialSelection = command.initialSelection,
                            showSplitOption = command.showSplitOption,
                        )
                    }
                    is BrandDesignUpdatePageViewModel.Command.ShowQuickSetupSearchOptionsBottomSheet -> {
                        showQuickSetupSearchOptionsBottomSheet(initialWithAi = command.initialWithAi)
                    }
                    is BrandDesignUpdatePageViewModel.Command.ShowQuickSetupDefaultBrowserDialog -> {
                        showQuickSetupDefaultBrowserDialog(command.intent)
                    }
                    BrandDesignUpdatePageViewModel.Command.OpenDefaultBrowserSystemSettings -> {
                        openDefaultBrowserSystemSettings()
                    }
                    BrandDesignUpdatePageViewModel.Command.LaunchAddWidgetPrompt -> {
                        addWidgetLauncher.launchAddWidget(activity)
                    }
                    BrandDesignUpdatePageViewModel.Command.ShowRemoveWidgetBottomSheet -> {
                        showRemoveWidgetInstructionsBottomSheet()
                    }
                    is BrandDesignUpdatePageViewModel.Command.SyncAddWidgetSwitch -> {
                        binding.daxDialogCta.reinstallerQuickSetupContent.addWidgetItem
                            .setCheckedSilently(command.isChecked)
                    }
                    is BrandDesignUpdatePageViewModel.Command.SyncQuickSetupSwitches -> {
                        with(binding.daxDialogCta.reinstallerQuickSetupContent) {
                            setDefaultBrowserItem.setCheckedSilently(command.defaultBrowserChecked)
                            addWidgetItem.setCheckedSilently(command.widgetChecked)
                        }
                    }
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
        bottomWingDelayedRunnable?.let { binding.bottomWingAnimation.removeCallbacks(it) }
        bottomWingDelayedRunnable = null
        leftWingDelayedRunnable?.let { binding.leftWingAnimation?.removeCallbacks(it) }
        leftWingDelayedRunnable = null
        welcomeFadeInAnimatorSet?.cancel()
        welcomeFadeInAnimatorSet = null
        comparisonChartFadeInAnimatorSet?.cancel()
        comparisonChartFadeInAnimatorSet = null
        comparisonChartDetailAnimatorSet?.cancel()
        comparisonChartDetailAnimatorSet = null
        skipOnboardingFadeOutAnimatorSet?.cancel()
        skipOnboardingFadeOutAnimatorSet = null
        skipOnboardingFadeInAnimatorSet?.cancel()
        skipOnboardingFadeInAnimatorSet = null
        arrowSlideAnimator?.cancel()
        arrowSlideAnimator = null
        changeBoundsTransitionListener?.let { listener ->
            changeBoundsTransition?.removeListener(listener)
        }
        changeBoundsTransitionListener = null
        changeBoundsTransition = null
        binding.daxDialogCta.comparisonChartContent.comparisonChartTitle.cancelAnimation()
        binding.daxDialogCta.addressBarContent.addressBarTitle.cancelAnimation()
        binding.daxDialogCta.inputScreenContent.inputScreenTitle.cancelAnimation()
        binding.daxDialogCta.reinstallerQuickSetupContent.quickSetupTitle.cancelAnimation()
        addressBarFadeInAnimatorSet?.cancel()
        addressBarFadeInAnimatorSet = null
        inputScreenFadeInAnimatorSet?.removeAllListeners()
        inputScreenFadeInAnimatorSet?.cancel()
        inputScreenFadeInAnimatorSet = null
        inputScreenPreviewFadeInAnimatorSet?.removeAllListeners()
        inputScreenPreviewFadeInAnimatorSet?.cancel()
        inputScreenPreviewFadeInAnimatorSet = null
        quickSetupFadeInAnimatorSet?.removeAllListeners()
        quickSetupFadeInAnimatorSet?.cancel()
        quickSetupFadeInAnimatorSet = null
        quickSetupSelectionJob?.cancel()
        quickSetupSelectionJob = null
        stepIndicatorFadeOutAnimator?.removeAllListeners()
        stepIndicatorFadeOutAnimator?.cancel()
        stepIndicatorFadeOutAnimator = null
        suggestionButtonsAnimatorSet?.cancel()
        suggestionButtonsAnimatorSet = null
        binding.daxDialogCta.inputScreenPreviewContent.inputScreenPreviewTitle.cancelAnimation()
        binding.daxDialogCta.inputScreenContent.inputScreenPicker.cancelLottieAnimations()
        bobbingDaxAnimator?.cancel()
        bobbingDaxAnimator = null
        binding.bobbingDaxAnimation.cancelAnimation()
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
        binding.bottomWingAnimation.cancelAnimation()
        binding.leftWingAnimation.cancelAnimation()
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkQuickSetupSwitchesState()
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ) {
        when (requestCode) {
            DEFAULT_BROWSER_ROLE_MANAGER_DIALOG -> {
                if (resultCode == Activity.RESULT_OK) {
                    viewModel.onDefaultBrowserSet()
                } else {
                    viewModel.onDefaultBrowserNotSet()
                }
            }
            QUICK_SETUP_DEFAULT_BROWSER_ROLE_MANAGER_DIALOG -> {
                if (resultCode == Activity.RESULT_OK) {
                    viewModel.onQuickSetupDefaultBrowserSet()
                } else {
                    viewModel.onQuickSetupDefaultBrowserNotSet()
                    binding.daxDialogCta.reinstallerQuickSetupContent.setDefaultBrowserItem.setCheckedSilently(false)
                }
            }
            else -> super.onActivityResult(requestCode, resultCode, data)
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
        selectedAddressBarPosition: OmnibarType,
        showSplitOption: Boolean,
        inputScreenSelected: Boolean,
        maxPageCount: Int,
        comparisonChartConfig: ComparisonChartConfig,
    ) {
        context?.let {
            isAnimating = true
            viewModel.onDialogAnimationStarted()
            when (onboardingDialogType) {
                INITIAL, INITIAL_REINSTALL_USER, SYNC_RESTORE -> {
                    val isSyncRestore = onboardingDialogType == SYNC_RESTORE
                    val showSecondaryCta = onboardingDialogType == INITIAL_REINSTALL_USER || isSyncRestore
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

                    val titleRes = if (isSyncRestore) R.string.syncRestoreDialogBrandDesignTitle else R.string.preOnboardingWelcomeDialogTitle
                    if (isSyncRestore) {
                        // SYNC_RESTORE reuses welcomeContent with its own copy and the sync-restore CTAs.
                        binding.daxDialogCta.welcomeContent.hiddenTitleText.text = getString(titleRes)
                        binding.daxDialogCta.welcomeContent.bodyText1.text =
                            getString(R.string.syncRestoreDialogBrandDesignBody1).preventWidows().html(requireContext())
                        binding.daxDialogCta.primaryCta.text = getString(R.string.syncRestoreDialogPrimaryCta)
                        binding.daxDialogCta.secondaryCta.text = getString(R.string.syncRestoreDialogSecondaryCta)
                    }
                    // SYNC_RESTORE shows no second body line; INITIAL/INITIAL_REINSTALL_USER do.
                    // Set isVisible explicitly so a prior dialog that hid bodyText2 doesn't leak into this one.
                    binding.daxDialogCta.welcomeContent.bodyText2.isVisible = !isSyncRestore

                    val showWalkingDax = applyWalkingDaxLayout()
                    binding.daxDialogCta.cardView.setArrowDepthFraction(if (showWalkingDax) 1f else 0f)

                    playOutroAnimation(
                        nextStep = OnboardingBackgroundStep.Welcome,
                        onAnimationStart = {
                            if (showWalkingDax) playWalkingDaxAnimation()
                        },
                        onAnimationEnd = {
                            fadeInDialog {
                                binding.daxDialogCta.welcomeContent.titleText.startOnboardingTypingAnimation(
                                    getString(titleRes),
                                ) {
                                    val animators = mutableListOf<Animator>(
                                        ObjectAnimator.ofFloat(binding.daxDialogCta.welcomeContent.bodyText1, View.ALPHA, 1f)
                                            .setDuration(DIALOG_CONTENT_FADE_IN_DURATION),
                                        ObjectAnimator.ofFloat(binding.daxDialogCta.primaryCta, View.ALPHA, 1f)
                                            .setDuration(DIALOG_CONTENT_FADE_IN_DURATION),
                                    )
                                    if (!isSyncRestore) {
                                        animators += ObjectAnimator.ofFloat(binding.daxDialogCta.welcomeContent.bodyText2, View.ALPHA, 1f)
                                            .setDuration(DIALOG_CONTENT_FADE_IN_DURATION)
                                    }
                                    if (showSecondaryCta) {
                                        binding.daxDialogCta.secondaryCta.isVisible = true
                                        animators += ObjectAnimator.ofFloat(binding.daxDialogCta.secondaryCta, View.ALPHA, 1f)
                                            .setDuration(DIALOG_CONTENT_FADE_IN_DURATION)
                                    }
                                    welcomeFadeInAnimatorSet = AnimatorSet().apply {
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

                QUICK_SETUP -> {
                    binding.welcomeScreenWalkingDax.isVisible = false
                    backgroundAnimator?.transitionTo(step = OnboardingBackgroundStep.QuickSetup)

                    // Swap content before measuring so the next layout pass reflects the quick-setup size,
                    // and ChangeBounds animates the card expanding into it. The include root stays fully
                    // opaque so the title is visible while it types; only the options container and primary
                    // CTA are alpha-hidden until typing completes.
                    binding.daxDialogCta.welcomeContent.root.isVisible = false
                    binding.daxDialogCta.secondaryCta.isVisible = false

                    binding.daxDialogCta.reinstallerQuickSetupContent.root.isVisible = true
                    updateQuickSetupRowsVisibility()
                    binding.daxDialogCta.reinstallerQuickSetupContent.quickSetupTitleHidden.text =
                        getString(R.string.preOnboardingReinstallQuickSetupTitle).html(requireContext())

                    val showBottomWingAnimation = BrandDesignUpdateOnboardingLayoutHelper.hasSpaceForAnimation(
                        rootView = binding.root,
                        dialogView = binding.daxDialogCta.root,
                        decorationView = binding.bottomWingAnimation,
                    )
                    if (!showBottomWingAnimation) {
                        binding.bottomWingAnimation.isVisible = false
                    }

                    val transition = ChangeBounds().apply {
                        duration = DIALOG_TRANSITION_DURATION
                    }
                    changeBoundsTransition = transition
                    val listener = object : TransitionListenerAdapter() {
                        override fun onTransitionEnd(transition: androidx.transition.Transition) {
                            // Transition callbacks can still arrive after removeListener() if the
                            // end event was already in flight; unlike Animator.cancel(), this is
                            // not a synchronous stop.
                            if (view == null) return
                            binding.daxDialogCta.reinstallerQuickSetupContent.quickSetupTitle.startOnboardingTypingAnimation(
                                getString(R.string.preOnboardingReinstallQuickSetupTitle),
                            ) {
                                quickSetupFadeInAnimatorSet = AnimatorSet().apply {
                                    playTogether(
                                        ObjectAnimator.ofFloat(
                                            binding.daxDialogCta.reinstallerQuickSetupContent.quickSetupOptionsContainer,
                                            View.ALPHA,
                                            1f,
                                        ).setDuration(DIALOG_CONTENT_FADE_IN_DURATION),
                                        ObjectAnimator.ofFloat(binding.daxDialogCta.primaryCta, View.ALPHA, 1f)
                                            .setDuration(DIALOG_CONTENT_FADE_IN_DURATION),
                                    )
                                    addListener(
                                        object : AnimatorListenerAdapter() {
                                            override fun onAnimationEnd(animation: Animator) {
                                                binding.daxDialogCta.primaryCta.setOnClickListener {
                                                    viewModel.onPrimaryCtaClicked()
                                                }
                                                setQuickSetupListeners()

                                                isAnimating = false
                                            }
                                        },
                                    )
                                    start()
                                }
                            }
                        }
                    }
                    changeBoundsTransitionListener = listener
                    transition.addListener(listener)
                    TransitionManager.beginDelayedTransition(binding.root as ViewGroup, transition)

                    val cardView = binding.daxDialogCta.cardView
                    cardView.setArrowAnimationTarget(ARROW_TARGET_OFFSET_END_DP.toPx().toFloat())
                    arrowSlideAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                        duration = DIALOG_TRANSITION_DURATION
                        interpolator = androidx.interpolator.view.animation.FastOutSlowInInterpolator()
                        addUpdateListener {
                            cardView.setArrowAnimationFraction(it.animatedValue as Float)
                        }
                        start()
                    }

                    if (showBottomWingAnimation) {
                        playBottomWingAnimation()
                    }

                    binding.daxDialogCta.root.updateLayoutParams<ConstraintLayout.LayoutParams> {
                        if (showBottomWingAnimation) {
                            verticalBias = if (deviceInfo.isTablet()) 0.5f else 0f
                            bottomToTop = binding.bottomWingAnimation.id
                            bottomToBottom = ConstraintLayout.LayoutParams.UNSET
                        } else {
                            verticalBias = if (deviceInfo.isTablet()) 0.5f else 0f
                            bottomToTop = ConstraintLayout.LayoutParams.UNSET
                            bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                        }
                    }

                    binding.daxDialogCta.primaryCta.text = getString(R.string.preOnboardingReinstallStartBrowsing)
                    binding.daxDialogCta.primaryCta.alpha = 0f
                }

                COMPARISON_CHART, AI_COMPARISON_CHART -> {
                    populateComparisonChart(comparisonChartConfig)
                    backgroundAnimator?.transitionTo(
                        step = OnboardingBackgroundStep.ComparisonChart,
                    )

                    // Swap content before measuring so the dialog height reflects the comparison chart
                    binding.daxDialogCta.welcomeContent.root.isVisible = false
                    binding.daxDialogCta.secondaryCta.isVisible = false
                    binding.daxDialogCta.comparisonChartContent.root.isVisible = true

                    val showBottomWingAnimation = BrandDesignUpdateOnboardingLayoutHelper.hasSpaceForAnimation(
                        rootView = binding.root,
                        dialogView = binding.daxDialogCta.root,
                        decorationView = binding.bottomWingAnimation,
                    )
                    if (!showBottomWingAnimation) {
                        binding.bottomWingAnimation.isVisible = false
                        (binding.daxDialogCta.root.layoutParams as ConstraintLayout.LayoutParams).apply {
                            verticalBias = 0f
                            bottomToTop = ConstraintLayout.LayoutParams.UNSET
                            bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                        }
                    }
                    binding.daxDialogCta.cardView.setArrowDepthFraction(if (showBottomWingAnimation) 1f else 0f)

                    val transition = ChangeBounds().apply {
                        duration = DIALOG_TRANSITION_DURATION
                    }
                    changeBoundsTransition = transition
                    val listener = object : TransitionListenerAdapter() {
                        override fun onTransitionEnd(transition: androidx.transition.Transition) {
                            // Transition callbacks can still arrive after removeListener() if the
                            // end event was already in flight; unlike Animator.cancel(), this is
                            // not a synchronous stop.
                            if (view == null) return
                            binding.daxDialogCta.comparisonChartContent.comparisonChartTitle.startOnboardingTypingAnimation(
                                getString(comparisonChartConfig.titleRes).preventWidows(),
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
                                            isAnimating = false
                                            binding.daxDialogCta.primaryCta.setOnClickListener { viewModel.onPrimaryCtaClicked() }
                                            playCheckIconAnimation()
                                        }
                                    })
                                    start()
                                }
                            }
                        }
                    }
                    changeBoundsTransitionListener = listener
                    transition.addListener(listener)
                    binding.daxDialogCta.stepIndicator.setSteps(totalSteps = maxPageCount, currentStep = 1)
                    binding.daxDialogCta.stepIndicator.isVisible = true
                    binding.daxDialogCta.stepIndicator.alpha = 0f
                    TransitionManager.beginDelayedTransition(binding.root as ViewGroup, transition)

                    val cardView = binding.daxDialogCta.cardView
                    cardView.setArrowAnimationTarget(ARROW_TARGET_OFFSET_END_DP.toPx().toFloat())
                    arrowSlideAnimator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
                        duration = DIALOG_TRANSITION_DURATION
                        interpolator = androidx.interpolator.view.animation.FastOutSlowInInterpolator()
                        addUpdateListener {
                            cardView.setArrowAnimationFraction(it.animatedValue as Float)
                        }
                        start()
                    }

                    if (showBottomWingAnimation) playBottomWingAnimation()

                    binding.welcomeScreenWalkingDax.isVisible = false
                    (binding.daxDialogCta.root.layoutParams as ConstraintLayout.LayoutParams).apply {
                        if (showBottomWingAnimation) {
                            verticalBias = if (deviceInfo.isTablet()) 0.5f else 0f
                            bottomToTop = binding.bottomWingAnimation.id
                            bottomToBottom = ConstraintLayout.LayoutParams.UNSET
                        } else {
                            verticalBias = 0f
                            bottomToTop = ConstraintLayout.LayoutParams.UNSET
                            bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                        }
                    }

                    binding.daxDialogCta.primaryCta.text = getString(comparisonChartConfig.primaryCtaTextRes)
                    binding.daxDialogCta.primaryCta.alpha = 0f
                }

                SKIP_ONBOARDING_OPTION -> {
                    val fadeOutAnimators = listOf<Animator>(
                        ObjectAnimator.ofFloat(binding.daxDialogCta.welcomeContent.titleText, View.ALPHA, 0f)
                            .setDuration(OUTRO_FADE_DURATION),
                        ObjectAnimator.ofFloat(binding.daxDialogCta.welcomeContent.bodyText1, View.ALPHA, 0f)
                            .setDuration(OUTRO_FADE_DURATION),
                        ObjectAnimator.ofFloat(binding.daxDialogCta.welcomeContent.bodyText2, View.ALPHA, 0f)
                            .setDuration(OUTRO_FADE_DURATION),
                        ObjectAnimator.ofFloat(binding.daxDialogCta.primaryCta, View.ALPHA, 0f)
                            .setDuration(OUTRO_FADE_DURATION),
                        ObjectAnimator.ofFloat(binding.daxDialogCta.secondaryCta, View.ALPHA, 0f)
                            .setDuration(OUTRO_FADE_DURATION),
                    )

                    skipOnboardingFadeOutAnimatorSet = AnimatorSet().apply {
                        playTogether(fadeOutAnimators)
                        addListener(object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: Animator) {
                                binding.daxDialogCta.welcomeContent.hiddenTitleText.text =
                                    getString(R.string.preOnboardingDaxDialog3Title)
                                binding.daxDialogCta.welcomeContent.bodyText1.text =
                                    getString(R.string.preOnboardingDaxDialog3Text).preventWidows().html(requireContext())
                                binding.daxDialogCta.welcomeContent.bodyText2.isGone = true

                                binding.daxDialogCta.welcomeContent.titleText.cancelAnimation()
                                binding.daxDialogCta.welcomeContent.titleText.text = ""
                                binding.daxDialogCta.welcomeContent.titleText.alpha = 1f

                                binding.daxDialogCta.primaryCta.text = getString(R.string.preOnboardingDaxDialog3Button)
                                binding.daxDialogCta.secondaryCta.text = getString(R.string.preOnboardingDaxDialog3SecondaryButton)

                                binding.daxDialogCta.welcomeContent.titleText.startOnboardingTypingAnimation(
                                    getString(R.string.preOnboardingDaxDialog3Title),
                                ) {
                                    skipOnboardingFadeInAnimatorSet = AnimatorSet().apply {
                                        playTogether(
                                            ObjectAnimator.ofFloat(binding.daxDialogCta.welcomeContent.bodyText1, View.ALPHA, 1f)
                                                .setDuration(DIALOG_CONTENT_FADE_IN_DURATION),
                                            ObjectAnimator.ofFloat(binding.daxDialogCta.primaryCta, View.ALPHA, 1f)
                                                .setDuration(DIALOG_CONTENT_FADE_IN_DURATION),
                                            ObjectAnimator.ofFloat(binding.daxDialogCta.secondaryCta, View.ALPHA, 1f)
                                                .setDuration(DIALOG_CONTENT_FADE_IN_DURATION),
                                        )
                                        addListener(object : AnimatorListenerAdapter() {
                                            override fun onAnimationEnd(animation: Animator) {
                                                binding.daxDialogCta.primaryCta.setOnClickListener { viewModel.onPrimaryCtaClicked() }
                                                binding.daxDialogCta.secondaryCta.setOnClickListener { viewModel.onSecondaryCtaClicked() }
                                                isAnimating = false
                                            }
                                        })
                                        start()
                                    }
                                }
                            }
                        })
                        start()
                    }
                }

                ADDRESS_BAR_POSITION -> {
                    dismissBottomWingAnimation()
                    binding.daxDialogCta.comparisonChartContent.root.isVisible = false
                    binding.daxDialogCta.addressBarContent.root.isVisible = true
                    updateAddressBarPositionOptions(selectedAddressBarPosition, showSplitOption, animate = false)

                    val showBobbingDax = BrandDesignUpdateOnboardingLayoutHelper.hasSpaceForAnimation(
                        rootView = binding.root,
                        dialogView = binding.daxDialogCta.root,
                        decorationView = binding.bobbingDaxAnimation,
                    )
                    (binding.daxDialogCta.root.layoutParams as ConstraintLayout.LayoutParams).apply {
                        if (showBobbingDax && deviceInfo.isTablet()) {
                            verticalBias = 0.5f
                            bottomToTop = binding.bobbingDaxAnimation.id
                            bottomToBottom = ConstraintLayout.LayoutParams.UNSET
                        } else {
                            verticalBias = 0f
                            bottomToTop = ConstraintLayout.LayoutParams.UNSET
                            bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                        }
                    }
                    backgroundAnimator?.transitionTo(
                        step = OnboardingBackgroundStep.AddressBar,
                    )

                    if (showBobbingDax) {
                        animateBobbingDaxIn()
                    } else {
                        bobbingDaxAnimator?.cancel()
                        bobbingDaxAnimator = null
                        binding.bobbingDaxAnimation.apply {
                            if (this.isAnimating) cancelAnimation()
                            isVisible = false
                        }
                    }
                    binding.daxDialogCta.cardView.setArrowDepthFraction(if (showBobbingDax) 1f else 0f)

                    binding.daxDialogCta.stepIndicator.setSteps(totalSteps = maxPageCount, currentStep = 1)
                    binding.daxDialogCta.stepIndicator.animateToNextStep()

                    val transition = ChangeBounds().apply {
                        duration = DIALOG_TRANSITION_DURATION
                    }
                    changeBoundsTransition = transition
                    val listener = object : TransitionListenerAdapter() {
                        override fun onTransitionEnd(transition: androidx.transition.Transition) {
                            if (view == null) return
                            binding.daxDialogCta.addressBarContent.addressBarTitle.startOnboardingTypingAnimation(
                                getString(R.string.preOnboardingAddressBarTitle),
                            ) {
                                addressBarFadeInAnimatorSet = AnimatorSet().apply {
                                    playTogether(
                                        ObjectAnimator.ofFloat(
                                            binding.daxDialogCta.addressBarContent.addressBarPicker,
                                            View.ALPHA,
                                            1f,
                                        ).setDuration(DIALOG_CONTENT_FADE_IN_DURATION),
                                        ObjectAnimator.ofFloat(binding.daxDialogCta.primaryCta, View.ALPHA, 1f)
                                            .setDuration(DIALOG_CONTENT_FADE_IN_DURATION),
                                    )
                                    addListener(object : AnimatorListenerAdapter() {
                                        override fun onAnimationEnd(animation: Animator) {
                                            isAnimating = false
                                            binding.daxDialogCta.primaryCta.setOnClickListener { viewModel.onPrimaryCtaClicked() }
                                        }
                                    })
                                    start()
                                }
                            }
                        }
                    }
                    changeBoundsTransitionListener = listener
                    transition.addListener(listener)

                    binding.daxDialogCta.root.translationZ = 1f.toPx()
                    TransitionManager.beginDelayedTransition(binding.daxDialogCta.root as ViewGroup, transition)

                    binding.daxDialogCta.primaryCta.text = getString(R.string.preOnboardingAddressBarOkButton)
                    binding.daxDialogCta.primaryCta.alpha = 0f

                    updateAddressBarPositionOptions(selectedAddressBarPosition, showSplitOption)
                }

                INPUT_SCREEN -> {
                    backgroundAnimator?.transitionTo(
                        step = OnboardingBackgroundStep.InputType,
                    )

                    animateBobbingDaxOut()

                    binding.welcomeScreenWalkingDax.isVisible = false
                    binding.daxDialogCta.welcomeContent.root.isVisible = false
                    binding.daxDialogCta.secondaryCta.isVisible = false
                    binding.daxDialogCta.comparisonChartContent.root.isVisible = false
                    binding.daxDialogCta.addressBarContent.root.isVisible = false
                    binding.daxDialogCta.inputScreenContent.root.isVisible = true
                    updateAiChatToggleState(binding, withAi = inputScreenSelected, transition = BrandDesignInputScreenPicker.Transition.NONE)

                    val leftWingView = binding.leftWingAnimation
                    val showLeftWingAnimation = BrandDesignUpdateOnboardingLayoutHelper.hasSpaceForAnimation(
                        rootView = binding.root,
                        dialogView = binding.daxDialogCta.root,
                        decorationView = leftWingView,
                    )
                    if (!showLeftWingAnimation) {
                        binding.leftWingAnimation.isVisible = false
                    } else {
                        playLeftWingAnimation()
                    }

                    val transition = ChangeBounds().apply {
                        duration = DIALOG_TRANSITION_DURATION
                    }
                    changeBoundsTransition = transition
                    val listener = object : TransitionListenerAdapter() {
                        override fun onTransitionEnd(transition: androidx.transition.Transition) {
                            if (view == null) return
                            binding.daxDialogCta.inputScreenContent.inputScreenTitle.startOnboardingTypingAnimation(
                                getString(R.string.preOnboardingInputScreenTitleUpdated),
                            ) {
                                inputScreenFadeInAnimatorSet = AnimatorSet().apply {
                                    playTogether(
                                        ObjectAnimator.ofFloat(
                                            binding.daxDialogCta.inputScreenContent.inputScreenPicker,
                                            View.ALPHA,
                                            1f,
                                        ).setDuration(DIALOG_CONTENT_FADE_IN_DURATION),
                                        ObjectAnimator.ofFloat(
                                            binding.daxDialogCta.inputScreenContent.inputScreenDescription,
                                            View.ALPHA,
                                            1f,
                                        ).setDuration(DIALOG_CONTENT_FADE_IN_DURATION),
                                        ObjectAnimator.ofFloat(binding.daxDialogCta.primaryCta, View.ALPHA, 1f)
                                            .setDuration(DIALOG_CONTENT_FADE_IN_DURATION),
                                    )
                                    addListener(object : AnimatorListenerAdapter() {
                                        override fun onAnimationEnd(animation: Animator) {
                                            isAnimating = false
                                            binding.daxDialogCta.inputScreenContent.inputScreenPicker.startWithAiAnimation(delayedStart = true)
                                        }
                                    })
                                    start()
                                }
                            }
                        }
                    }
                    changeBoundsTransitionListener = listener
                    transition.addListener(listener)

                    binding.daxDialogCta.stepIndicator.setSteps(totalSteps = maxPageCount, currentStep = 2)
                    binding.daxDialogCta.stepIndicator.animateToNextStep()

                    TransitionManager.beginDelayedTransition(binding.root as ViewGroup, transition)

                    binding.daxDialogCta.root.updateLayoutParams<ConstraintLayout.LayoutParams> {
                        if (showLeftWingAnimation && deviceInfo.isTablet()) {
                            verticalBias = 0.5f
                            bottomToTop = binding.leftWingAnimation.id
                            bottomToBottom = ConstraintLayout.LayoutParams.UNSET
                        } else {
                            verticalBias = 0f
                            bottomToTop = ConstraintLayout.LayoutParams.UNSET
                            bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                        }
                    }
                    binding.daxDialogCta.cardView.setArrowDepthFraction(if (showLeftWingAnimation) 1f else 0f)

                    val descriptionText = getString(R.string.preOnboardingInputScreenDescription).preventWidows()
                    binding.daxDialogCta.inputScreenContent.inputScreenDescription.text = descriptionText.html(requireContext())

                    binding.daxDialogCta.primaryCta.text = getString(R.string.preOnboardingInputScreenButton)
                    binding.daxDialogCta.primaryCta.setOnClickListener { viewModel.onPrimaryCtaClicked() }
                    binding.daxDialogCta.primaryCta.alpha = 0f
                }

                INPUT_SCREEN_PREVIEW -> {
                    dismissLeftWingAnimation()

                    stepIndicatorFadeOutAnimator = ObjectAnimator.ofFloat(binding.daxDialogCta.stepIndicator, View.ALPHA, 0f)
                        .apply {
                            duration = OUTRO_FADE_DURATION
                            addListener(object : AnimatorListenerAdapter() {
                                override fun onAnimationEnd(animation: Animator) {
                                    if (view == null) return
                                    binding.daxDialogCta.stepIndicator.isVisible = false
                                }
                            })
                            start()
                        }

                    binding.daxDialogCta.root.updateLayoutParams<ConstraintLayout.LayoutParams> {
                        if (deviceInfo.isTablet()) {
                            verticalBias = 0.5f
                            bottomToTop = ConstraintLayout.LayoutParams.UNSET
                            bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                        } else {
                            verticalBias = 0f
                            bottomToTop = ConstraintLayout.LayoutParams.UNSET
                            bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                        }
                    }

                    val transition = ChangeBounds().apply {
                        duration = DIALOG_TRANSITION_DURATION
                    }
                    changeBoundsTransition = transition
                    val listener = object : TransitionListenerAdapter() {
                        override fun onTransitionEnd(transition: androidx.transition.Transition) {
                            if (view == null) return
                            val previewContent = binding.daxDialogCta.inputScreenPreviewContent
                            previewContent.inputScreenPreviewTitle.startOnboardingTypingAnimation(
                                getString(R.string.preOnboardingInputModeDemoTitle),
                            ) {
                                inputScreenPreviewFadeInAnimatorSet = AnimatorSet().apply {
                                    playTogether(
                                        ObjectAnimator.ofFloat(previewContent.inputModeToggle, View.ALPHA, 1f)
                                            .setDuration(DIALOG_CONTENT_FADE_IN_DURATION),
                                        ObjectAnimator.ofFloat(previewContent.inputModeDemoCard, View.ALPHA, 1f)
                                            .setDuration(DIALOG_CONTENT_FADE_IN_DURATION),
                                    )
                                    addListener(object : AnimatorListenerAdapter() {
                                        override fun onAnimationEnd(animation: Animator) {
                                            if (view == null) return

                                            previewContent.inputText.apply {
                                                isFocusable = true
                                                isFocusableInTouchMode = true

                                                if (resources.configuration.screenHeightDp >= MIN_SCREEN_HEIGHT_FOR_KEYBOARD_DP) {
                                                    post {
                                                        if (view == null) return@post
                                                        activity?.showKeyboard(previewContent.inputText)
                                                    }
                                                }
                                            }

                                            playSuggestionButtonsAnimation()
                                        }
                                    })
                                    start()
                                }
                            }
                        }
                    }
                    changeBoundsTransitionListener = listener
                    transition.addListener(listener)

                    TransitionManager.beginDelayedTransition(binding.root as ViewGroup, transition)

                    binding.daxDialogCta.cardView.setShowArrow(false)
                    binding.daxDialogCta.inputScreenContent.root.isVisible = false
                    binding.daxDialogCta.inputScreenPreviewContent.root.isVisible = true

                    if (android.os.Build.VERSION.SDK_INT >= 28) {
                        binding.daxDialogCta.inputScreenPreviewContent.inputModeDemoCard.addBottomShadow()
                    }

                    binding.daxDialogCta.inputScreenPreviewContent.inputModeToggle.alpha = 0f
                    binding.daxDialogCta.inputScreenPreviewContent.inputModeDemoCard.alpha = 0f
                    binding.daxDialogCta.primaryCta.isVisible = false

                    val state = viewModel.viewState.value
                    val defaultMode = if (state.inputScreenPreviewIsSearchSelected) InputMode.SEARCH else InputMode.CHAT
                    val suggestions = if (state.inputScreenPreviewIsSearchSelected) {
                        state.inputScreenPreviewSearchSuggestions
                    } else {
                        state.inputScreenPreviewChatSuggestions
                    }
                    setInputScreenPreviewInputMode(defaultMode, suggestions)

                    if (!state.inputScreenPreviewIsSearchSelected) {
                        binding.daxDialogCta.inputScreenPreviewContent.inputModeToggle.getTabAt(1)?.select()
                    }

                    binding.daxDialogCta.inputScreenPreviewContent.inputModeToggle.addOnTabSelectedListener(
                        object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
                            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab) {
                                val changeBounds = ChangeBounds().apply { duration = DIALOG_TRANSITION_DURATION }
                                TransitionManager.beginDelayedTransition(
                                    binding.daxDialogCta.cardView,
                                    changeBounds,
                                )
                                val tabState = viewModel.viewState.value
                                if (tab.position == 0) {
                                    setInputScreenPreviewInputMode(InputMode.SEARCH, tabState.inputScreenPreviewSearchSuggestions)
                                } else {
                                    setInputScreenPreviewInputMode(InputMode.CHAT, tabState.inputScreenPreviewChatSuggestions)
                                }
                            }
                            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
                            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
                        },
                    )
                }
            }
        }
    }

    private fun showDialogWithoutAnimation(
        onboardingDialogType: PreOnboardingDialogType,
        selectedAddressBarPosition: OmnibarType,
        showSplitOption: Boolean,
        inputScreenSelected: Boolean,
        maxPageCount: Int,
        comparisonChartConfig: ComparisonChartConfig,
    ) {
        snapToIntroEndState()

        when (onboardingDialogType) {
            INITIAL, INITIAL_REINSTALL_USER, SYNC_RESTORE -> {
                val isSyncRestore = onboardingDialogType == SYNC_RESTORE
                val showSecondaryCta = onboardingDialogType == INITIAL_REINSTALL_USER || isSyncRestore

                binding.logoAnimation.alpha = 0f
                binding.welcomeTitle.alpha = 0f

                backgroundAnimator?.snapTo(OnboardingBackgroundStep.Welcome)

                binding.daxDialogCta.secondaryCta.visibility = if (showSecondaryCta) View.INVISIBLE else View.GONE

                val showWalkingDax = applyWalkingDaxLayout()
                if (showWalkingDax) {
                    with(binding.welcomeScreenWalkingDax) {
                        cancelAnimation()
                        progress = 1f
                        alpha = 1f
                        translationX = -WALKING_DAX_FINAL_X_DP.toPx().toFloat()
                    }
                }
                binding.daxDialogCta.cardView.setArrowDepthFraction(if (showWalkingDax) 1f else 0f)

                binding.daxDialogCta.root.isVisible = true
                binding.daxDialogCta.daxCtaContainer.alpha = 1f
                binding.daxDialogCta.welcomeContent.root.alpha = 1f
                binding.daxDialogCta.welcomeContent.titleText.cancelAnimation()
                val titleString = if (isSyncRestore) {
                    getString(R.string.syncRestoreDialogBrandDesignTitle)
                } else {
                    getString(R.string.preOnboardingWelcomeDialogTitle)
                }
                binding.daxDialogCta.welcomeContent.titleText.text = titleString
                if (isSyncRestore) {
                    binding.daxDialogCta.welcomeContent.hiddenTitleText.text = titleString
                    binding.daxDialogCta.welcomeContent.bodyText1.text =
                        getString(R.string.syncRestoreDialogBrandDesignBody1).preventWidows().html(requireContext())
                    binding.daxDialogCta.primaryCta.text = getString(R.string.syncRestoreDialogPrimaryCta)
                    binding.daxDialogCta.secondaryCta.text = getString(R.string.syncRestoreDialogSecondaryCta)
                }
                // SYNC_RESTORE shows no second body line; INITIAL/INITIAL_REINSTALL_USER do.
                // Set isVisible explicitly so a prior dialog that hid bodyText2 doesn't leak into this one.
                binding.daxDialogCta.welcomeContent.bodyText2.isVisible = !isSyncRestore
                binding.daxDialogCta.welcomeContent.bodyText1.alpha = 1f
                binding.daxDialogCta.welcomeContent.bodyText2.alpha = 1f
                binding.daxDialogCta.primaryCta.alpha = 1f
                binding.daxDialogCta.primaryCta.setOnClickListener { viewModel.onPrimaryCtaClicked() }
                if (showSecondaryCta) {
                    binding.daxDialogCta.secondaryCta.isVisible = true
                    binding.daxDialogCta.secondaryCta.alpha = 1f
                    binding.daxDialogCta.secondaryCta.setOnClickListener { viewModel.onSecondaryCtaClicked() }
                }
            }

            COMPARISON_CHART, AI_COMPARISON_CHART -> {
                populateComparisonChart(comparisonChartConfig)
                binding.logoAnimation.alpha = 0f
                binding.welcomeTitle.alpha = 0f

                backgroundAnimator?.snapTo(OnboardingBackgroundStep.ComparisonChart)

                binding.welcomeScreenWalkingDax.isVisible = false
                val cardView = binding.daxDialogCta.cardView
                cardView.setArrowAnimationTarget(ARROW_TARGET_OFFSET_END_DP.toPx().toFloat())
                cardView.setArrowAnimationFraction(1f)

                // Swap content before measuring so the dialog height reflects the comparison chart
                binding.daxDialogCta.welcomeContent.root.isVisible = false
                binding.daxDialogCta.secondaryCta.isVisible = false
                binding.daxDialogCta.comparisonChartContent.root.isVisible = true

                val showWing = BrandDesignUpdateOnboardingLayoutHelper.hasSpaceForAnimation(
                    rootView = binding.root,
                    dialogView = binding.daxDialogCta.root,
                    decorationView = binding.bottomWingAnimation,
                )
                binding.bottomWingAnimation.apply {
                    cancelAnimation()
                    isVisible = showWing
                    alpha = 1f
                    progress = WING_STOP_PROGRESS
                }
                binding.daxDialogCta.root.updateLayoutParams<ConstraintLayout.LayoutParams> {
                    if (showWing) {
                        verticalBias = if (deviceInfo.isTablet()) 0.5f else 0f
                        bottomToTop = binding.bottomWingAnimation.id
                        bottomToBottom = ConstraintLayout.LayoutParams.UNSET
                    } else {
                        verticalBias = 0f
                        bottomToTop = ConstraintLayout.LayoutParams.UNSET
                        bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                    }
                }
                binding.daxDialogCta.cardView.setArrowDepthFraction(if (showWing) 1f else 0f)
                binding.daxDialogCta.comparisonChartContent.comparisonChartTitle.cancelAnimation()
                binding.daxDialogCta.comparisonChartContent.comparisonChartTitle.text =
                    getString(comparisonChartConfig.titleRes).preventWidows()
                binding.daxDialogCta.comparisonChartContent.comparisonTable.alpha = 1f
                comparisonCheckViews().forEach { checkView ->
                    checkView.alpha = 1f
                    checkView.scaleX = 1f
                    checkView.scaleY = 1f
                    checkView.setImageResource(CommonR.drawable.ic_check_green_24)
                }

                binding.daxDialogCta.stepIndicator.isVisible = true
                binding.daxDialogCta.stepIndicator.alpha = 1f
                binding.daxDialogCta.stepIndicator.setSteps(totalSteps = maxPageCount, currentStep = 1)
                binding.daxDialogCta.primaryCta.alpha = 1f
                binding.daxDialogCta.primaryCta.text = getString(comparisonChartConfig.primaryCtaTextRes)
                binding.daxDialogCta.primaryCta.setOnClickListener { viewModel.onPrimaryCtaClicked() }

                binding.daxDialogCta.root.isVisible = true
                binding.daxDialogCta.daxCtaContainer.alpha = 1f
            }

            SKIP_ONBOARDING_OPTION -> {
                binding.logoAnimation.alpha = 0f
                binding.welcomeTitle.alpha = 0f
                backgroundAnimator?.snapTo(OnboardingBackgroundStep.Welcome)

                binding.daxDialogCta.comparisonChartContent.root.isVisible = false
                binding.daxDialogCta.welcomeContent.root.isVisible = true
                binding.daxDialogCta.welcomeContent.hiddenTitleText.text = getString(R.string.preOnboardingDaxDialog3Title)
                binding.daxDialogCta.welcomeContent.bodyText1.text =
                    getString(R.string.preOnboardingDaxDialog3Text).preventWidows().html(requireContext())
                binding.daxDialogCta.welcomeContent.bodyText2.isGone = true
                binding.daxDialogCta.primaryCta.text = getString(R.string.preOnboardingDaxDialog3Button)
                binding.daxDialogCta.secondaryCta.text = getString(R.string.preOnboardingDaxDialog3SecondaryButton)
                binding.daxDialogCta.secondaryCta.visibility = View.INVISIBLE

                val showWalkingDax = applyWalkingDaxLayout()
                if (showWalkingDax) {
                    with(binding.welcomeScreenWalkingDax) {
                        cancelAnimation()
                        progress = 1f
                        alpha = 1f
                        translationX = -WALKING_DAX_FINAL_X_DP.toPx().toFloat()
                    }
                }
                binding.daxDialogCta.cardView.setArrowDepthFraction(if (showWalkingDax) 1f else 0f)

                binding.daxDialogCta.welcomeContent.titleText.cancelAnimation()
                binding.daxDialogCta.welcomeContent.titleText.text = getString(R.string.preOnboardingDaxDialog3Title)
                binding.daxDialogCta.welcomeContent.titleText.alpha = 1f
                binding.daxDialogCta.welcomeContent.bodyText1.alpha = 1f

                binding.daxDialogCta.primaryCta.alpha = 1f
                binding.daxDialogCta.primaryCta.setOnClickListener { viewModel.onPrimaryCtaClicked() }

                binding.daxDialogCta.secondaryCta.isVisible = true
                binding.daxDialogCta.secondaryCta.alpha = 1f
                binding.daxDialogCta.secondaryCta.setOnClickListener { viewModel.onSecondaryCtaClicked() }

                binding.daxDialogCta.root.isVisible = true
                binding.daxDialogCta.daxCtaContainer.alpha = 1f
            }

            ADDRESS_BAR_POSITION -> {
                binding.logoAnimation.alpha = 0f
                binding.welcomeTitle.alpha = 0f

                // Address bar already visible — just update the selected option without re-running full dialog setup.
                if (binding.daxDialogCta.addressBarContent.root.isVisible) {
                    updateAddressBarPositionOptions(selectedAddressBarPosition, showSplitOption)
                    return
                }

                binding.bottomWingAnimation.isVisible = false

                backgroundAnimator?.snapTo(OnboardingBackgroundStep.AddressBar)

                binding.welcomeScreenWalkingDax.isVisible = false
                binding.daxDialogCta.welcomeContent.root.isVisible = false
                binding.daxDialogCta.secondaryCta.isVisible = false
                binding.daxDialogCta.comparisonChartContent.root.isVisible = false

                binding.daxDialogCta.addressBarContent.root.isVisible = true
                updateAddressBarPositionOptions(selectedAddressBarPosition, showSplitOption, animate = false)
                val showBobbingDax = BrandDesignUpdateOnboardingLayoutHelper.hasSpaceForAnimation(
                    rootView = binding.root,
                    dialogView = binding.daxDialogCta.root,
                    decorationView = binding.bobbingDaxAnimation,
                )
                binding.daxDialogCta.root.updateLayoutParams<ConstraintLayout.LayoutParams> {
                    if (showBobbingDax && deviceInfo.isTablet()) {
                        verticalBias = 0.5f
                        bottomToTop = binding.bobbingDaxAnimation.id
                        bottomToBottom = ConstraintLayout.LayoutParams.UNSET
                    } else {
                        verticalBias = 0f
                        bottomToTop = ConstraintLayout.LayoutParams.UNSET
                        bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                    }
                }
                val cardView = binding.daxDialogCta.cardView
                cardView.setArrowAnimationTarget(ARROW_TARGET_OFFSET_END_DP.toPx().toFloat())
                cardView.setArrowAnimationFraction(1f)
                cardView.setArrowDepthFraction(if (showBobbingDax) 1f else 0f)
                binding.daxDialogCta.addressBarContent.root.alpha = 1f
                binding.daxDialogCta.addressBarContent.addressBarTitle.cancelAnimation()
                binding.daxDialogCta.addressBarContent.addressBarTitle.text =
                    getString(R.string.preOnboardingAddressBarTitle)
                binding.daxDialogCta.addressBarContent.addressBarPicker.alpha = 1f

                binding.daxDialogCta.stepIndicator.isVisible = true
                binding.daxDialogCta.stepIndicator.alpha = 1f
                binding.daxDialogCta.stepIndicator.setSteps(totalSteps = maxPageCount, currentStep = 2)
                binding.daxDialogCta.primaryCta.alpha = 1f
                binding.daxDialogCta.primaryCta.text = getString(R.string.preOnboardingAddressBarOkButton)
                binding.daxDialogCta.primaryCta.setOnClickListener { viewModel.onPrimaryCtaClicked() }

                binding.daxDialogCta.root.isVisible = true
                binding.daxDialogCta.root.translationZ = 1f.toPx()
                binding.daxDialogCta.daxCtaContainer.alpha = 1f

                if (showBobbingDax) {
                    binding.bobbingDaxAnimation.apply {
                        isVisible = true
                        alpha = 1f
                        translationX = 0f
                        if (!this.isAnimating) playAnimation()
                    }
                } else {
                    bobbingDaxAnimator?.cancel()
                    bobbingDaxAnimator = null
                    binding.bobbingDaxAnimation.apply {
                        if (this.isAnimating) cancelAnimation()
                        isVisible = false
                    }
                }

                updateAddressBarPositionOptions(selectedAddressBarPosition, showSplitOption, animate = false)
            }

            INPUT_SCREEN -> {
                if (binding.daxDialogCta.inputScreenContent.root.isVisible) {
                    // If the dialog is already showing, update toggle selection (crossfade state) without re-running the full setup.
                    // Also update and resume lottie animations.
                    updateAiChatToggleState(
                        binding,
                        withAi = inputScreenSelected,
                        transition = BrandDesignInputScreenPicker.Transition.CROSSFADE_ANIMATE,
                    )
                    return
                }

                binding.bottomWingAnimation.isVisible = false

                binding.welcomeScreenWalkingDax.isVisible = false
                binding.daxDialogCta.welcomeContent.root.isVisible = false
                binding.daxDialogCta.secondaryCta.isVisible = false
                binding.daxDialogCta.comparisonChartContent.root.isVisible = false
                binding.daxDialogCta.addressBarContent.root.isVisible = false
                binding.daxDialogCta.inputScreenContent.root.isVisible = true
                updateAiChatToggleState(binding, withAi = inputScreenSelected, transition = BrandDesignInputScreenPicker.Transition.NONE)

                val leftWingView = binding.leftWingAnimation
                val showLeftWing = BrandDesignUpdateOnboardingLayoutHelper.hasSpaceForAnimation(
                    rootView = binding.root,
                    dialogView = binding.daxDialogCta.root,
                    decorationView = leftWingView,
                )
                binding.leftWingAnimation.apply {
                    cancelAnimation()
                    isVisible = showLeftWing
                    alpha = 1f
                    setMinAndMaxProgress(0f, WING_STOP_PROGRESS)
                    progress = WING_STOP_PROGRESS
                }
                if (showLeftWing) {
                    (binding.leftWingAnimation.parent as? View)?.requestLayout()
                }

                binding.logoAnimation.alpha = 0f
                binding.welcomeTitle.alpha = 0f

                backgroundAnimator?.snapTo(OnboardingBackgroundStep.InputType)

                (binding.daxDialogCta.root.layoutParams as ConstraintLayout.LayoutParams).apply {
                    if (showLeftWing && deviceInfo.isTablet()) {
                        verticalBias = 0.5f
                        bottomToTop = binding.leftWingAnimation.id
                        bottomToBottom = ConstraintLayout.LayoutParams.UNSET
                    } else {
                        verticalBias = 0f
                        bottomToTop = ConstraintLayout.LayoutParams.UNSET
                        bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                    }
                }

                val cardView = binding.daxDialogCta.cardView
                cardView.setArrowAnimationTarget(ARROW_TARGET_OFFSET_END_DP.toPx().toFloat())
                cardView.setArrowAnimationFraction(1f)
                cardView.setArrowDepthFraction(if (showLeftWing) 1f else 0f)

                val descriptionText = getString(R.string.preOnboardingInputScreenDescription).preventWidows()
                binding.daxDialogCta.inputScreenContent.inputScreenDescription.text = descriptionText.html(requireContext())

                binding.daxDialogCta.inputScreenContent.inputScreenTitle.cancelAnimation()
                binding.daxDialogCta.inputScreenContent.inputScreenTitle.text =
                    getString(R.string.preOnboardingInputScreenTitleUpdated)
                binding.daxDialogCta.inputScreenContent.inputScreenPicker.alpha = 1f
                binding.daxDialogCta.inputScreenContent.inputScreenDescription.alpha = 1f

                binding.daxDialogCta.stepIndicator.isVisible = true
                binding.daxDialogCta.stepIndicator.alpha = 1f
                binding.daxDialogCta.stepIndicator.setSteps(totalSteps = maxPageCount, currentStep = 3)

                binding.daxDialogCta.primaryCta.alpha = 1f
                binding.daxDialogCta.primaryCta.text = getString(R.string.preOnboardingInputScreenButton)
                binding.daxDialogCta.primaryCta.setOnClickListener { viewModel.onPrimaryCtaClicked() }

                binding.daxDialogCta.root.isVisible = true
                binding.daxDialogCta.root.translationZ = 1f.toPx()
                binding.daxDialogCta.daxCtaContainer.alpha = 1f

                // when view is opened without animations, do not crossfade state but start lottie animations
                updateAiChatToggleState(binding, withAi = inputScreenSelected, transition = BrandDesignInputScreenPicker.Transition.ANIMATE)
            }

            QUICK_SETUP -> {
                binding.logoAnimation.alpha = 0f
                binding.welcomeTitle.alpha = 0f

                // Quick setup already visible — observeQuickSetupSelection keeps the row state in sync, nothing else needs re-running.
                if (binding.daxDialogCta.reinstallerQuickSetupContent.root.isVisible) {
                    return
                }

                binding.welcomeScreenWalkingDax.isVisible = false
                backgroundAnimator?.snapTo(OnboardingBackgroundStep.QuickSetup)

                // Apply the final visibility of every include + cta BEFORE measuring, so the dialog's
                // measured height reflects what will actually be on screen.
                binding.daxDialogCta.welcomeContent.root.isVisible = false
                binding.daxDialogCta.secondaryCta.isVisible = false

                binding.daxDialogCta.reinstallerQuickSetupContent.root.alpha = 1f
                binding.daxDialogCta.reinstallerQuickSetupContent.root.isVisible = true
                updateQuickSetupRowsVisibility()
                binding.daxDialogCta.reinstallerQuickSetupContent.quickSetupOptionsContainer.alpha = 1f
                binding.daxDialogCta.reinstallerQuickSetupContent.quickSetupTitleHidden.text =
                    getString(R.string.preOnboardingReinstallQuickSetupTitle).html(requireContext())
                binding.daxDialogCta.reinstallerQuickSetupContent.quickSetupTitle.cancelAnimation()
                binding.daxDialogCta.reinstallerQuickSetupContent.quickSetupTitle.text =
                    getString(R.string.preOnboardingReinstallQuickSetupTitle).html(requireContext())

                binding.daxDialogCta.primaryCta.alpha = 1f
                binding.daxDialogCta.primaryCta.text = getString(R.string.preOnboardingReinstallStartBrowsing)
                binding.daxDialogCta.primaryCta.setOnClickListener { viewModel.onPrimaryCtaClicked() }
                setQuickSetupListeners()

                val showWing = BrandDesignUpdateOnboardingLayoutHelper.hasSpaceForAnimation(
                    rootView = binding.root,
                    dialogView = binding.daxDialogCta.root,
                    decorationView = binding.bottomWingAnimation,
                )
                binding.bottomWingAnimation.apply {
                    cancelAnimation()
                    isVisible = showWing
                    alpha = 1f
                    progress = WING_STOP_PROGRESS
                }
                binding.daxDialogCta.root.updateLayoutParams<ConstraintLayout.LayoutParams> {
                    if (showWing) {
                        verticalBias = if (deviceInfo.isTablet()) 0.5f else 0f
                        bottomToTop = binding.bottomWingAnimation.id
                        bottomToBottom = ConstraintLayout.LayoutParams.UNSET
                    } else {
                        verticalBias = if (deviceInfo.isTablet()) 0.5f else 0f
                        bottomToTop = ConstraintLayout.LayoutParams.UNSET
                        bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                    }
                }

                val cardView = binding.daxDialogCta.cardView
                cardView.setArrowAnimationTarget(ARROW_TARGET_OFFSET_END_DP.toPx().toFloat())
                cardView.setArrowAnimationFraction(1f)

                binding.daxDialogCta.root.isVisible = true
                binding.daxDialogCta.daxCtaContainer.alpha = 1f
            }

            INPUT_SCREEN_PREVIEW -> {
                if (binding.daxDialogCta.inputScreenPreviewContent.root.isVisible) {
                    return
                }

                binding.logoAnimation.alpha = 0f
                binding.welcomeTitle.alpha = 0f

                binding.leftWingAnimation.isVisible = false
                binding.bottomWingAnimation.isVisible = false

                backgroundAnimator?.snapTo(OnboardingBackgroundStep.InputType)

                binding.welcomeScreenWalkingDax.isVisible = false
                binding.daxDialogCta.root.updateLayoutParams<ConstraintLayout.LayoutParams> {
                    if (deviceInfo.isTablet()) {
                        verticalBias = 0.5f
                        bottomToTop = ConstraintLayout.LayoutParams.UNSET
                        bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                    } else {
                        verticalBias = 0f
                        bottomToTop = ConstraintLayout.LayoutParams.UNSET
                        bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                    }
                }

                binding.daxDialogCta.cardView.setShowArrow(false)

                binding.daxDialogCta.welcomeContent.root.isVisible = false
                binding.daxDialogCta.secondaryCta.isVisible = false
                binding.daxDialogCta.comparisonChartContent.root.isVisible = false
                binding.daxDialogCta.addressBarContent.root.isVisible = false
                binding.daxDialogCta.inputScreenContent.root.isVisible = false

                binding.daxDialogCta.inputScreenPreviewContent.root.isVisible = true

                if (android.os.Build.VERSION.SDK_INT >= 28) {
                    binding.daxDialogCta.inputScreenPreviewContent.inputModeDemoCard.addBottomShadow()
                }

                binding.daxDialogCta.inputScreenPreviewContent.inputScreenPreviewTitle.cancelAnimation()
                binding.daxDialogCta.inputScreenPreviewContent.inputScreenPreviewTitle.text =
                    getString(R.string.preOnboardingInputModeDemoTitle).html(requireContext())
                binding.daxDialogCta.inputScreenPreviewContent.inputModeToggle.alpha = 1f
                binding.daxDialogCta.inputScreenPreviewContent.inputModeDemoCard.alpha = 1f

                binding.daxDialogCta.inputScreenPreviewContent.inputText.apply {
                    isFocusable = true
                    isFocusableInTouchMode = true
                }

                val previewContent = binding.daxDialogCta.inputScreenPreviewContent

                val state = viewModel.viewState.value
                val defaultMode = if (state.inputScreenPreviewIsSearchSelected) InputMode.SEARCH else InputMode.CHAT
                val suggestions = if (state.inputScreenPreviewIsSearchSelected) {
                    state.inputScreenPreviewSearchSuggestions
                } else {
                    state.inputScreenPreviewChatSuggestions
                }
                setInputScreenPreviewInputMode(defaultMode, suggestions)

                if (!state.inputScreenPreviewIsSearchSelected) {
                    previewContent.inputModeToggle.getTabAt(1)?.select()
                }

                previewContent.inputModeToggle.addOnTabSelectedListener(
                    object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
                        override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab) {
                            val changeBounds = ChangeBounds().apply { duration = DIALOG_TRANSITION_DURATION }
                            TransitionManager.beginDelayedTransition(
                                binding.daxDialogCta.cardView,
                                changeBounds,
                            )
                            val tabState = viewModel.viewState.value
                            if (tab.position == 0) {
                                setInputScreenPreviewInputMode(InputMode.SEARCH, tabState.inputScreenPreviewSearchSuggestions)
                            } else {
                                setInputScreenPreviewInputMode(InputMode.CHAT, tabState.inputScreenPreviewChatSuggestions)
                            }
                        }
                        override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
                        override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
                    },
                )

                with(binding.daxDialogCta.inputScreenPreviewContent) {
                    listOf(suggestion1, suggestion2, suggestion3).forEach { button ->
                        button.alpha = 1f
                        button.isVisible = true
                    }
                }

                binding.daxDialogCta.stepIndicator.isVisible = false
                binding.daxDialogCta.primaryCta.isVisible = false

                binding.daxDialogCta.root.isVisible = true
                binding.daxDialogCta.root.translationZ = 1f.toPx()
                binding.daxDialogCta.daxCtaContainer.alpha = 1f
            }
        }
    }

    private fun updateQuickSetupRowsVisibility() {
        val state = viewModel.viewState.value
        with(binding.daxDialogCta.reinstallerQuickSetupContent) {
            setDefaultBrowserItem.isVisible = !state.hideSetDefaultBrowserRow
            setDefaultBrowserDivider.isVisible = !state.hideSetDefaultBrowserRow
            addWidgetItem.isVisible = !state.hideAddWidgetRow
            addWidgetDivider.isVisible = !state.hideAddWidgetRow
        }
    }

    private fun setQuickSetupListeners() {
        with(binding.daxDialogCta.reinstallerQuickSetupContent) {
            setDefaultBrowserItem.setOnCheckedChangeListener { checked ->
                if (checked) {
                    viewModel.onQuickSetupSetAsDefaultClicked()
                } else {
                    viewModel.onQuickSetupSetAsDefaultUnchecked()
                }
            }
            addWidgetItem.setOnCheckedChangeListener { checked ->
                if (checked) {
                    viewModel.onQuickSetupAddHomescreenWidgetClicked()
                } else {
                    viewModel.onQuickSetupRemoveHomescreenWidgetClicked()
                }
            }
            addressBarPositionItem.setOnEditClickListener {
                viewModel.onQuickSetupAddressBarPositionEditClicked()
            }
            addressBarSearchOptionsItem.setOnEditClickListener {
                viewModel.onQuickSetupSearchOptionsEditClicked()
            }
        }

        registerQuickSetupBottomSheetResultListeners()
        observeQuickSetupSelection()
    }

    private fun observeQuickSetupSelection() {
        quickSetupSelectionJob?.cancel()
        quickSetupSelectionJob = viewModel.viewState
            .map { it.selectedAddressBarPosition to it.inputScreenSelected }
            .distinctUntilChanged()
            .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
            .onEach { (position, withAi) -> bindQuickSetupSelection(position, withAi) }
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    private fun bindQuickSetupSelection(position: OmnibarType, withAi: Boolean) {
        with(binding.daxDialogCta.reinstallerQuickSetupContent) {
            addressBarPositionItem.setIcon(addressBarPositionIconRes(position))
            addressBarPositionItem.setSecondaryText(addressBarPositionLabelRes(position))
            addressBarSearchOptionsItem.setIcon(searchOptionsIconRes(withAi))
            addressBarSearchOptionsItem.setSecondaryText(searchOptionsLabelRes(withAi))
        }
    }

    private fun addressBarPositionIconRes(type: OmnibarType): Int = when (type) {
        OmnibarType.SINGLE_TOP -> R.drawable.ic_address_bar_top_24
        OmnibarType.SINGLE_BOTTOM -> R.drawable.ic_address_bar_bottom_24
        OmnibarType.SPLIT -> R.drawable.ic_address_bar_split_24
    }

    private fun addressBarPositionLabelRes(type: OmnibarType): Int = when (type) {
        OmnibarType.SINGLE_TOP -> R.string.preOnboardingAddressBarPositionTop
        OmnibarType.SINGLE_BOTTOM -> R.string.preOnboardingAddressBarPositionBottom
        OmnibarType.SPLIT -> R.string.preOnboardingAddressBarPositionSplit
    }

    private fun searchOptionsIconRes(withAi: Boolean): Int =
        if (withAi) {
            R.drawable.ic_ai_24
        } else {
            R.drawable.ic_search_24
        }

    private fun searchOptionsLabelRes(withAi: Boolean): Int =
        if (withAi) {
            R.string.quickSetupInputScreenSearchAndDuckAi
        } else {
            R.string.quickSetupInputScreenSearchOnly
        }

    private fun showQuickSetupAddressBarPositionBottomSheet(initialSelection: OmnibarType, showSplitOption: Boolean) {
        QuickSetupAddressBarPositionBottomSheet
            .newInstance(initialSelection = initialSelection, showSplitOption = showSplitOption)
            .show(childFragmentManager, QuickSetupAddressBarPositionBottomSheet.TAG)
    }

    private fun showRemoveWidgetInstructionsBottomSheet() {
        RemoveWidgetInstructionsBottomSheet()
            .show(childFragmentManager, RemoveWidgetInstructionsBottomSheet.TAG)
    }

    private fun showQuickSetupSearchOptionsBottomSheet(initialWithAi: Boolean) {
        QuickSetupSearchOptionsBottomSheet
            .newInstance(initialWithAi = initialWithAi)
            .show(childFragmentManager, QuickSetupSearchOptionsBottomSheet.TAG)
    }

    private fun registerQuickSetupBottomSheetResultListeners() {
        childFragmentManager.setFragmentResultListener(
            QuickSetupAddressBarPositionBottomSheet.REQUEST_KEY,
            viewLifecycleOwner,
        ) { _, bundle ->
            val selectedName = bundle.getString(
                QuickSetupAddressBarPositionBottomSheet.RESULT_KEY_SELECTED_POSITION,
            ) ?: return@setFragmentResultListener
            viewModel.onAddressBarPositionOptionSelected(OmnibarType.valueOf(selectedName))
        }
        childFragmentManager.setFragmentResultListener(
            QuickSetupSearchOptionsBottomSheet.REQUEST_KEY,
            viewLifecycleOwner,
        ) { _, bundle ->
            val withAi = bundle.getBoolean(QuickSetupSearchOptionsBottomSheet.RESULT_KEY_WITH_AI)
            viewModel.onInputScreenOptionSelected(withAi = withAi)
        }
        childFragmentManager.setFragmentResultListener(
            RemoveWidgetInstructionsBottomSheet.REQUEST_KEY,
            viewLifecycleOwner,
        ) { _, _ ->
            viewModel.checkWidgetAddedState()
        }
    }

    /**
     * Calculates the walking Dax height and applies the result to the layout.
     * Returns true if Dax is visible, false if hidden due to insufficient space.
     */
    private fun applyWalkingDaxLayout(): Boolean {
        val walkingDaxHeight = BrandDesignUpdateOnboardingLayoutHelper.calculateWalkingDaxHeight(
            rootView = binding.root,
            dialogView = binding.daxDialogCta.root,
            daxView = binding.welcomeScreenWalkingDax,
            maxHeightPx = WALKING_DAX_MAX_HEIGHT_DP.toPx(),
            minHeightPx = WALKING_DAX_MIN_HEIGHT_DP.toPx(),
        )
        if (walkingDaxHeight != null) {
            binding.welcomeScreenWalkingDax.updateLayoutParams { height = walkingDaxHeight }
        } else {
            binding.welcomeScreenWalkingDax.isVisible = false
            binding.daxDialogCta.root.updateLayoutParams<ConstraintLayout.LayoutParams> {
                verticalBias = 0f
                bottomToTop = ConstraintLayout.LayoutParams.UNSET
                bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            }
        }
        return walkingDaxHeight != null
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

    private fun animateBobbingDaxIn() {
        bobbingDaxAnimator?.cancel()
        val screenWidth = binding.root.rootView.width.toFloat()
        binding.bobbingDaxAnimation.also { bobbingDax ->
            bobbingDax.isVisible = true
            bobbingDax.alpha = 0f
            bobbingDax.translationX = screenWidth
            bobbingDaxAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = OnboardingBackgroundAnimator.ENTER_DURATION
                interpolator = OnboardingBackgroundAnimator.EASE_IN_OUT
                addUpdateListener { animator ->
                    val progress = animator.animatedValue as Float
                    bobbingDax.translationX = screenWidth * (1f - progress)
                    bobbingDax.alpha = OnboardingBackgroundAnimator.enterAlpha(progress)
                }
                addListener(object : AnimatorListenerAdapter() {
                    private var cancelled = false

                    override fun onAnimationCancel(animation: Animator) {
                        cancelled = true
                    }

                    override fun onAnimationEnd(animation: Animator) {
                        if (!cancelled) {
                            bobbingDax.playAnimation()
                        }
                    }
                })
                start()
            }
        }
    }

    private fun animateBobbingDaxOut() {
        bobbingDaxAnimator?.cancel()
        val screenWidth = binding.root.rootView.width.toFloat()
        binding.bobbingDaxAnimation?.also { bobbingDax ->
            bobbingDaxAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = OnboardingBackgroundAnimator.EXIT_DURATION
                interpolator = OnboardingBackgroundAnimator.EASE_IN_OUT
                addUpdateListener { animator ->
                    val progress = animator.animatedValue as Float
                    bobbingDax.translationX = -screenWidth * progress
                    bobbingDax.alpha = OnboardingBackgroundAnimator.exitAlpha(progress)
                }
                addListener(object : AnimatorListenerAdapter() {
                    private var cancelled = false

                    override fun onAnimationCancel(animation: Animator) {
                        cancelled = true
                    }

                    override fun onAnimationEnd(animation: Animator) {
                        if (!cancelled) {
                            bobbingDax.isVisible = false
                            bobbingDax.cancelAnimation()
                            bobbingDax.translationX = 0f
                        }
                    }
                })
                start()
            }
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

    private fun skipCurrentDialogAnimation() {
        if (!isAnimating) return

        // End the skip-onboarding fade-out first — its end listener starts the typing animation
        skipOnboardingFadeOutAnimatorSet?.end()

        // Complete any active typing animation (triggers afterAnimation callback which starts fade-in)
        with(binding.daxDialogCta) {
            listOf(
                welcomeContent.titleText,
                comparisonChartContent.comparisonChartTitle,
                addressBarContent.addressBarTitle,
                inputScreenContent.inputScreenTitle,
                inputScreenPreviewContent.inputScreenPreviewTitle,
                reinstallerQuickSetupContent.quickSetupTitle,
            ).filter { it.hasAnimationStarted() }.forEach { it.performClick() }
        }

        // End any running content fade-in animations (end() snaps to final values and triggers end listeners)
        welcomeFadeInAnimatorSet?.end()
        comparisonChartFadeInAnimatorSet?.end()
        comparisonChartDetailAnimatorSet?.end()
        skipOnboardingFadeInAnimatorSet?.end()
        addressBarFadeInAnimatorSet?.end()
        inputScreenFadeInAnimatorSet?.end()
        inputScreenPreviewFadeInAnimatorSet?.end()
        quickSetupFadeInAnimatorSet?.end()
        suggestionButtonsAnimatorSet?.end()

        // Snap check icons to final state — the postDelayed AVD runnables would otherwise animate them in one by one.
        // Only do this on the comparison chart: those runnables are only ever scheduled by playCheckIconAnimation(),
        // and snapping outside that screen leaves the check views at alpha=1/scale=1 with a static drawable, which
        // makes them appear pre-rendered when the comparison chart later fades in.
        if (viewModel.viewState.value.currentDialog in setOf(COMPARISON_CHART, AI_COMPARISON_CHART)) {
            snapCheckIconsToFinalState()
        }
    }

    private fun snapCheckIconsToFinalState() {
        comparisonCheckViews().forEach { checkView ->
            checkView.alpha = 1f
            checkView.scaleX = 1f
            checkView.scaleY = 1f
            checkView.setImageResource(CommonR.drawable.ic_check_green_24)
        }
    }

    private fun playCheckIconAnimation() {
        val overshoot = OvershootInterpolator(CHECK_ICON_OVERSHOOT_TENSION)
        val checkViews = comparisonCheckViews()

        // Reset trimPathEnd up-front: the alpha fade-in completes ~50ms before the postDelayed AVD start,
        // so any stale trimPathEnd=1 from a previous run would render the tick fully drawn during the gap.
        checkViews.forEach { checkView ->
            (checkView.drawable?.mutate() as? AnimatedVectorDrawable)?.reset()
        }

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
            start()
        }
    }

    private fun playBottomWingAnimation() {
        binding.bottomWingAnimation.apply {
            isVisible = true
            alpha = 0f
            setMaxProgress(WING_STOP_PROGRESS)
            bottomWingDelayedRunnable = postDelayed(WING_START_DELAY) {
                animate()
                    .alpha(1f)
                    .setDuration(WING_FADE_IN_DURATION)
                    .start()
                playAnimation()
            }
        }
    }

    private fun dismissBottomWingAnimation() {
        binding.bottomWingAnimation.apply {
            if (!isVisible) return
            setMinProgress(WING_STOP_PROGRESS)
            setMaxProgress(1f)
            speed = 1f
            addAnimatorListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    isGone = true
                    removeAnimatorListener(this)
                }
            })
            playAnimation()
        }
    }

    private fun dismissLeftWingAnimation() {
        binding.leftWingAnimation.apply {
            if (!isVisible) return
            setMinProgress(WING_STOP_PROGRESS)
            setMaxProgress(1f)
            speed = 1f
            addAnimatorListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    isGone = true
                    removeAnimatorListener(this)
                }
            })
            playAnimation()
        }
    }

    private fun playLeftWingAnimation() {
        binding.leftWingAnimation?.apply {
            isVisible = true
            alpha = 0f
            setMaxProgress(WING_STOP_PROGRESS)
            leftWingDelayedRunnable = postDelayed(WING_START_DELAY) {
                animate()
                    .alpha(1f)
                    .setDuration(WING_FADE_IN_DURATION)
                    .start()
                playAnimation()
            }
        }
    }

    private fun setInputScreenPreviewInputMode(
        inputMode: InputMode,
        suggestions: List<DaxDialogIntroOption>,
    ) {
        currentInputMode = inputMode
        val previewContent = binding.daxDialogCta.inputScreenPreviewContent

        listOf(previewContent.suggestion1, previewContent.suggestion2, previewContent.suggestion3)
            .forEachIndexed { index, button ->
                suggestions[index].setOptionView(button)
                button.setOnClickListener {
                    viewModel.onInputModeDemoQuerySubmitted(suggestions[index].link, isChat = inputMode == InputMode.CHAT)
                }
            }

        previewContent.inputModeDemoActionIcon.setOnClickListener {
            val query = previewContent.inputText.text?.toString().orEmpty().trim()
            if (query.isNotEmpty()) {
                viewModel.onInputModeDemoQuerySubmitted(query, isChat = currentInputMode == InputMode.CHAT)
            }
        }

        when (inputMode) {
            InputMode.SEARCH -> {
                previewContent.inputText.minLines = 1
                previewContent.inputText.maxLines = 1
                previewContent.inputText.setHint(R.string.preOnboardingInputModeDemoSearchHint)
                previewContent.inputModeDemoActionIcon.setImageResource(CommonR.drawable.ic_find_search_24)
            }
            InputMode.CHAT -> {
                previewContent.inputText.minLines = 3
                previewContent.inputText.maxLines = 3
                previewContent.inputText.setHint(R.string.preOnboardingInputModeDemoChatHint)
                previewContent.inputModeDemoActionIcon.setImageResource(CommonR.drawable.ic_arrow_right_24)
            }
        }
    }

    private fun playSuggestionButtonsAnimation() {
        val previewContent = binding.daxDialogCta.inputScreenPreviewContent
        val buttons = listOf(
            previewContent.suggestion1,
            previewContent.suggestion2,
            previewContent.suggestion3,
        )

        TransitionManager.beginDelayedTransition(
            binding.daxDialogCta.cardView,
            ChangeBounds().apply { duration = INPUT_SCREEN_PREVIEW_SUGGESTION_ANIMATION_DURATION },
        )
        buttons.forEach { button ->
            button.alpha = 0f
            button.isVisible = true
        }

        val buttonAnimators = buttons.mapIndexed { index, button ->
            ObjectAnimator.ofFloat(button, View.ALPHA, 0f, 1f).apply {
                duration = INPUT_SCREEN_PREVIEW_SUGGESTION_ANIMATION_DURATION
                startDelay = index * INPUT_SCREEN_PREVIEW_SUGGESTION_ANIMATION_DURATION
            }
        }

        suggestionButtonsAnimatorSet = AnimatorSet().apply {
            playTogether(buttonAnimators)
            this.startDelay = INPUT_SCREEN_PREVIEW_SUGGESTIONS_ANIMATION_DELAY
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    isAnimating = false
                }
            })
            start()
        }
    }

    private fun showDefaultBrowserDialog(intent: Intent) {
        startActivityForResult(intent, DEFAULT_BROWSER_ROLE_MANAGER_DIALOG)
    }

    private fun showQuickSetupDefaultBrowserDialog(intent: Intent) {
        startActivityForResult(intent, QUICK_SETUP_DEFAULT_BROWSER_ROLE_MANAGER_DIALOG)
    }

    private fun openDefaultBrowserSystemSettings() {
        try {
            startActivity(DefaultBrowserSystemSettings.intent())
        } catch (e: ActivityNotFoundException) {
            val errorMessage = getString(R.string.cannotLaunchDefaultAppSettings)
            logcat(WARN) { "$errorMessage: ${e.asLog()}" }
            Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show()
            viewModel.checkQuickSetupSwitchesState()
        }
    }

    private enum class InputMode { SEARCH, CHAT }

    private fun updateAiChatToggleState(
        binding: ContentOnboardingWelcomePageUpdateBinding,
        withAi: Boolean,
        transition: BrandDesignInputScreenPicker.Transition,
    ) {
        with(binding.daxDialogCta.inputScreenContent.inputScreenPicker) {
            setLightMode(appTheme.isLightModeEnabled())
            setSelection(withAi, transition)
            setOnSelectionChangedListener { viewModel.onInputScreenOptionSelected(withAi = it) }
        }
    }

    private fun BrandDesignUpdatePageViewModel.ViewState.currentComparisonChartConfig(): ComparisonChartConfig = when (this.currentDialog) {
        AI_COMPARISON_CHART -> ComparisonChartConfig.Ai
        else -> ComparisonChartConfig.Default
    }

    private fun populateComparisonChart(config: ComparisonChartConfig) {
        with(binding.daxDialogCta.comparisonChartContent) {
            comparisonChartHeaderLeftIcon.setImageResource(config.headerLeftIconRes)
            comparisonChartHeaderLeftIcon.updateLayoutParams {
                width = config.headerLeftIconSizeDp.toPx(comparisonTable.context).toInt()
                height = config.headerLeftIconSizeDp.toPx(comparisonTable.context).toInt()
            }
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                comparisonChartHeaderLeftIconCard.addBottomShadow()
                comparisonChartHeaderRightIconCard.addBottomShadow()
            }
            comparisonChartTitleHidden.text = getString(config.titleRes).preventWidows()
            if (config.headerLeftLabelRes != null) {
                comparisonChartHeaderLabel.text = getString(config.headerLeftLabelRes).preventWidows()
                comparisonChartHeaderLabel.isVisible = true
            } else {
                comparisonChartHeaderLabel.isVisible = false
            }
            comparisonRows.removeAllViews()
            val inflater = LayoutInflater.from(comparisonRows.context)
            config.rows.forEachIndexed { index, row ->
                val rowView = inflater.inflate(
                    R.layout.include_brand_design_comparison_chart_row,
                    comparisonRows,
                    false,
                ) as LinearLayout
                rowView.findViewById<ImageView>(R.id.rowIcon).setImageResource(row.iconRes)
                rowView.findViewById<DaxTextView>(R.id.rowText).text = getString(row.textRes).preventWidows()
                if (index % 2 == 0) {
                    rowView.setBackgroundResource(R.drawable.background_comparison_chart_row_highlighted)
                }
                comparisonRows.addView(rowView)
            }
        }
    }

    private fun comparisonCheckViews(): List<ImageView> =
        binding.daxDialogCta.comparisonChartContent.comparisonRows.children
            .map { it.findViewById<ImageView>(R.id.rowCheck) }
            .toList()

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

        private const val CHECK_ICON_ANIMATION_DURATION = 400L
        private const val CHECK_ICON_FADE_DURATION = 130L
        private const val CHECK_ICON_STAGGER_DELAY = 130L
        private const val CHECK_ICON_OVERSHOOT_TENSION = 2.4f
        private const val CHECK_ICON_AVD_START_DELAY = 180L

        private const val DIALOG_TRANSITION_DURATION = 400L
        private const val TYPING_DELAY_MS = 20L
        private const val TYPING_POST_DELAY_MS = 20L
        private const val INPUT_SCREEN_PREVIEW_SUGGESTION_ANIMATION_DURATION = 500L
        private const val INPUT_SCREEN_PREVIEW_SUGGESTIONS_ANIMATION_DELAY = 500L
        private const val MIN_SCREEN_HEIGHT_FOR_KEYBOARD_DP = 600
        private const val ARROW_TARGET_OFFSET_END_DP = 80

        private const val WING_START_DELAY = 300L
        private const val WING_FADE_IN_DURATION = 150L
        private const val WING_STOP_PROGRESS = 0.5f

        private const val WALKING_DAX_DELAY = 400L
        private const val WALKING_DAX_FADE_DURATION = 100L
        private const val WALKING_DAX_SLIDE_DURATION = 600L
        private const val WALKING_DAX_START_X_DP = 48
        private const val WALKING_DAX_FINAL_X_DP = 22
        private const val WALKING_DAX_MAX_HEIGHT_DP = 274
        private const val WALKING_DAX_MIN_HEIGHT_DP = 174

        private const val DEFAULT_BROWSER_ROLE_MANAGER_DIALOG = 101
        private const val QUICK_SETUP_DEFAULT_BROWSER_ROLE_MANAGER_DIALOG = 102

        private val WELCOME_DAX_INTERPOLATOR = PathInterpolator(0.33f, 0f, 0.67f, 1f)
    }
}
