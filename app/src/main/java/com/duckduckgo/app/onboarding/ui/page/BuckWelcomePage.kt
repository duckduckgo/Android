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

import android.animation.Animator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.View.OnLayoutChangeListener
import android.view.ViewGroup.MarginLayoutParams
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.FrameLayout.LayoutParams
import androidx.annotation.RawRes
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
import com.duckduckgo.app.browser.databinding.ContentOnboardingWelcomePageBuckBinding
import com.duckduckgo.app.onboarding.ui.page.LottieOnboardingAnimationSpec.AnimationPhase.*
import com.duckduckgo.app.onboarding.ui.page.PreOnboardingDialogType.ADDRESS_BAR_POSITION
import com.duckduckgo.app.onboarding.ui.page.PreOnboardingDialogType.COMPARISON_CHART
import com.duckduckgo.app.onboarding.ui.page.PreOnboardingDialogType.INITIAL
import com.duckduckgo.app.onboarding.ui.page.PreOnboardingDialogType.INITIAL_REINSTALL_USER
import com.duckduckgo.app.onboarding.ui.page.PreOnboardingDialogType.SKIP_ONBOARDING_OPTION
import com.duckduckgo.app.onboarding.ui.page.WelcomePageViewModel.Command.Finish
import com.duckduckgo.app.onboarding.ui.page.WelcomePageViewModel.Command.OnboardingSkipped
import com.duckduckgo.app.onboarding.ui.page.WelcomePageViewModel.Command.SetAddressBarPositionOptions
import com.duckduckgo.app.onboarding.ui.page.WelcomePageViewModel.Command.ShowAddressBarPositionDialog
import com.duckduckgo.app.onboarding.ui.page.WelcomePageViewModel.Command.ShowComparisonChart
import com.duckduckgo.app.onboarding.ui.page.WelcomePageViewModel.Command.ShowDefaultBrowserDialog
import com.duckduckgo.app.onboarding.ui.page.WelcomePageViewModel.Command.ShowInitialDialog
import com.duckduckgo.app.onboarding.ui.page.WelcomePageViewModel.Command.ShowInitialReinstallUserDialog
import com.duckduckgo.app.onboarding.ui.page.WelcomePageViewModel.Command.ShowSkipOnboardingOption
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.ui.store.AppTheme
import com.duckduckgo.common.ui.view.TypeAnimationTextView
import com.duckduckgo.common.ui.view.toPx
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.FragmentViewModelFactory
import com.duckduckgo.common.utils.extensions.html
import com.duckduckgo.common.utils.extensions.preventWidows
import com.duckduckgo.di.scopes.FragmentScope
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@InjectWith(FragmentScope::class)
class BuckWelcomePage : OnboardingPageFragment(R.layout.content_onboarding_welcome_page_buck) {

    @Inject
    lateinit var viewModelFactory: FragmentViewModelFactory

    @Inject
    lateinit var appBuildConfig: AppBuildConfig

    @Inject
    lateinit var appTheme: AppTheme

    private val binding: ContentOnboardingWelcomePageBuckBinding by viewBinding()
    private val viewModel by lazy {
        ViewModelProvider(this, viewModelFactory)[WelcomePageViewModel::class.java]
    }

    private var welcomeAnimation: ViewPropertyAnimatorCompat? = null
    private var daxDialogAnimationStarted = false
    private var onboardingAnimationViewOnLayoutChangeListener: OnLayoutChangeListener? = null

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

            option1Icon.isSelected = defaultOption
            option2Icon.isSelected = !defaultOption

