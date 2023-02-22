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

package com.duckduckgo.app.cta.onboarding_experiment

import android.animation.Animator
import android.animation.ValueAnimator
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import androidx.fragment.app.DialogFragment
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ViewDaxDialogExperimentBinding
import com.duckduckgo.app.cta.onboarding_experiment.animation.LottieOnboardingExperimentAnimationHelper
import com.duckduckgo.app.cta.onboarding_experiment.animation.OnboardingExperimentStep
import com.duckduckgo.app.cta.onboarding_experiment.animation.OnboardingExperimentStep.BLOCK_TRACKERS
import com.duckduckgo.app.cta.onboarding_experiment.animation.OnboardingExperimentStep.PRIVACY_SHIELD
import com.duckduckgo.app.cta.onboarding_experiment.animation.OnboardingExperimentStep.SHOW_TRACKERS
import com.duckduckgo.app.cta.onboarding_experiment.animation.OnboardingExperimentStep.SHOW_TRACKERS_EXPANDED
import com.duckduckgo.app.cta.onboarding_experiment.animation.OnboardingExperimentStep.TRACKERS_HAND_LOOP
import com.duckduckgo.app.global.extensions.html
import com.duckduckgo.app.trackerdetection.model.Entity
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.mobile.android.ui.view.DaxDialog
import com.duckduckgo.mobile.android.ui.view.DaxDialogListener
import com.duckduckgo.mobile.android.ui.view.gone
import com.duckduckgo.mobile.android.ui.view.show
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject

interface DaxDialogExperimentListener : DaxDialogListener {
    fun onPrivacyShieldClick()
}

@InjectWith(FragmentScope::class)
class TypewriterExperimentDaxDialog : DialogFragment(R.layout.view_dax_dialog_experiment), DaxDialog {

    @Inject
    lateinit var animatorHelper: LottieOnboardingExperimentAnimationHelper

    private val binding: ViewDaxDialogExperimentBinding by viewBinding()

    private var daxText: String = ""
    private var primaryButtonText: String = ""
    private var daxDialogListener: DaxDialogExperimentListener? = null
    private var trackers: List<Entity> = listOf()

    override fun setDaxDialogListener(listener: DaxDialogListener?) {
        daxDialogListener = listener as DaxDialogExperimentListener?
    }

    fun setBlockedTrackers(blockedTrackers: List<Entity>) {
        trackers = blockedTrackers
    }

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            statusBarColor = Color.TRANSPARENT
        }
        return dialog
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.run {
            if (containsKey(ARG_DAX_TEXT)) {
                getString(ARG_DAX_TEXT)?.let { daxText = it }
            }
            if (containsKey(ARG_PRIMARY_CTA_TEXT)) {
                getString(ARG_PRIMARY_CTA_TEXT)?.let { primaryButtonText = it }
            }
        }
    }

    override fun getTheme(): Int {
        return com.duckduckgo.mobile.android.R.style.Widget_DuckDuckGo_DaxDialogFragment
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        setStepOneView()
    }

    override fun onDismiss(dialog: DialogInterface) {
        if (activity != null) {
            binding.dialogText.cancelAnimation()
            daxDialogListener?.onDaxDialogDismiss()
        }
        daxDialogListener = null
        super.onDismiss(dialog)
    }

    private fun setStepOneView() {
        setDialog()
        setListeners()
        setLottieViewAnimation(SHOW_TRACKERS, this::setExpandedTrackersAndShowDialog)
    }

    private fun setExpandedTrackersAndShowDialog() {
        setLottieViewAnimation(SHOW_TRACKERS_EXPANDED, this::setHandLoop)
        binding.cardView.animate().alpha(1.0f).duration = 1000
        binding.logo.animate().alpha(1.0f).duration = 1000
        binding.dialogText.startTypingAnimation(daxText, true)
    }

    private fun setDialog() {
        context?.let {
            with(binding) {
                cardView.alpha = 0.0f
                logo.alpha = 0.0f
                hiddenText.text = daxText.html(it)
                primaryCta.text = primaryButtonText
                dialogText.typingDelayInMs = DEFAULT_TYPING_DELAY
            }
        } ?: dismiss()
    }

    private fun setListeners() {
        with(binding) {
            primaryCta.setOnClickListener {
                dialogText.cancelAnimation()
                setStepTwoView()
            }
            dialogContainer.setOnClickListener {
                if (dialogText.hasAnimationFinished()) {
                    dismiss()
                } else {
                    dialogText.finishAnimation()
                }
            }
            cardView.setOnClickListener {
                dialogText.finishAnimation()
            }
            onboardingTrackersBlockedAnim.setOnClickListener {
                dialogText.finishAnimation()
            }
        }
    }

    private fun setHandLoop() {
        setLottieViewAnimation(TRACKERS_HAND_LOOP, loop = true)
    }

    private fun setStepTwoView() {
        binding.onboardingStepTwoText.show()
        binding.primaryCta.gone()
        binding.onboardingStepOneText.gone()
        setLottieViewAnimation(BLOCK_TRACKERS, this::setStepThreeView)
    }

    private fun setStepThreeView() {
        binding.cardView.gone()
        binding.logo.gone()
        binding.onboardingTrackersBlockedAnim.setOnClickListener { daxDialogListener?.onPrivacyShieldClick() }
        setLottieViewAnimation(PRIVACY_SHIELD, this::dismiss)
    }

    private fun setLottieViewAnimation(
        step: OnboardingExperimentStep,
        onAnimationEnd: (() -> Unit)? = null,
        loop: Boolean = false,
    ) {
        with(binding.onboardingTrackersBlockedAnim) {
            animatorHelper.startTrackersOnboardingAnimationForStep(binding.onboardingTrackersBlockedAnim, step, trackers)
            repeatCount = if (loop) ValueAnimator.INFINITE else 0
            addAnimatorListener(
                object : Animator.AnimatorListener {
                    override fun onAnimationRepeat(animation: Animator) {}
                    override fun onAnimationCancel(animation: Animator) {}
                    override fun onAnimationStart(animation: Animator) {}
                    override fun onAnimationEnd(animation: Animator) {
                        onAnimationEnd?.invoke()
                    }
                },
            )
        }
    }

    companion object {
        fun newInstance(
            daxText: String,
            primaryButtonText: String,
        ): TypewriterExperimentDaxDialog {
            return TypewriterExperimentDaxDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_DAX_TEXT, daxText)
                    putString(ARG_PRIMARY_CTA_TEXT, primaryButtonText)
                }
            }
        }

        private const val DEFAULT_TYPING_DELAY: Long = 20
        private const val ARG_DAX_TEXT = "daxText"
        private const val ARG_PRIMARY_CTA_TEXT = "primaryCtaText"
    }
}
