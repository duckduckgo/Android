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

package com.duckduckgo.app.onboarding.ui.page.configdriven.binders

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.drawable.AnimatedVectorDrawable
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.IncludeBrandDesignComparisonChartBinding
import com.duckduckgo.app.onboarding.ui.page.ComparisonChartConfig
import com.duckduckgo.app.onboarding.ui.page.configdriven.BindScope
import com.duckduckgo.app.onboarding.ui.page.configdriven.ContentConfig
import com.duckduckgo.app.onboarding.ui.page.configdriven.ContentHandle
import com.duckduckgo.app.onboarding.ui.page.configdriven.DialogBinder
import com.duckduckgo.app.onboarding.ui.page.configdriven.DialogTitleController
import com.duckduckgo.common.ui.view.addBottomShadow
import com.duckduckgo.common.ui.view.text.DaxTextView
import com.duckduckgo.common.ui.view.toPx
import com.duckduckgo.common.utils.extensions.preventWidows
import com.duckduckgo.mobile.android.R as CommonR

/**
 * Stateless. Ported from BrandDesignUpdateWelcomePage:
 *  - row/header population: populateComparisonChart :3002-3036
 *  - check-icon stagger: playCheckIconAnimation :2746-2789, snap state snapCheckIconsToFinalState :2737-2744
 *
 * Simplification: legacy's playCheckIconAnimation also fades in the shared stepIndicator view alongside the
 * check icons. stepIndicator lives outside every include (declared directly on the CTA root, like
 * primary/secondaryCta) and DialogConfig models it as a top-level `stepIndicator: StepProgress?` — so per the
 * "binders never touch shared views" rule that animation is left for the engine, not ported here.
 */
class ComparisonChartBinder(private val binding: IncludeBrandDesignComparisonChartBinding) : DialogBinder<ContentConfig.ComparisonChart> {

    override val view: View = binding.root

    override fun bind(content: ContentConfig.ComparisonChart, scope: BindScope): ContentHandle {
        val context = binding.root.context
        val config = content.config

        binding.comparisonChartHeaderLeftIcon.setImageResource(config.headerLeftIconRes)
        binding.comparisonChartHeaderLeftIcon.updateLayoutParams {
            width = config.headerLeftIconSizeDp.toPx(context).toInt()
            height = config.headerLeftIconSizeDp.toPx(context).toInt()
        }
        if (Build.VERSION.SDK_INT >= 28) {
            binding.comparisonChartHeaderLeftIconCard.addBottomShadow()
            binding.comparisonChartHeaderRightIconCard.addBottomShadow()
        }
        if (config.headerLeftLabelRes != null) {
            binding.comparisonChartHeaderLabel.text = context.getString(config.headerLeftLabelRes).preventWidows()
            binding.comparisonChartHeaderLabel.isVisible = true
        } else {
            binding.comparisonChartHeaderLabel.isVisible = false
        }
        populateRows(config)

        val title = DialogTitleController(binding.comparisonChartTitle, binding.comparisonChartTitleHidden)
        title.set(content.title.resolve(context))

        return ContentHandle(
            title = title,
            fadeTargets = listOf(binding.comparisonTable),
            entrance = { afterFade { checkIconStaggerAnimator() } },
        )
    }

    private fun populateRows(config: ComparisonChartConfig) {
        val context = binding.root.context
        binding.comparisonRows.removeAllViews()
        val inflater = LayoutInflater.from(binding.comparisonRows.context)
        config.rows.forEachIndexed { index, row ->
            val rowView = inflater.inflate(
                R.layout.include_brand_design_comparison_chart_row,
                binding.comparisonRows,
                false,
            ) as LinearLayout
            rowView.findViewById<ImageView>(R.id.rowIcon).setImageResource(row.iconRes)
            rowView.findViewById<DaxTextView>(R.id.rowText).text = context.getString(row.textRes)
            if (index % 2 == 0) {
                rowView.setBackgroundResource(R.drawable.background_comparison_chart_row_highlighted)
            }
            binding.comparisonRows.addView(rowView)
        }
    }

    private fun comparisonCheckViews(): List<ImageView> =
        binding.comparisonRows.children
            .map { it.findViewById<ImageView>(R.id.rowCheck) }
            .toList()

    /**
     * One AnimatorSet the engine owns end-to-end: per row, fades/scales the check icon in with an overshoot
     * interpolator, plus a zero-duration trigger animator (see [avdStartTrigger]) that starts the check
     * icon's AnimatedVectorDrawable at the same relative delay legacy scheduled via `postDelayed` (:2775-2779).
     * Each row's listener forces the exact legacy snap state (:2737-2744) in onAnimationEnd, which Android
     * invokes on natural completion AND on cancel() — so end()/cancel() from the engine both land views in
     * final visible state even if a row's animator was still pending on its startDelay.
     */
    private fun checkIconStaggerAnimator(): Animator {
        val overshoot = OvershootInterpolator(CHECK_ICON_OVERSHOOT_TENSION)
        val checkViews = comparisonCheckViews()

        // Reset trimPathEnd up-front: the alpha fade-in completes before the AVD start, so a stale
        // trimPathEnd=1 from a previous run would render the tick fully drawn during the gap.
        checkViews.forEach { checkView ->
            (checkView.drawable?.mutate() as? AnimatedVectorDrawable)?.reset()
        }

        val rowAnimators: List<Animator> = checkViews.mapIndexed { index, checkView ->
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
                    avdStartTrigger(checkView),
                )
                startDelay = index * CHECK_ICON_STAGGER_DELAY
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        checkView.alpha = 1f
                        checkView.scaleX = 1f
                        checkView.scaleY = 1f
                        checkView.setImageResource(CommonR.drawable.ic_check_green_24)
                    }
                })
            }
        }

        return AnimatorSet().apply { playTogether(rowAnimators) }
    }

    /** Zero-duration animator used purely as a delayed trigger for AnimatedVectorDrawable.start(). */
    private fun avdStartTrigger(checkView: ImageView): Animator =
        ValueAnimator.ofInt(0, 1).apply {
            startDelay = CHECK_ICON_AVD_START_DELAY
            duration = 0L
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    (checkView.drawable as? AnimatedVectorDrawable)?.start()
                }
            })
        }

    private companion object {
        const val CHECK_ICON_ANIMATION_DURATION = 400L
        const val CHECK_ICON_FADE_DURATION = 130L
        const val CHECK_ICON_STAGGER_DELAY = 130L
        const val CHECK_ICON_OVERSHOOT_TENSION = 2.4f
        const val CHECK_ICON_AVD_START_DELAY = 180L
    }
}