            option1Body.isSelected = defaultOption
            option2Body.isSelected = !defaultOption
        }
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        ViewCompat.setOnApplyWindowInsetsListener(binding.daxDialogCta.root) { v, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val margin = v.resources.getDimensionPixelSize(com.duckduckgo.mobile.android.R.dimen.keyline_4)
            v.updateLayoutParams<MarginLayoutParams> { topMargin = statusBarHeight + margin }
            insets
        }

        setBackgroundRes(
            if (appTheme.isLightModeEnabled()) {
                R.drawable.buck_onboarding_background_small_light
            } else {
                R.drawable.buck_onboarding_background_small_dark
            },
        )

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

                    binding.daxDialogCta.cardView.animateEntrance(
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

                            binding.daxDialogCta.initial.dialogTitle.startTypingAnimation(titleText, afterAnimation = {
                                binding.daxDialogCta.initial.dialogBody.startTypingAnimation(
                                    descriptionText,
                                    afterAnimation = { afterTypingAnimation() },
                                )
                            })
                        },
                    )

                    playAnimation(
                        animation = LottieOnboardingAnimationSpec.POPUP,
                        phase = ENTER,
                    )
                }

                INITIAL -> {
                    binding.daxDialogCta.root.isVisible = true
                    binding.daxDialogCta.initial.root.isVisible = true

                    binding.daxDialogCta.primaryCta.text = it.getString(R.string.preOnboardingDaxDialog1Button)
                    binding.daxDialogCta.primaryCta.alpha = MIN_ALPHA
                    binding.daxDialogCta.secondaryCta.isVisible = false

                    binding.daxDialogCta.cardView.animateEntrance(
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

                            binding.daxDialogCta.initial.dialogTitle.startTypingAnimation(titleText, afterAnimation = {
                                binding.daxDialogCta.initial.dialogBody.startTypingAnimation(
                                    descriptionText,
                                    afterAnimation = { afterTypingAnimation() },
                                )
                            })
                        },
                    )

                    playAnimation(
                        animation = LottieOnboardingAnimationSpec.POPUP,
                        phase = ENTER,
                    )
                }

                COMPARISON_CHART -> {
                    playExitAnimation(
                        onAnimationEnd = {
                            resetDialogContentVisibility()
                            binding.daxDialogCta.secondaryCta.isVisible = false
                            TransitionManager.beginDelayedTransition(binding.daxDialogCta.cardView, AutoTransition())
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

                            playAnimation(
                                animation = LottieOnboardingAnimationSpec.WING,
                                phase = ENTER,
                            )
                        },
                    )
                }

                SKIP_ONBOARDING_OPTION -> {
                    playExitAnimation(
                        onAnimationEnd = {
                            resetDialogContentVisibility()
                            TransitionManager.beginDelayedTransition(binding.daxDialogCta.cardView, AutoTransition())
                            binding.daxDialogCta.skipOnboarding.root.isVisible = true

                            val titleText = it.getString(R.string.preOnboardingDaxDialog3Title)
                            val descriptionText = it.getString(R.string.preOnboardingDaxDialog3Text)

                            binding.daxDialogCta.skipOnboarding.dialogTitleInvisible.text = titleText.html(context = it)
                            binding.daxDialogCta.skipOnboarding.descriptionInvisible.text = descriptionText.html(context = it)

                            binding.daxDialogCta.primaryCta.text = it.getString(R.string.preOnboardingDaxDialog3Button)
                            binding.daxDialogCta.primaryCta.alpha = MIN_ALPHA
                            binding.daxDialogCta.secondaryCta.text = it.getString(R.string.preOnboardingDaxDialog3SecondaryButton)
                            binding.daxDialogCta.secondaryCta.alpha = MIN_ALPHA

                            afterTypingAnimation = {
                                binding.daxDialogCta.skipOnboarding.dialogTitle.finishAnimation()
                                binding.daxDialogCta.skipOnboarding.description.finishAnimation()
                                binding.daxDialogCta.primaryCta.setOnClickListener { viewModel.onPrimaryCtaClicked(SKIP_ONBOARDING_OPTION) }
                                binding.daxDialogCta.secondaryCta.setOnClickListener { viewModel.onSecondaryCtaClicked(SKIP_ONBOARDING_OPTION) }

                                if (binding.daxDialogCta.skipOnboarding.description.text.isEmpty()) {
                                    binding.daxDialogCta.skipOnboarding.description.text = descriptionText.html(context = it)
                                }

                                binding.daxDialogCta.primaryCta.animate().alpha(MAX_ALPHA).setDuration(ANIMATION_DURATION)
                                binding.daxDialogCta.secondaryCta.animate().alpha(MAX_ALPHA).setDuration(ANIMATION_DURATION)
                            }

                            binding.daxDialogCta.skipOnboarding.dialogTitle.startTypingAnimation(titleText, afterAnimation = {
                                binding.daxDialogCta.skipOnboarding.description.startTypingAnimation(
                                    descriptionText,
                                    afterAnimation = { afterTypingAnimation() },
                                )
                            })
                        },
                    )
                }

                ADDRESS_BAR_POSITION -> {
                    playExitAnimation(
                        onAnimationEnd = {
                            resetDialogContentVisibility()
                            binding.daxDialogCta.secondaryCta.isVisible = false
                            TransitionManager.beginDelayedTransition(binding.daxDialogCta.cardView, AutoTransition())
                            binding.daxDialogCta.addressBarPosition.root.isVisible = true

                            setAddressBarPositionOptions(true)
                            binding.daxDialogCta.primaryCta.text = it.getString(R.string.preOnboardingAddressBarOkButton)
                            binding.daxDialogCta.primaryCta.alpha = MIN_ALPHA

                            val contentViews = with(binding.daxDialogCta.addressBarPosition) { listOf(option1, option2) }
                            contentViews.forEach { view -> view.alpha = MIN_ALPHA }
                            val titleText = getString(R.string.preOnboardingAddressBarTitle).preventWidows()

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

                            playAnimation(LottieOnboardingAnimationSpec.POPUP_SMALL)
                        },
                    )
                }
            }
            binding.sceneBg.setOnClickListener { afterTypingAnimation() }
            binding.daxDialogCta.cardContainer.setOnClickListener { afterTypingAnimation() }
        }
    }

    private fun resetDialogContentVisibility() {
        binding.daxDialogCta
            .run { listOf(initial, skipOnboarding, comparisonChart, addressBarPosition) }
            .forEach { it.root.isVisible = false }
    }

    private fun startWelcomeAnimation() {
        binding.welcomeDialog.animateEntrance()

        playAnimation(
            animation = LottieOnboardingAnimationSpec.WALK_WAVE,
            phase = ENTER,
            onAnimationEnd = {
                binding.longDescriptionContainer.setOnClickListener(null)
                startDaxDialogAnimation()
            },
        )

        binding.longDescriptionContainer.run {
            setOnClickListener {
                setOnClickListener(null)
                startDaxDialogAnimation()
            }
        }
    }

    private fun startDaxDialogAnimation() {
        if (daxDialogAnimationStarted) return
        daxDialogAnimationStarted = true

        var welcomeContentFadedAway = false
        var welcomeDaxFadedAway = false

        fun onAnimationFinished() {
            if (welcomeContentFadedAway && welcomeDaxFadedAway) {
                viewModel.loadDaxDialog()
            }
        }

        ViewCompat.animate(binding.welcomeContent as View)
            .alpha(MIN_ALPHA)
            .setDuration(ANIMATION_DURATION)
            .withStartAction {
                playAnimation(
                    animation = LottieOnboardingAnimationSpec.WALK_WAVE,
                    phase = EXIT,
                    onAnimationEnd = {
                        welcomeDaxFadedAway = true
                        onAnimationFinished()
                    },
                )
            }
            .withEndAction {
                welcomeContentFadedAway = true
                onAnimationFinished()
            }
    }

    private fun scheduleTypingAnimation(textView: TypeAnimationTextView, text: String, afterAnimation: () -> Unit = {}) {
        viewLifecycleOwner.lifecycleScope.launch {
            delay(ANIMATION_DURATION)
            textView.startTypingAnimation(text, afterAnimation = afterAnimation)
        }
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

    private fun setBackgroundRes(backgroundRes: Int) {
        binding.sceneBg.setImageResource(backgroundRes)
    }

    private fun playAnimation(
        animation: LottieOnboardingAnimationSpec,
        phase: LottieOnboardingAnimationSpec.AnimationPhase? = null,
        onAnimationEnd: () -> Unit = {},
    ) = with(binding.onboardingPageAnimation) {
        if (tag != animation) {
            setAnimation(animation.resId)
            tag = animation
        }

        val (minProgress, maxProgress) = when (phase) {
            ENTER -> 0f to animation.enterPhaseMaxProgress
            EXIT -> animation.enterPhaseMaxProgress to 1f
            null -> 0f to 1f
        }
        setMinAndMaxProgress(minProgress, maxProgress)

        if (onboardingAnimationViewOnLayoutChangeListener != null) {
            removeOnLayoutChangeListener(onboardingAnimationViewOnLayoutChangeListener)
        }

        val parentView = parent as FrameLayout

        onboardingAnimationViewOnLayoutChangeListener = OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val layoutCustomization = animation.layoutCustomization(parentView)
            updateLayoutParams<LayoutParams> {
                marginStart = layoutCustomization.marginStart
                marginEnd = layoutCustomization.marginEnd
                topMargin = layoutCustomization.marginTop
                bottomMargin = layoutCustomization.marginBottom
                gravity = layoutCustomization.gravity
            }
            translationX = layoutCustomization.translationX
        }

        parentView.addOnLayoutChangeListener(onboardingAnimationViewOnLayoutChangeListener)

        addAnimatorListener(
            object : Animator.AnimatorListener {
                override fun onAnimationStart(animation: Animator) = Unit

                override fun onAnimationEnd(animation: Animator) {
                    removeAnimatorListener(this)
                    onAnimationEnd()
                }

                override fun onAnimationCancel(animation: Animator) = Unit
                override fun onAnimationRepeat(animation: Animator) = Unit
            },
        )

        playAnimation()
    }

    private fun playExitAnimation(onAnimationEnd: () -> Unit = {}) {
        val animation = binding.onboardingPageAnimation.tag as? LottieOnboardingAnimationSpec

        if (animation == null || binding.onboardingPageAnimation.progress > 0.999f) {
            // There is no exit animation or it has already finished
            onAnimationEnd()
            return
        }

        playAnimation(
            animation = animation,
            phase = EXIT,
            onAnimationEnd = onAnimationEnd,
        )
    }

    companion object {
        private const val MIN_ALPHA = 0f
        private const val MAX_ALPHA = 1f
        private const val ANIMATION_DURATION = 400L

        private const val DEFAULT_BROWSER_ROLE_MANAGER_DIALOG = 101
    }
}

