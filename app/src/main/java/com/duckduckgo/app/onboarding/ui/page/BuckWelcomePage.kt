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
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RawRes
import androidx.core.view.ViewCompat
import androidx.core.view.ViewPropertyAnimatorCompat
import androidx.core.view.WindowCompat
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
import com.duckduckgo.common.ui.view.toPx
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.FragmentViewModelFactory
import com.duckduckgo.common.utils.extensions.html
import com.duckduckgo.di.scopes.FragmentScope
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

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

    private val requestPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { permissionGranted ->
        if (permissionGranted) {
            viewModel.notificationRuntimePermissionGranted()
        }
        if (view?.windowVisibility == View.VISIBLE) {
            startDaxDialogAnimation(ANIMATION_DELAY_AFTER_NOTIFICATIONS_PERMISSIONS_HANDLED)
        }
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

    @SuppressLint("InlinedApi")
    private fun requestNotificationsPermissions() {
        if (appBuildConfig.sdkInt >= android.os.Build.VERSION_CODES.TIRAMISU) {
            viewModel.notificationRuntimePermissionRequested()
            requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startDaxDialogAnimation()
        }
    }

    private fun configureDaxCta(onboardingDialogType: PreOnboardingDialogType) {
        context?.let {
            viewModel.onDialogShown(onboardingDialogType)
            when (onboardingDialogType) {
                INITIAL_REINSTALL_USER -> {
                    binding.daxDialogCta.root.isVisible = true
                    binding.daxDialogCta.initial.root.isVisible = true

                    binding.daxDialogCta.primaryCta.text = it.getString(R.string.preOnboardingDaxDialog1Button)
                    binding.daxDialogCta.primaryCta.setOnClickListener { viewModel.onPrimaryCtaClicked(INITIAL_REINSTALL_USER) }
                    binding.daxDialogCta.secondaryCta.text = it.getString(R.string.preOnboardingDaxDialog1SecondaryButton)
                    binding.daxDialogCta.secondaryCta.setOnClickListener { viewModel.onSecondaryCtaClicked(INITIAL_REINSTALL_USER) }
                    binding.daxDialogCta.secondaryCta.isVisible = true

                    binding.daxDialogCta.cardView.animateEntrance()

                    playAnimation(
                        animation = LottieOnboardingAnimationSpec.POPUP,
                        phase = ENTER,
                    )
                }

                INITIAL -> {
                    binding.daxDialogCta.root.isVisible = true
                    binding.daxDialogCta.initial.root.isVisible = true

                    binding.daxDialogCta.primaryCta.text = it.getString(R.string.preOnboardingDaxDialog1Button)
                    binding.daxDialogCta.primaryCta.setOnClickListener { viewModel.onPrimaryCtaClicked(INITIAL) }
                    binding.daxDialogCta.secondaryCta.isVisible = false

                    binding.daxDialogCta.cardView.animateEntrance()

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

                            binding.daxDialogCta.primaryCta.text = it.getString(R.string.preOnboardingDaxDialog2Button)
                            binding.daxDialogCta.primaryCta.setOnClickListener { viewModel.onPrimaryCtaClicked(COMPARISON_CHART) }

                            playAnimation(
                                animation = LottieOnboardingAnimationSpec.WING,
                                phase = ENTER,
                            )
                        },
                    )
                }

                SKIP_ONBOARDING_OPTION -> {
                    resetDialogContentVisibility()
                    TransitionManager.beginDelayedTransition(binding.daxDialogCta.cardView, AutoTransition())
                    binding.daxDialogCta.skipOnboarding.root.isVisible = true

                    binding.daxDialogCta.skipOnboarding.description.text = it.getString(R.string.highlightsPreOnboardingDaxDialog3Text)
                        .html(context = it)

                    binding.daxDialogCta.primaryCta.text = it.getString(R.string.preOnboardingDaxDialog3Button)
                    binding.daxDialogCta.primaryCta.setOnClickListener { viewModel.onPrimaryCtaClicked(SKIP_ONBOARDING_OPTION) }
                    binding.daxDialogCta.secondaryCta.text = it.getString(R.string.preOnboardingDaxDialog3SecondaryButton)
                    binding.daxDialogCta.secondaryCta.setOnClickListener { viewModel.onSecondaryCtaClicked(SKIP_ONBOARDING_OPTION) }
                }

                ADDRESS_BAR_POSITION -> {
                    playExitAnimation(
                        onAnimationEnd = {
                            resetDialogContentVisibility()
                            binding.daxDialogCta.secondaryCta.isVisible = false
                            TransitionManager.beginDelayedTransition(binding.daxDialogCta.cardView, AutoTransition())
                            binding.daxDialogCta.addressBarPosition.root.isVisible = true

                            setAddressBarPositionOptions(true)
                            binding.daxDialogCta.primaryCta.text = it.getString(R.string.highlightsPreOnboardingAddressBarOkButton)
                            binding.daxDialogCta.primaryCta.setOnClickListener { viewModel.onPrimaryCtaClicked(ADDRESS_BAR_POSITION) }
                            binding.daxDialogCta.addressBarPosition.option1.setOnClickListener {
                                viewModel.onAddressBarPositionOptionSelected(true)
                            }
                            binding.daxDialogCta.addressBarPosition.option2.setOnClickListener {
                                viewModel.onAddressBarPositionOptionSelected(false)
                            }
                            binding.daxDialogCta.addressBarPosition.root.animate().alpha(MAX_ALPHA).duration = ANIMATION_DURATION

                            playAnimation(LottieOnboardingAnimationSpec.POPUP_SMALL)
                        },
                    )
                }
            }
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
            onAnimationEnd = { requestNotificationsPermissions() },
        )
    }

    private fun startDaxDialogAnimation(animationDelay: Long = ANIMATION_DELAY) {
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
            .setStartDelay(animationDelay)
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

        updateLayoutParams<FrameLayout.LayoutParams> {
            marginStart = animation.viewLayoutParamsOverride.marginStart
            marginEnd = animation.viewLayoutParamsOverride.marginEnd
            topMargin = animation.viewLayoutParamsOverride.marginTop
            bottomMargin = animation.viewLayoutParamsOverride.marginBottom
            gravity = animation.viewLayoutParamsOverride.gravity
        }

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
        val animation = (binding.onboardingPageAnimation.tag as? LottieOnboardingAnimationSpec) ?: return
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
        private const val ANIMATION_DELAY = 1400L
        private const val ANIMATION_DELAY_AFTER_NOTIFICATIONS_PERMISSIONS_HANDLED = 800L

        private const val DEFAULT_BROWSER_ROLE_MANAGER_DIALOG = 101
    }
}

