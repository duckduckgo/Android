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

package com.duckduckgo.app.cta.onboarding_experiment.animation

import android.content.Context
import com.airbnb.lottie.LottieAnimationView
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.omnibar.animations.TrackerLogo
import com.duckduckgo.app.browser.omnibar.animations.TrackerLogo.ImageLogo
import com.duckduckgo.app.browser.omnibar.animations.TrackerLogo.LetterLogo
import com.duckduckgo.app.browser.omnibar.animations.TrackerLogo.StackedLogo
import com.duckduckgo.app.browser.omnibar.animations.TrackersRenderer
import com.duckduckgo.app.cta.onboarding_experiment.animation.OnboardingExperimentStep.BLOCK_TRACKERS
import com.duckduckgo.app.cta.onboarding_experiment.animation.OnboardingExperimentStep.FULL
import com.duckduckgo.app.cta.onboarding_experiment.animation.OnboardingExperimentStep.PRIVACY_SHIELD
import com.duckduckgo.app.cta.onboarding_experiment.animation.OnboardingExperimentStep.SHOW_TRACKERS
import com.duckduckgo.app.trackerdetection.model.Entity
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.mobile.android.ui.store.AppTheme
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import timber.log.Timber
import javax.inject.Inject

/** Public interface for the Onboarding Experiment Animation Helper */
interface OnboardingExperimentAnimationHelper {

    /**
     * This method will setup into [holder] a LottieAnimation based on [OnboardingExperimentStep] state.
     */
    fun startTrackersOnboardingAnimationForStep(
        holder: LottieAnimationView,
        step: OnboardingExperimentStep,
        trackers: List<Entity>
    )
}

@ContributesBinding(AppScope::class)
class LottieOnboardingExperimentAnimationHelper @Inject constructor(val appTheme: AppTheme) : OnboardingExperimentAnimationHelper {

    override fun startTrackersOnboardingAnimationForStep(
        holder: LottieAnimationView,
        step: OnboardingExperimentStep,
        trackers: List<Entity>,
    ) {
        val context = holder.context
        val logos = getLogos(context, trackers)

        with(holder) {
            setCacheComposition(false) // ensure assets are not cached
            setAnimationForStep(holder, step)
            maintainOriginalImageBounds = true
            setImageAssetDelegate(OnboardingExperimentTrackersLottieAssetDelegate(context, logos))
            removeAllAnimatorListeners()
            playAnimation()
        }
    }

    private fun setAnimationForStep(
        holder: LottieAnimationView,
        step: OnboardingExperimentStep,
    ) {
        when (step) {
            SHOW_TRACKERS -> {
                // val animationRawRes = getAnimationRawRes(logos, appTheme) will determine number of trackers
                val res = if (appTheme.isLightModeEnabled()) R.raw.trackers_light_full else R.raw.trackers_dark_full
                holder.setAnimation(res)
                holder.setMinAndMaxFrame(0, 116)
                Timber.i("Onboarding step: TRACKERS")
            }
            BLOCK_TRACKERS -> {
                // val animationRawRes = getAnimationRawRes(logos, appTheme) will determine number of trackers
                val res = if (appTheme.isLightModeEnabled()) R.raw.trackers_light_full else R.raw.trackers_dark_full
                holder.setAnimation(res)
                holder.setMinAndMaxFrame(116, 177)
                Timber.i("Onboarding step: BLOCK_TRACKERS")
            }
            PRIVACY_SHIELD -> {
                val res = if (appTheme.isLightModeEnabled()) R.raw.trackers_light_full else R.raw.trackers_dark_full
                holder.setAnimation(res)
                holder.progress = 1.0f
                Timber.i("Onboarding step: PRIVACY_SHIELD")
            }
            FULL -> {
                val res = if (appTheme.isLightModeEnabled()) R.raw.trackers_light_full else R.raw.trackers_dark_full
                holder.setAnimation(res)
                Timber.i("Onboarding step: FULL_FLOW")
            }
        }
    }

    /**
     * Methods duplicated from BrowserLottieTrackersAnimationHelper
     */

    private fun getLogos(
        context: Context,
        entities: List<Entity>,
    ): List<TrackerLogo> {
        if (context.packageName == null) return emptyList()
        val trackerLogoList = entities
            .asSequence()
            .distinct()
            .take(MAX_LOGOS_SHOWN + 1)
            .sortedWithDisplayNamesStartingWithVowelsToTheEnd()
            .map {
                val resId = TrackersRenderer().networkLogoIcon(context, it.name)
                if (resId == null) {
                    LetterLogo(it.displayName.take(1))
                } else {
                    ImageLogo(resId)
                }
            }.toMutableList()

        return if (trackerLogoList.size <= MAX_LOGOS_SHOWN) {
            trackerLogoList
        } else {
            trackerLogoList.take(MAX_LOGOS_SHOWN)
                .toMutableList()
                .apply { add(StackedLogo()) }
        }
    }

    private fun Sequence<Entity>.sortedWithDisplayNamesStartingWithVowelsToTheEnd(): Sequence<Entity> {
        return sortedWith(compareBy { "AEIOU".contains(it.displayName.take(1)) })
    }

    private fun getAnimationRawRes(
        logos: List<TrackerLogo>,
        theme: AppTheme,
    ): Int {
        val trackers = logos.size
        return when {
            trackers == 1 -> if (theme.isLightModeEnabled()) R.raw.light_trackers_1 else R.raw.dark_trackers_1
            trackers == 2 -> if (theme.isLightModeEnabled()) R.raw.light_trackers_2 else R.raw.dark_trackers_2
            trackers >= 3 -> if (theme.isLightModeEnabled()) R.raw.light_trackers else R.raw.dark_trackers
            else -> TODO()
        }
    }

    companion object {
        private const val MAX_LOGOS_SHOWN = 3
    }
}

enum class OnboardingExperimentStep {
    SHOW_TRACKERS,
    BLOCK_TRACKERS,
    PRIVACY_SHIELD,
    FULL
}