private enum class LottieOnboardingAnimationSpec(
    @RawRes val resId: Int,
    val enterPhaseMaxProgress: Float = 1.0f,
    val layoutCustomization: (FrameLayout) -> LayoutCustomization,
) {
    WALK_WAVE(
        resId = R.raw.ob_1_walk_wave,
        enterPhaseMaxProgress = 0.92f,
        layoutCustomization = { parentView ->
            val baseMarginHorizontal = 48.toPx()
            val targetTopMargin = 16.toPx()
            val targetBottomMargin = 48.toPx()
            val containerHeight = parentView.height - targetTopMargin - targetBottomMargin
            val containerWidth = parentView.width - baseMarginHorizontal * 2
            val aspectRatioLimit = 6f / 5f

            val targetHeight = containerHeight.coerceAtMost((containerWidth * aspectRatioLimit).toInt())
            val targetWidth = targetHeight // the animation asset has 1:1 aspect ratio

            // apply negative horizontal margins to stretch the animation view beyond screen width
            val targetHorizontalMargin = -(targetWidth - containerWidth) / 2

            LayoutCustomization(
                marginStart = targetHorizontalMargin,
                marginEnd = targetHorizontalMargin,
                marginBottom = targetBottomMargin,
                marginTop = targetTopMargin,
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
                translationX = -0.15f * targetWidth,
            )
        },
    ),
    POPUP(
        resId = R.raw.ob_2_popup,
        enterPhaseMaxProgress = 0.75f,
        layoutCustomization = LayoutCustomization(
            marginEnd = 16.toPx(),
        ),
    ),
    WING(
        resId = R.raw.ob_3_wing,
        enterPhaseMaxProgress = 0.8f,
        layoutCustomization = LayoutCustomization(
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
        ),
    ),
    POPUP_SMALL(
        resId = R.raw.ob_4_popup,
        layoutCustomization = LayoutCustomization(
            marginEnd = 16.toPx(),
        ),
    ),
    ;

    constructor(
        @RawRes resId: Int,
        enterPhaseMaxProgress: Float = 1.0f,
        layoutCustomization: LayoutCustomization = LayoutCustomization(),
    ) : this(resId, enterPhaseMaxProgress, { _ -> layoutCustomization })

    data class LayoutCustomization(
        val marginStart: Int = 0,
        val marginEnd: Int = 0,
        val marginTop: Int = 0,
        val marginBottom: Int = 0,
        val gravity: Int = Gravity.BOTTOM or Gravity.START,
        val translationX: Float = 0f,
    )

    enum class AnimationPhase { ENTER, EXIT }
}
