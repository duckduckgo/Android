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
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.fragment.app.DialogFragment
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ViewDaxDialogExperimentBinding
import com.duckduckgo.app.cta.onboarding_experiment.animation.LottieOnboardingExperimentAnimationHelper
import com.duckduckgo.app.cta.onboarding_experiment.animation.OnboardingExperimentStep.BLOCK_TRACKERS
import com.duckduckgo.app.cta.onboarding_experiment.animation.OnboardingExperimentStep.PRIVACY_SHIELD
import com.duckduckgo.app.cta.onboarding_experiment.animation.OnboardingExperimentStep.SHOW_TRACKERS
import com.duckduckgo.app.global.extensions.html
import com.duckduckgo.app.trackerdetection.model.Entity
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.mobile.android.ui.view.DaxDialog
import com.duckduckgo.mobile.android.ui.view.DaxDialogListener
import com.duckduckgo.mobile.android.ui.view.gone
import com.duckduckgo.mobile.android.ui.view.show
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import dagger.android.support.AndroidSupportInjection
import timber.log.Timber
import timber.log.Timber.Forest
import javax.inject.Inject

@InjectWith(FragmentScope::class)
class TypewriterExperimentDaxDialog : DialogFragment(R.layout.view_dax_dialog_experiment), DaxDialog {

    @Inject
    lateinit var animatorHelper: LottieOnboardingExperimentAnimationHelper

    private val binding: ViewDaxDialogExperimentBinding by viewBinding()

    private var daxText: String = ""
    private var primaryButtonText: String = ""
    private var daxDialogListener: DaxDialogListener? = null
    private var trackers: List<Entity> = listOf()

    override fun setDaxDialogListener(listener: DaxDialogListener?) {
        daxDialogListener = listener
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
        val window = dialog.window
        val attributes = window?.attributes

        attributes?.gravity = Gravity.BOTTOM
        window?.attributes = attributes
        window?.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        )
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
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
        return com.duckduckgo.mobile.android.R.style.Widget_DuckDuckGo_DaxDialog
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.attributes?.dimAmount = 0f
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
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
        animatorHelper.startTrackersOnboardingAnimationForStep(binding.onboardingTrackersBlockedAnim, SHOW_TRACKERS, trackers)
        binding.dialogText.startTypingAnimation(daxText, true)
    }

    private fun setDialog() {
        context?.let {
            with(binding) {
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
                dialogText.finishAnimation()
                dismiss()
            }
        }
    }

    private fun setStepTwoView() {
        binding.onboardingStepTwoText.show()
        binding.primaryCta.gone()
        binding.onboardingStepOneText.gone()
        with(binding.onboardingTrackersBlockedAnim) {
            animatorHelper.startTrackersOnboardingAnimationForStep(this, BLOCK_TRACKERS, trackers)
            addAnimatorListener(
                object : Animator.AnimatorListener {
                    override fun onAnimationRepeat(animation: Animator) {}
                    override fun onAnimationCancel(animation: Animator) {}
                    override fun onAnimationStart(animation: Animator) {}
                    override fun onAnimationEnd(animation: Animator) {
                        setStepThreeView()
                    }
                },
            )
        }
    }

    private fun setStepThreeView() {
        binding.cardView.gone()
        binding.logo.gone()
        with(binding.onboardingTrackersBlockedAnim) {
            animatorHelper.startTrackersOnboardingAnimationForStep(this, PRIVACY_SHIELD, trackers)
            addAnimatorListener(
                object : Animator.AnimatorListener {
                    override fun onAnimationRepeat(animation: Animator) {}
                    override fun onAnimationCancel(animation: Animator) {}
                    override fun onAnimationStart(animation: Animator) {}
                    override fun onAnimationEnd(animation: Animator) {
                        dismiss()
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