private enum class LottieOnboardingAnimationSpec(
    @RawRes val resId: Int,
    val enterPhaseMaxProgress: Float = 1.0f,
    val viewLayoutParamsOverride: LottieAnimationViewLayoutParamsOverride = LottieAnimationViewLayoutParamsOverride(),
) {
    WALK_WAVE(
        resId = R.raw.ob_1_walk_wave,
        enterPhaseMaxProgress = 0.92f,
        viewLayoutParamsOverride = LottieAnimationViewLayoutParamsOverride(
            marginStart = (-112).toPx(),
            marginEnd = 16.toPx(),
            marginBottom = 48.toPx(),
        ),
    ),
    POPUP(
        resId = R.raw.ob_2_popup,
        enterPhaseMaxProgress = 0.75f,
        viewLayoutParamsOverride = LottieAnimationViewLayoutParamsOverride(
            marginEnd = 16.toPx(),
        ),
    ),
    WING(
        resId = R.raw.ob_3_wing,
        enterPhaseMaxProgress = 0.8f,
        viewLayoutParamsOverride = LottieAnimationViewLayoutParamsOverride(
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
        ),
    ),
    POPUP_SMALL(
        resId = R.raw.ob_4_popup,
        viewLayoutParamsOverride = LottieAnimationViewLayoutParamsOverride(
            marginEnd = 16.toPx(),
        ),
    ),
    ;

    data class LottieAnimationViewLayoutParamsOverride(
        val marginStart: Int = 0,
        val marginEnd: Int = 0,
        val marginTop: Int = 0,
        val marginBottom: Int = 0,
        val gravity: Int = Gravity.BOTTOM or Gravity.START,
    )

    enum class AnimationPhase { ENTER, EXIT }
}
