/*
 * Copyright (c) 2025 DuckDuckGo
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

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.PathInterpolator
import androidx.annotation.DrawableRes
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.animation.doOnEnd
import androidx.core.view.ViewCompat
import androidx.core.view.ViewPropertyAnimatorCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ContentOnboardingWelcomePageBbBinding
import com.duckduckgo.app.onboarding.ui.page.BbWelcomePage.BbOnboardingBackgroundSceneManager.BackgroundTile
import com.duckduckgo.app.onboarding.ui.page.BbWelcomePage.BbOnboardingBackgroundSceneManager.BackgroundTile.*
import com.duckduckgo.app.onboarding.ui.page.PreOnboardingDialogType.ADDRESS_BAR_POSITION
import com.duckduckgo.app.onboarding.ui.page.PreOnboardingDialogType.COMPARISON_CHART
import com.duckduckgo.app.onboarding.ui.page.PreOnboardingDialogType.INITIAL
import com.duckduckgo.app.onboarding.ui.page.PreOnboardingDialogType.INITIAL_REINSTALL_USER
import com.duckduckgo.app.onboarding.ui.page.PreOnboardingDialogType.SKIP_ONBOARDING_OPTION
import com.duckduckgo.app.onboarding.ui.page.WelcomePageViewModel.Command.*
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.ui.store.AppTheme
import com.duckduckgo.common.ui.view.TypeAnimationTextView
import com.duckduckgo.common.ui.view.toPx
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.FragmentViewModelFactory
import com.duckduckgo.common.utils.extensions.html
import com.duckduckgo.di.scopes.FragmentScope
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@InjectWith(FragmentScope::class)
class BbWelcomePage : OnboardingPageFragment(R.layout.content_onboarding_welcome_page_bb) {

    @Inject
    lateinit var viewModelFactory: FragmentViewModelFactory

    @Inject
    lateinit var appBuildConfig: AppBuildConfig

    @Inject
    lateinit var appTheme: AppTheme

    private val binding: ContentOnboardingWelcomePageBbBinding by viewBinding()
    private val viewModel by lazy {
        ViewModelProvider(this, viewModelFactory)[WelcomePageViewModel::class.java]
    }

    private var welcomeAnimation: ViewPropertyAnimatorCompat? = null
    private var daxDialogAnimaationStarted = false
    private var backgroundSceneManager: BbOnboardingBackgroundSceneManager? = null

    @SuppressLint("SourceLockedOrientationActivity")
    override fun onAttach(context: Context) {
        super.onAttach(context)
        requireActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel.commands.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).onEach {
            when (it) {
                is ShowInitialReinstallUserDialog -> configureDaxCta(INITIAL_REINSTALL_USER)
                is ShowInitialDialog -> configureDaxCta(INITIAL)
                is ShowComparisonChart -> configureDaxCta(COMPARISON_CHART)
                is ShowSkipOnboardingOption -> configureDaxCta(SKIP_ONBOARDING_OPTION)
                is ShowDefaultBrowserDialog -> showDefaultBrowserDialog(it.intent)
                is ShowAddressBarPositionDialog -> configureDaxCta(ADDRESS_BAR_POSITION)
                is Finish -> onContinuePressed()
                is OnboardingSkipped -> onSkipPressed()
                is SetAddressBarPositionOptions -> setAddressBarPositionOptions(it.defaultOption)
            }
        }.launchIn(lifecycleScope)
    }

    private fun setAddressBarPositionOptions(defaultOption: Boolean) {
        with(binding.daxDialogCta.addressBarPosition) {
            option1.isSelected = defaultOption
            option2.isSelected = !defaultOption

            option1Switch.isChecked = defaultOption
            option2Switch.isChecked = !defaultOption

            option1Image.isSelected = defaultOption
            option2Image.isSelected = !defaultOption

            option1Body.isSelected = defaultOption
            option2Body.isSelected = !defaultOption
        }
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        ViewCompat.setOnApplyWindowInsetsListener(binding.statusBarGuideline) { _, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            binding.statusBarGuideline.setGuidelineBegin(statusBarHeight)
            insets
        }

        backgroundSceneManager = BbOnboardingBackgroundSceneManager(
            view1 = binding.background1,
            view2 = binding.background2,
            lightModeEnabled = appTheme.isLightModeEnabled(),
        ).also { it.initializeView() }

        startWelcomeAnimation()
    }

    override fun onResume() {
        super.onResume()
        applyFullScreenFlags()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        welcomeAnimation?.cancel()
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

    private fun configureDaxCta(onboardingDialogType: PreOnboardingDialogType) {
        context?.let {
            var afterTypingAnimation: () -> Unit = {}
            viewModel.onDialogShown(onboardingDialogType)
            when (onboardingDialogType) {
                INITIAL_REINSTALL_USER -> {
                    binding.daxDialogCta.root.isVisible = true
                    binding.daxDialogCta.initial.root.isVisible = true

                    binding.daxDialogCta.primaryCta.text = it.getString(R.string.preOnboardingDaxDialog1Button)
                    binding.daxDialogCta.primaryCta.alpha = MIN_ALPHA
                    binding.daxDialogCta.secondaryCta.text = it.getString(R.string.preOnboardingDaxDialog1SecondaryButton)
                    binding.daxDialogCta.secondaryCta.isVisible = true
                    binding.daxDialogCta.secondaryCta.alpha = MIN_ALPHA

                    showDaxDialogCardView(
                        onAnimationEnd = {
                            val titleText = getString(R.string.highlightsPreOnboardingDaxDialog1TitleBuck)
                            val descriptionText = getString(R.string.highlightsPreOnboardingDaxDialog1DescriptionBuck)

                            afterTypingAnimation = {
                                binding.daxDialogCta.initial.dialogTitle.finishAnimation()
                                binding.daxDialogCta.initial.dialogBody.finishAnimation()
                                binding.daxDialogCta.primaryCta.setOnClickListener { viewModel.onPrimaryCtaClicked(INITIAL_REINSTALL_USER) }
                                binding.daxDialogCta.secondaryCta.setOnClickListener { viewModel.onSecondaryCtaClicked(INITIAL_REINSTALL_USER) }

                                if (binding.daxDialogCta.initial.dialogBody.text.isEmpty()) {
                                    binding.daxDialogCta.initial.dialogBody.text = descriptionText
                                }

                                binding.daxDialogCta.primaryCta.animate().alpha(MAX_ALPHA).setDuration(ANIMATION_DURATION)
                                binding.daxDialogCta.secondaryCta.animate().alpha(MAX_ALPHA).setDuration(ANIMATION_DURATION)
                            }

                            binding.daxDialogCta.initial.dialogTitle.startTypingAnimation(
                                titleText,
                                afterAnimation = {
                                    binding.daxDialogCta.initial.dialogBody.startTypingAnimation(
                                        descriptionText,
                                        afterAnimation = { afterTypingAnimation() },
                                    )
                                },
                            )
                        },
                    )
                }

                INITIAL -> {
                    binding.daxDialogCta.root.isVisible = true
                    binding.daxDialogCta.initial.root.isVisible = true

                    binding.daxDialogCta.primaryCta.text = it.getString(R.string.preOnboardingDaxDialog1Button)
                    binding.daxDialogCta.primaryCta.alpha = MIN_ALPHA
                    binding.daxDialogCta.secondaryCta.isVisible = false

                    showDaxDialogCardView(
                        onAnimationEnd = {
                            val titleText = getString(R.string.highlightsPreOnboardingDaxDialog1TitleBuck)
                            val descriptionText = getString(R.string.highlightsPreOnboardingDaxDialog1DescriptionBuck)

                            afterTypingAnimation = {
                                binding.daxDialogCta.initial.dialogTitle.finishAnimation()
                                binding.daxDialogCta.initial.dialogBody.finishAnimation()
                                binding.daxDialogCta.primaryCta.setOnClickListener { viewModel.onPrimaryCtaClicked(INITIAL_REINSTALL_USER) }

                                if (binding.daxDialogCta.initial.dialogBody.text.isEmpty()) {
                                    binding.daxDialogCta.initial.dialogBody.text = descriptionText
                                }

                                binding.daxDialogCta.primaryCta.animate().alpha(MAX_ALPHA).setDuration(ANIMATION_DURATION)
                            }

                            binding.daxDialogCta.initial.dialogTitle.startTypingAnimation(
                                titleText,
                                afterAnimation = {
                                    binding.daxDialogCta.initial.dialogBody.startTypingAnimation(
                                        descriptionText,
                                        afterAnimation = { afterTypingAnimation() },
                                    )
                                },
                            )
                        },
                    )
                }

                COMPARISON_CHART -> {
                    resetDialogContentVisibility()
                    binding.daxDialogCta.secondaryCta.isVisible = false
                    TransitionManager.beginDelayedTransition(
                        binding.daxDialogCta.cardView,
                        AutoTransition(),
                    )
                    binding.daxDialogCta.comparisonChart.root.isVisible = true

                    val titleText = it.getString(R.string.highlightsPreOnboardingDaxDialog2TitleBuck)
                    binding.daxDialogCta.comparisonChart.titleInvisible.text = titleText.html(context = it)
                    binding.daxDialogCta.primaryCta.text = it.getString(R.string.preOnboardingDaxDialog2Button)
                    binding.daxDialogCta.primaryCta.alpha = MIN_ALPHA

                    val comparisonChartViews = binding.daxDialogCta.comparisonChart.root.children
                        .filter { view -> view != binding.daxDialogCta.comparisonChart.titleContainer }

                    comparisonChartViews.forEach { view -> view.alpha = MIN_ALPHA }

                    afterTypingAnimation = {
                        comparisonChartViews.forEach { view ->
                            view.animate().alpha(MAX_ALPHA).setDuration(ANIMATION_DURATION)
                        }

                        binding.daxDialogCta.primaryCta.animate().alpha(MAX_ALPHA).setDuration(ANIMATION_DURATION)
                        binding.daxDialogCta.primaryCta.setOnClickListener { viewModel.onPrimaryCtaClicked(COMPARISON_CHART) }
                    }

                    scheduleTypingAnimation(binding.daxDialogCta.comparisonChart.title, titleText) { afterTypingAnimation() }
                    backgroundSceneManager?.transitionToNextTile(expectedTile = TILE_03)
                }

                SKIP_ONBOARDING_OPTION -> {
                    resetDialogContentVisibility()
                    TransitionManager.beginDelayedTransition(
                        binding.daxDialogCta.cardView,
                        AutoTransition(),
                    )
                    binding.daxDialogCta.skipOnboarding.root.isVisible = true

                    val titleText = it.getString(R.string.highlightsPreOnboardingDaxDialog3Title)
                    val descriptionText = it.getString(R.string.highlightsPreOnboardingDaxDialog3Text)

                    binding.daxDialogCta.skipOnboarding.dialogTitleInvisible.text = titleText.html(context = it)
                    binding.daxDialogCta.skipOnboarding.descriptionInvisible.text = descriptionText.html(context = it)

                    binding.daxDialogCta.primaryCta.text = it.getString(R.string.preOnboardingDaxDialog3Button)
                    binding.daxDialogCta.primaryCta.alpha = MIN_ALPHA
                    binding.daxDialogCta.secondaryCta.text = it.getString(R.string.preOnboardingDaxDialog3SecondaryButton)
                    binding.daxDialogCta.secondaryCta.alpha = MIN_ALPHA

                    afterTypingAnimation = {
                        binding.daxDialogCta.skipOnboarding.dialogTitle.finishAnimation()
                        binding.daxDialogCta.skipOnboarding.description.finishAnimation()
                        binding.daxDialogCta.primaryCta.setOnClickListener {
                            viewModel.onPrimaryCtaClicked(SKIP_ONBOARDING_OPTION)
                        }
                        binding.daxDialogCta.secondaryCta.setOnClickListener {
                            viewModel.onSecondaryCtaClicked(SKIP_ONBOARDING_OPTION)
                        }

                        if (binding.daxDialogCta.skipOnboarding.description.text.isEmpty()) {
                            binding.daxDialogCta.skipOnboarding.description.text = descriptionText.html(context = it)
                        }

                        binding.daxDialogCta.primaryCta.animate().alpha(MAX_ALPHA).setDuration(ANIMATION_DURATION)
                        binding.daxDialogCta.secondaryCta.animate().alpha(MAX_ALPHA).setDuration(ANIMATION_DURATION)
                    }

                    binding.daxDialogCta.skipOnboarding.dialogTitle.startTypingAnimation(
                        titleText,
                        afterAnimation = {
                            binding.daxDialogCta.skipOnboarding.description.startTypingAnimation(
                                descriptionText,
                                afterAnimation = { afterTypingAnimation() },
                            )
                        },
                    )
                }

                ADDRESS_BAR_POSITION -> {
                    resetDialogContentVisibility()
                    binding.daxDialogCta.secondaryCta.isVisible = false
                    TransitionManager.beginDelayedTransition(
                        binding.daxDialogCta.cardView,
                        AutoTransition(),
                    )
                    binding.daxDialogCta.addressBarPosition.root.isVisible = true

                    setAddressBarPositionOptions(true)
                    binding.daxDialogCta.primaryCta.text = it.getString(R.string.highlightsPreOnboardingAddressBarOkButton)
                    binding.daxDialogCta.primaryCta.alpha = MIN_ALPHA

                    val contentViews = with(binding.daxDialogCta.addressBarPosition) { listOf(option1, option2) }
                    contentViews.forEach { view -> view.alpha = MIN_ALPHA }
                    val titleText = getString(R.string.highlightsPreOnboardingAddressBarTitle)

                    afterTypingAnimation = {
                        binding.daxDialogCta.addressBarPosition.option1.setOnClickListener {
                            viewModel.onAddressBarPositionOptionSelected(true)
                        }
                        binding.daxDialogCta.addressBarPosition.option2.setOnClickListener {
                            viewModel.onAddressBarPositionOptionSelected(false)
                        }

                        binding.daxDialogCta.primaryCta.setOnClickListener { viewModel.onPrimaryCtaClicked(ADDRESS_BAR_POSITION) }

                        contentViews.forEach { view ->
                            view.animate().alpha(MAX_ALPHA).setDuration(ANIMATION_DURATION)
                        }
                        binding.daxDialogCta.primaryCta.animate().alpha(MAX_ALPHA).setDuration(ANIMATION_DURATION)
                    }

                    scheduleTypingAnimation(binding.daxDialogCta.addressBarPosition.dialogTitle, titleText) { afterTypingAnimation() }
                    backgroundSceneManager?.transitionToNextTile(expectedTile = TILE_04)
                }
            }
            backgroundSceneManager?.setBackgroundClickListener(afterTypingAnimation)
            binding.daxDialogCta.cardContainer.setOnClickListener { afterTypingAnimation() }
        }
    }

    private fun resetDialogContentVisibility() {
        binding.daxDialogCta
            .run { listOf(initial, skipOnboarding, comparisonChart, addressBarPosition) }
            .forEach { it.root.isVisible = false }
    }

    private fun startWelcomeAnimation() {
        binding.daxLogo.setMaxProgress(0.9f)
        binding.daxLogo.playAnimation()

        binding.welcomeTitle.translationY = 32f.toPx()
        binding.welcomeTitle.animate()
            .alpha(MAX_ALPHA)
            .translationY(0f)
            .setDuration(800)
            .setStartDelay(100)
            .setInterpolator(STANDARD_EASING_INTERPOLATOR)
            .withEndAction {
                startDaxDialogAnimation()
            }

        backgroundSceneManager?.startWelcomeAnimation()
    }

    private fun startDaxDialogAnimation() {
        if (daxDialogAnimaationStarted) return
        daxDialogAnimaationStarted = true

        val animationDelay = 1.seconds
        val animationDuration = SCENE_TRANSITION_DURATION

        ConstraintSet().apply {
            clone(binding.longDescriptionContainer)
            // update dax logo constraints to set it up for transition using its start+top margins
            clear(R.id.daxLogo, ConstraintSet.END)
            connect(
                R.id.daxLogo,
                ConstraintSet.START,
                ConstraintSet.PARENT_ID,
                ConstraintSet.START,
                binding.daxLogo.x.toInt(), // adjust start margin to maintain current position
            )

            // update title text constraints to disconnect it from dax logo
            clear(R.id.welcomeTitle, ConstraintSet.TOP)
            connect(
                R.id.welcomeTitle,
                ConstraintSet.TOP,
                R.id.statusBarGuideline,
                ConstraintSet.BOTTOM,
                (binding.welcomeTitle.y - binding.statusBarGuideline.y).toInt(),
            )

            applyTo(binding.longDescriptionContainer)
        }

        ValueAnimator.ofFloat(0f, 1f)
            .apply {
                duration = animationDuration.inWholeMilliseconds
                startDelay = animationDelay.inWholeMilliseconds
                interpolator = STANDARD_EASING_INTERPOLATOR

                val daxLogoLayoutParams = binding.daxLogo.layoutParams as MarginLayoutParams
                val initialMarginStart = daxLogoLayoutParams.marginStart
                val initialMarginTop = daxLogoLayoutParams.topMargin
                val targetMarginStart = 16f.toPx()
                val targetMarginTop = 16f.toPx()

                addUpdateListener { animator ->
                    val progress = animator.animatedValue as Float

                    binding.daxLogo.updateLayoutParams<MarginLayoutParams> {
                        marginStart =
                            (initialMarginStart + (targetMarginStart - initialMarginStart) * progress).toInt()
                        topMargin =
                            (initialMarginTop + (targetMarginTop - initialMarginTop) * progress).toInt()
                    }
                }
            }
            .start()

        binding.welcomeTitle.animate()
            .alpha(MIN_ALPHA)
            .setDuration(animationDuration.inWholeMilliseconds)
            .setStartDelay(animationDelay.inWholeMilliseconds)
            .withStartAction {
                backgroundSceneManager?.transitionToNextTile(expectedTile = TILE_02)
            }
            .withEndAction {
                viewModel.loadDaxDialog()
            }
    }

    private fun showDaxDialogCardView(onAnimationEnd: () -> Unit) {
        val animationDuration = 600.milliseconds
        val rotationDelay = 67.milliseconds
        val scaleValues = floatArrayOf(0.14f, 1.03f, 0.99f, 1.0f)
        val rotationValues = floatArrayOf(5f, -1f, 0.5f, 0f)

        val dialogCardView = binding.daxDialogCta.cardView
            .apply {
                pivotX = 0f
                pivotY = 0f
                scaleX = scaleValues.first()
                scaleY = scaleValues.first()
                rotation = rotationValues.first()
            }

        val scaleXAnimator = ObjectAnimator.ofFloat(dialogCardView, "scaleX", *scaleValues)
        val scaleYAnimator = ObjectAnimator.ofFloat(dialogCardView, "scaleY", *scaleValues)

        val rotationAnimator = ObjectAnimator.ofFloat(dialogCardView, "rotation", *rotationValues)
            .apply { startDelay = rotationDelay.inWholeMilliseconds }

        AnimatorSet().run {
            playTogether(scaleXAnimator, scaleYAnimator, rotationAnimator)
            setDuration(animationDuration.inWholeMilliseconds)
            doOnEnd { onAnimationEnd() }
            start()
        }
    }

    private fun scheduleTypingAnimation(textView: TypeAnimationTextView, text: String, afterAnimation: () -> Unit = {}) {
        textView.postDelayed(
            { textView.startTypingAnimation(text, afterAnimation = afterAnimation) },
            ANIMATION_DURATION,
        )
    }

    private fun showDefaultBrowserDialog(intent: Intent) {
        startActivityForResult(intent, DEFAULT_BROWSER_ROLE_MANAGER_DIALOG)
    }

    private fun applyFullScreenFlags() {
        activity?.window?.apply {
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            WindowCompat.setDecorFitsSystemWindows(this, false)
            statusBarColor = Color.TRANSPARENT
            navigationBarColor = Color.TRANSPARENT
        }
        ViewCompat.requestApplyInsets(binding.longDescriptionContainer)
    }

    companion object {
        private const val MIN_ALPHA = 0f
        private const val MAX_ALPHA = 1f
        private const val ANIMATION_DURATION = 400L
        private val SCENE_TRANSITION_DURATION = 800.milliseconds

        private const val DEFAULT_BROWSER_ROLE_MANAGER_DIALOG = 101

        // https://m3.material.io/styles/motion/easing-and-duration/tokens-specs#7e37d374-0c1b-4007-8187-6f29bb1fb3e7
        private val STANDARD_EASING_INTERPOLATOR = PathInterpolator(0.2f, 0f, 0f, 1f)
    }

    private class BbOnboardingBackgroundSceneManager(
        view1: View,
        view2: View,
        val lightModeEnabled: Boolean,
    ) {
        private val screenWidth = view1.context.resources.displayMetrics.widthPixels.toFloat()
        private var currentBackgroundView = view1
        private var nextBackgroundView = view2
        private var transitionInProgress = false
        private var currentTile = TILE_01

        private val nextTile: BackgroundTile?
            get() {
                return with(BackgroundTile.entries) {
                    val nextTileIndex = indexOf(currentTile) + 1
                    if (nextTileIndex in indices) get(nextTileIndex) else null
                }
            }

        fun initializeView() {
            currentBackgroundView.setBackgroundResource(getBackgroundResource(currentTile))
            currentBackgroundView.scaleX = 1.0f
            currentBackgroundView.scaleY = 1.0f
            currentBackgroundView.isVisible = true
            nextTile?.let { nextBackgroundView.setBackgroundResource(getBackgroundResource(it)) }
            nextBackgroundView.translationX = screenWidth
            nextBackgroundView.isVisible = false
        }

        fun startWelcomeAnimation() {
            if (transitionInProgress) return
            transitionInProgress = true

            currentBackgroundView.animate()
                .scaleX(BACKGROUND_TARGET_SCALE)
                .scaleY(BACKGROUND_TARGET_SCALE)
                .setDuration(SCENE_TRANSITION_DURATION.inWholeMilliseconds)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction {
                    transitionInProgress = false
                }

            nextBackgroundView.scaleX = BACKGROUND_TARGET_SCALE
            nextBackgroundView.scaleY = BACKGROUND_TARGET_SCALE
            nextBackgroundView.translationX = screenWidth * BACKGROUND_TARGET_SCALE
        }

        fun transitionToNextTile(expectedTile: BackgroundTile) {
            if (transitionInProgress || nextTile != expectedTile) return

            currentTile = expectedTile
            transitionInProgress = true
            nextBackgroundView.isVisible = true

            val currentSlideOut = ObjectAnimator.ofFloat(
                currentBackgroundView,
                "translationX",
                0f,
                -screenWidth * BACKGROUND_TARGET_SCALE,
            )

            val nextSlideIn = ObjectAnimator.ofFloat(
                nextBackgroundView,
                "translationX",
                screenWidth * BACKGROUND_TARGET_SCALE,
                0f,
            )

            // Execute animation
            AnimatorSet().apply {
                playTogether(currentSlideOut, nextSlideIn)
                duration = SCENE_TRANSITION_DURATION.inWholeMilliseconds

                doOnEnd { completeTransition() }
            }.start()
        }

        fun setBackgroundClickListener(onClick: () -> Unit) {
            currentBackgroundView.setOnClickListener { onClick() }
            nextBackgroundView.setOnClickListener { onClick() }
        }

        private fun completeTransition() {
            val temp = currentBackgroundView
            currentBackgroundView = nextBackgroundView
            nextBackgroundView = temp

            // Hide the off-screen view
            nextBackgroundView.isVisible = false

            // Prepare next tile if available
            nextTile?.let { tile ->
                nextBackgroundView.setBackgroundResource(getBackgroundResource(tile))
                nextBackgroundView.translationX = screenWidth * BACKGROUND_TARGET_SCALE
            }

            transitionInProgress = false
        }

        @DrawableRes
        private fun getBackgroundResource(tile: BackgroundTile): Int {
            return if (lightModeEnabled) tile.drawableLight else tile.drawableDark
        }

        enum class BackgroundTile(
            @DrawableRes val drawableLight: Int,
            @DrawableRes val drawableDark: Int,
        ) {
            TILE_01(
                drawableLight = R.drawable.bb_onboarding_background_01_light,
                drawableDark = R.drawable.bb_onboarding_background_01_dark,
            ),
            TILE_02(
                drawableLight = R.drawable.bb_onboarding_background_02_light,
                drawableDark = R.drawable.bb_onboarding_background_02_dark,
            ),
            TILE_03(
                drawableLight = R.drawable.bb_onboarding_background_03_light,
                drawableDark = R.drawable.bb_onboarding_background_03_dark,
            ),
            TILE_04(
                drawableLight = R.drawable.bb_onboarding_background_04_light,
                drawableDark = R.drawable.bb_onboarding_background_04_dark,
            ),
            ;
        }

        private companion object {
            const val BACKGROUND_TARGET_SCALE = 1.15f
        }
    }
}
