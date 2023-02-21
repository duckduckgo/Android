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
import androidx.annotation.DrawableRes
import com.airbnb.lottie.LottieAnimationView
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.omnibar.animations.TrackerLogo
import com.duckduckgo.app.browser.omnibar.animations.TrackerLogo.ImageLogo
import com.duckduckgo.app.browser.omnibar.animations.TrackerLogo.LetterLogo
import com.duckduckgo.app.browser.omnibar.animations.TrackerLogo.StackedLogo
import com.duckduckgo.app.browser.omnibar.animations.TrackersRenderer
import com.duckduckgo.app.cta.onboarding_experiment.animation.OnboardingExperimentStep.BLOCK_TRACKERS
import com.duckduckgo.app.cta.onboarding_experiment.animation.OnboardingExperimentStep.PRIVACY_SHIELD
import com.duckduckgo.app.cta.onboarding_experiment.animation.OnboardingExperimentStep.SHOW_TRACKERS
import com.duckduckgo.app.cta.onboarding_experiment.animation.OnboardingExperimentStep.SHOW_TRACKERS_EXPANDED
import com.duckduckgo.app.cta.onboarding_experiment.animation.OnboardingExperimentStep.TRACKERS_HAND_LOOP
import com.duckduckgo.app.trackerdetection.model.Entity
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.mobile.android.ui.store.AppTheme
import com.squareup.anvil.annotations.ContributesBinding
import java.util.*
import javax.inject.Inject
import timber.log.Timber

/** Public interface for the Onboarding Experiment Animation Helper */
interface OnboardingExperimentAnimationHelper {

    /**
     * This method will setup into [holder] a LottieAnimation based on [OnboardingExperimentStep] state.
     */
    fun startTrackersOnboardingAnimationForStep(
        holder: LottieAnimationView,
        step: OnboardingExperimentStep,
        trackers: List<Entity>,
    )
}

@ContributesBinding(FragmentScope::class)
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
            setAnimationForStep(holder, step, logos.size)
            maintainOriginalImageBounds = true
            setImageAssetDelegate(OnboardingExperimentTrackersLottieAssetDelegate(context, logos))
            removeAllAnimatorListeners()
            playAnimation()
        }
    }

    private fun setAnimationForStep(
        holder: LottieAnimationView,
        step: OnboardingExperimentStep,
        numberOfTrackers: Int,
    ) {
        when (step) {
            SHOW_TRACKERS -> {
                holder.setAnimation(getAnimationRawRes(numberOfTrackers))
                holder.setMinAndMaxFrame(0, 45)
                Timber.i("Onboarding step: TRACKERS")
            }
            SHOW_TRACKERS_EXPANDED -> {
                holder.setAnimation(getAnimationRawRes(numberOfTrackers))
                holder.setMinAndMaxFrame(46, 71)
                Timber.i("Onboarding step: TRACKERS")
            }
            TRACKERS_HAND_LOOP -> {
                holder.setMinAndMaxFrame(72, 119)
                Timber.i("Onboarding step: TRACKERS HAND LOOP")
            }
            BLOCK_TRACKERS -> {
                holder.setMinAndMaxFrame(120, 199)
                Timber.i("Onboarding step: BLOCK_TRACKERS")
            }
            PRIVACY_SHIELD -> {
                holder.setAnimation(getAnimationRawRes(numberOfTrackers))
                holder.setMinFrame(200)
                Timber.i("Onboarding step: PRIVACY_SHIELD")
            }
        }
    }

    /**
     * Methods duplicated from BrowserLottieTrackersAnimationHelper
     */

    private fun getAnimationRawRes(numberOfTrackers: Int): Int {
        return when (numberOfTrackers) {
            1 -> if (appTheme.isLightModeEnabled()) R.raw.tracker_onboarding_1_light_full else R.raw.tracker_onboarding_1_dark_full
            2 -> if (appTheme.isLightModeEnabled()) R.raw.tracker_onboarding_2_light_full else R.raw.tracker_onboarding_2_dark_full
            else -> if (appTheme.isLightModeEnabled()) R.raw.tracker_onboarding_3_light_full else R.raw.tracker_onboarding_3_dark_full
        }
    }

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
                val resId = networkFullColorLogoIcon(context, it.name)
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

    @DrawableRes
    private fun networkFullColorLogoIcon( // fixme move it to TrackersRender class in case experiment is implemented
        context: Context,
        networkName: String,
    ): Int? {
        return TrackersRenderer().networkIcon(context, networkName, "network_full_color_logo_")
    }

    companion object {
        private const val MAX_LOGOS_SHOWN = 3
    }
}

enum class OnboardingExperimentStep {
    SHOW_TRACKERS,
    SHOW_TRACKERS_EXPANDED,
    TRACKERS_HAND_LOOP,
    BLOCK_TRACKERS,
    PRIVACY_SHIELD,
}
