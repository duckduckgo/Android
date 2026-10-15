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

package com.duckduckgo.app.browser.omnibar.animations.addressbar

import android.animation.Animator
import android.animation.Animator.AnimatorListener
import android.animation.AnimatorSet
import android.content.Context
import android.transition.Scene
import android.transition.Slide
import android.transition.Transition
import android.transition.Transition.TransitionListener
import android.transition.TransitionManager
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.annotation.RawRes
import androidx.core.animation.addListener
import androidx.core.animation.doOnEnd
import androidx.core.content.ContextCompat
import com.airbnb.lottie.LottieAnimationView
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.trackerdetection.model.Entity
import com.duckduckgo.common.ui.store.AppBrandDesignUpdateToggles
import com.duckduckgo.common.ui.store.AppTheme
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.view.text.DaxTextView
import com.duckduckgo.common.ui.view.toPx
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.FragmentScope
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.core.transition.doOnEnd as doOnTransitionEnd
import com.duckduckgo.mobile.android.R as DesignSystemR

@ContributesBinding(FragmentScope::class)
class BrowserLottieTrackersAnimatorHelper @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val theme: AppTheme,
    private val addressBarTrackersAnimator: AddressBarTrackersAnimator,
    private val commonAddressBarAnimationHelper: CommonAddressBarAnimationHelper,
    private val appBrandDesignUpdateToggles: AppBrandDesignUpdateToggles,
) : BrowserTrackersAnimatorHelper {

    private var listener: TrackersAnimatorListener? = null

    private lateinit var cookieView: LottieAnimationView
    private lateinit var cookieScene: ViewGroup
    private lateinit var cookieViewBackground: View
    private var cookieCosmeticHide: Boolean = false

    private var enqueueCookiesAnimation = false
    private var isCookiesAnimationRunning = false
    private var hasCookiesAnimationBeenCanceled = false
    private var cookieUseLightAnimation: Boolean? = null

    private val conflatedJob = ConflatedJob()
    private val coroutineScope = CoroutineScope(SupervisorJob() + dispatcherProvider.main())

    lateinit var firstScene: Scene
    lateinit var secondScene: Scene

    private lateinit var adBlockingView: LottieAnimationView
    private lateinit var adBlockingScene: ViewGroup
    private lateinit var adBlockingViewBackground: View
    private var isAdBlockingAnimationRunning = false
    private var hasAdBlockingAnimationBeenCanceled = false
    private lateinit var adBlockingFirstScene: Scene
    private lateinit var adBlockingSecondScene: Scene

    override fun startAddressBarTrackersAnimation(
        context: Context,
        sceneRoot: ViewGroup,
        animatedIconBackgroundView: View,
        addressBarTrackersBlockedAnimationShieldIcon: LottieAnimationView,
        omnibarViews: List<View>,
        shieldViews: List<View>,
        entities: List<Entity>?,
        customBackgroundColor: Int?,
        useSoftwareRenderingMode: Boolean,
    ) {
        if (isCookiesAnimationRunning || isAdBlockingAnimationRunning || addressBarTrackersAnimator.isAnimationRunning) return

        addressBarTrackersAnimator.startAnimation(
            context = context,
            sceneRoot = sceneRoot,
            animatedIconBackgroundView = animatedIconBackgroundView,
            addressBarTrackersBlockedAnimationShieldIcon = addressBarTrackersBlockedAnimationShieldIcon,
            omnibarViews = omnibarViews,
            shieldViews = shieldViews,
            entities = entities,
            customBackgroundColor = customBackgroundColor,
            useSoftwareRenderingMode = useSoftwareRenderingMode,
            onAnimationComplete = {
                conflatedJob +=
                    coroutineScope.launch {
                        delay(DELAY_BETWEEN_ANIMATIONS_DURATION)
                        tryToStartCookiesAnimation(omnibarViews + shieldViews)
                    }
            },
        )
    }

    override fun createCookiesAnimation(
        context: Context,
        omnibarViews: List<View>,
        shieldViews: List<View>,
        cookieBackground: View,
        cookieAnimationView: LottieAnimationView,
        cookieScene: ViewGroup,
        cookieCosmeticHide: Boolean,
        enqueueCookieAnimation: Boolean,
        useLightAnimation: Boolean?,
    ) {
        this.cookieScene = cookieScene
        this.cookieViewBackground = cookieBackground
        this.cookieView = cookieAnimationView
        this.cookieCosmeticHide = cookieCosmeticHide
        this.cookieUseLightAnimation = useLightAnimation

        if (enqueueCookieAnimation) {
            this.enqueueCookiesAnimation = true
        } else if (!addressBarTrackersAnimator.isAnimationRunning) {
            startCookiesAnimation(omnibarViews + shieldViews)
        } else {
            enqueueCookiesAnimation = false
        }
    }

    override fun createAdBlockingAnimation(
        context: Context,
        omnibarViews: List<View>,
        shieldViews: List<View>,
        badgeBackground: View,
        badgeAnimationView: LottieAnimationView,
        badgeScene: ViewGroup,
        icon: Int,
        text: Int,
    ) {
        // Ad-blocking is exclusive: cancel any in-flight tracker/cookie animation before showing.
        conflatedJob.cancel()
        addressBarTrackersAnimator.cancelAnimation()
        stopCookiesAnimation()

        this.adBlockingScene = badgeScene
        this.adBlockingViewBackground = badgeBackground
        this.adBlockingView = badgeAnimationView
        startAdBlockingAnimation(context, omnibarViews + shieldViews, icon, text)
    }

    private fun startAdBlockingAnimation(
        context: Context,
        omnibarViews: List<View>,
        iconRes: Int,
        textRes: Int,
    ) {
        if (omnibarViews.any { it.id == R.id.customTabDomain }) return // not shown in custom tabs
        isAdBlockingAnimationRunning = true

        val addressBarRebrandEnabled = appBrandDesignUpdateToggles.addressBar().isEnabled()
        val sceneContext = addressBarAnimationContext(adBlockingScene.context, addressBarRebrandEnabled)
        adBlockingViewBackground.background = ContextCompat.getDrawable(sceneContext, DesignSystemR.drawable.animated_icon_dummy_background)
        adBlockingFirstScene = Scene.getSceneForLayout(adBlockingScene, R.layout.ad_blocking_scene_1, sceneContext)
        adBlockingSecondScene = Scene.getSceneForLayout(adBlockingScene, R.layout.ad_blocking_scene_2, sceneContext)

        hasAdBlockingAnimationBeenCanceled = false
        val allOmnibarViews: List<View> = omnibarViews.filterNotNull().toList()
        adBlockingView.show()
        adBlockingView.alpha = 0F
        adBlockingView.setImageResource(resolveAdBlockingIcon(iconRes, addressBarRebrandEnabled))
        // The static player icon has no built-in inset (unlike the cookie/tracker Lottie compositions,
        // whose artwork fills the badge chip), so pad it to center the glyph in the chip and match their
        // spacing to the text.
        adBlockingView.setPaddingRelative(AD_BLOCKING_ICON_START_PADDING_DP.toPx(), 0, 0, 0)

        val slideInTransition: Transition = createSlideTransition()
        val slideOutTransition: Transition = createSlideTransition()

        slideInTransition.doOnTransitionEnd {
            AnimatorSet().apply {
                play(commonAddressBarAnimationHelper.animateFadeIn(adBlockingView, 0L))
                startDelay = COOKIES_ANIMATION_DELAY
                addListener(
                    doOnEnd {
                        if (!hasAdBlockingAnimationBeenCanceled) {
                            AnimatorSet().apply {
                                TransitionManager.go(adBlockingFirstScene, slideOutTransition)
                                play(commonAddressBarAnimationHelper.animateFadeOut(adBlockingView, COOKIES_ANIMATION_FADE_OUT_DURATION))
                                    .with(
                                        commonAddressBarAnimationHelper.animateFadeOut(
                                            adBlockingViewBackground,
                                            COOKIES_ANIMATION_FADE_OUT_DURATION,
                                        ),
                                    )
                                addListener(
                                    doOnEnd {
                                        adBlockingView.gone()
                                        isAdBlockingAnimationRunning = false
                                        listener?.onAnimationFinished()
                                    },
                                )
                                start()
                            }
                        } else {
                            isAdBlockingAnimationRunning = false
                            listener?.onAnimationFinished()
                        }
                    },
                )
                start()
            }
        }

        slideOutTransition.doOnTransitionEnd {
            if (!hasAdBlockingAnimationBeenCanceled) {
                AnimatorSet().apply {
                    play(commonAddressBarAnimationHelper.animateViewsIn(allOmnibarViews))
                    start()
                }
                adBlockingScene.gone()
            } else {
                isAdBlockingAnimationRunning = false
                listener?.onAnimationFinished()
            }
        }

        AnimatorSet().apply {
            play(commonAddressBarAnimationHelper.animateViewsOut(allOmnibarViews))
                .with(commonAddressBarAnimationHelper.animateFadeIn(adBlockingViewBackground))
                .with(commonAddressBarAnimationHelper.animateFadeIn(adBlockingView))
            addListener(
                onEnd = {
                    adBlockingScene.show()
                    adBlockingScene.alpha = 1F
                    TransitionManager.go(adBlockingSecondScene, slideInTransition)
                    adBlockingScene.findViewById<DaxTextView>(R.id.adBlockingText)?.setText(textRes)
                },
            )
            start()
        }
    }

    override fun removeListener() {
        listener = null
    }

    override fun setListener(animatorListener: TrackersAnimatorListener) {
        listener = animatorListener
    }

    override fun cancelAnimations(
        omnibarViews: List<View>,
    ) {
        conflatedJob.cancel()
        addressBarTrackersAnimator.cancelAnimation()
        stopCookiesAnimation()
        stopAdBlockingAnimation()
        omnibarViews.forEach { it.alpha = 1f }
    }

    private fun tryToStartCookiesAnimation(
        omnibarViews: List<View>,
    ) {
        if (enqueueCookiesAnimation) {
            startCookiesAnimation(omnibarViews)
            enqueueCookiesAnimation = false
        }
    }

    private fun startCookiesAnimation(
        omnibarViews: List<View>,
    ) {
        if (omnibarViews.any { it.id == R.id.customTabDomain }) return // Do not show cookies animation in custom tabs
        isCookiesAnimationRunning = true

        val addressBarRebrandEnabled = appBrandDesignUpdateToggles.addressBar().isEnabled()
        val sceneContext = addressBarAnimationContext(cookieScene.context, addressBarRebrandEnabled)
        cookieViewBackground.background = ContextCompat.getDrawable(sceneContext, DesignSystemR.drawable.animated_icon_dummy_background)
        if (cookieCosmeticHide) {
            firstScene = Scene.getSceneForLayout(cookieScene, R.layout.cookie_cosmetic_scene_1, sceneContext)
            secondScene = Scene.getSceneForLayout(cookieScene, R.layout.cookie_cosmetic_scene_2, sceneContext)
        } else {
            firstScene = Scene.getSceneForLayout(cookieScene, R.layout.cookie_scene_1, sceneContext)
            secondScene = Scene.getSceneForLayout(cookieScene, R.layout.cookie_scene_2, sceneContext)
        }

        hasCookiesAnimationBeenCanceled = false
        val allOmnibarViews: List<View> = (omnibarViews).filterNotNull().toList()
        cookieView.show()
        cookieView.alpha = 0F
        cookieView.setAnimation(
            resolveCookieAnimation(
                isLightMode = cookieUseLightAnimation ?: theme.isLightModeEnabled(),
                brandIconsEnabled = addressBarRebrandEnabled,
            ),
        )
        cookieView.progress = 0F

        val slideInCookiesTransition: Transition = createSlideTransition()
        val slideOutCookiesTransition: Transition = createSlideTransition()

        // After the slide in transitions, wait 1s and then begin slide out + fade out animation views
        slideInCookiesTransition.addListener(
            object : TransitionListener {
                override fun onTransitionEnd(transition: Transition) {
                    AnimatorSet().apply {
                        play(commonAddressBarAnimationHelper.animateFadeIn(cookieView, 0L)) // Fake animation because the delay doesn't really work
                        startDelay = COOKIES_ANIMATION_DELAY
                        addListener(
                            doOnEnd {
                                if (!hasCookiesAnimationBeenCanceled) {
                                    AnimatorSet().apply {
                                        TransitionManager.go(firstScene, slideOutCookiesTransition)
                                        play(commonAddressBarAnimationHelper.animateFadeOut(cookieView, COOKIES_ANIMATION_FADE_OUT_DURATION))
                                            .with(
                                                commonAddressBarAnimationHelper.animateFadeOut(
                                                    cookieViewBackground,
                                                    COOKIES_ANIMATION_FADE_OUT_DURATION,
                                                ),
                                            )
                                        addListener(
                                            doOnEnd {
                                                cookieView.gone()
                                                isCookiesAnimationRunning = false
                                                listener?.onAnimationFinished()
                                            },
                                        )
                                        start()
                                    }
                                } else {
                                    isCookiesAnimationRunning = false
                                    listener?.onAnimationFinished()
                                }
                            },
                        )
                        start()
                    }
                }

                override fun onTransitionStart(transition: Transition) {}
                override fun onTransitionCancel(transition: Transition) {}
                override fun onTransitionPause(transition: Transition) {}
                override fun onTransitionResume(transition: Transition) {}
            },
        )

        // After slide out finished, hide view and fade in omnibar views
        slideOutCookiesTransition.addListener(
            object : TransitionListener {
                override fun onTransitionEnd(transition: Transition) {
                    if (!hasCookiesAnimationBeenCanceled) {
                        AnimatorSet().apply {
                            play(commonAddressBarAnimationHelper.animateViewsIn(allOmnibarViews))
                            start()
                        }
                        cookieScene.gone()
                    } else {
                        isCookiesAnimationRunning = false
                        listener?.onAnimationFinished()
                    }
                }

                override fun onTransitionStart(transition: Transition) {}
                override fun onTransitionCancel(transition: Transition) {}
                override fun onTransitionPause(transition: Transition) {}
                override fun onTransitionResume(transition: Transition) {}
            },
        )

        // When lottie animation begins, begin the transition to slide in the text
        cookieView.addAnimatorListener(
            object : AnimatorListener {
                override fun onAnimationStart(p0: Animator) {
                    TransitionManager.go(secondScene, slideInCookiesTransition)
                }

                override fun onAnimationEnd(p0: Animator) {}
                override fun onAnimationCancel(p0: Animator) {}
                override fun onAnimationRepeat(p0: Animator) {}
            },
        )

        // Here the animations begins. Fade out omnibar, fade in dummy view and after that start lottie animation
        AnimatorSet().apply {
            play(commonAddressBarAnimationHelper.animateViewsOut(allOmnibarViews))
                .with(commonAddressBarAnimationHelper.animateFadeIn(cookieViewBackground))
                .with(commonAddressBarAnimationHelper.animateFadeIn(cookieView))
            addListener(
                onEnd = {
                    cookieScene.show()
                    cookieScene.alpha = 1F
                    cookieView.playAnimation()
                },
            )
            start()
        }
    }

    private fun createSlideTransition(): Transition {
        val slideInCookiesTransition: Transition = Slide(Gravity.START)
        slideInCookiesTransition.duration = COOKIES_ANIMATION_DURATION
        return slideInCookiesTransition
    }

    private fun stopCookiesAnimation() {
        if (!::cookieViewBackground.isInitialized || !::cookieView.isInitialized) return

        hasCookiesAnimationBeenCanceled = true
        if (this::firstScene.isInitialized) {
            TransitionManager.go(firstScene)
        }
        cookieViewBackground.alpha = 0f
        cookieScene.gone()
        cookieView.gone()
    }

    private fun stopAdBlockingAnimation() {
        if (!::adBlockingViewBackground.isInitialized || !::adBlockingView.isInitialized) return

        hasAdBlockingAnimationBeenCanceled = true
        if (this::adBlockingFirstScene.isInitialized) {
            TransitionManager.go(adBlockingFirstScene)
        }
        adBlockingViewBackground.alpha = 0f
        adBlockingScene.gone()
        adBlockingView.gone()
    }

    @RawRes
    internal fun resolveCookieAnimation(
        isLightMode: Boolean,
        brandIconsEnabled: Boolean,
    ): Int = when {
        brandIconsEnabled && isLightMode -> R.raw.cookie_icon_animated_light_brand_update
        brandIconsEnabled -> R.raw.cookie_icon_animated_dark_brand_update
        isLightMode -> R.raw.cookie_icon_animated_light
        else -> R.raw.cookie_icon_animated_dark
    }

    @DrawableRes
    internal fun resolveAdBlockingIcon(
        @DrawableRes legacyRes: Int,
        rebrandIconsEnabled: Boolean,
    ): Int = if (rebrandIconsEnabled) {
        R.drawable.video_player_color_24_brand_update
    } else {
        legacyRes
    }

    companion object {
        private const val COOKIES_ANIMATION_DELAY = 1000L
        private const val COOKIES_ANIMATION_DURATION = 300L
        private const val COOKIES_ANIMATION_FADE_OUT_DURATION = 800L

        // Allows enough time for the tracker animation to finish and the address bar to settle
        // before starting the cookies animation
        private const val DELAY_BETWEEN_ANIMATIONS_DURATION = 1500L

        private const val AD_BLOCKING_ICON_START_PADDING_DP = 9
    }
}

internal fun addressBarAnimationContext(
    context: Context,
    addressBarRebrandEnabled: Boolean,
): Context = if (addressBarRebrandEnabled) {
    ContextThemeWrapper(context, DesignSystemR.style.ThemeOverlay_Rebrand_AddressBarAnimation)
} else {
    context
}

sealed class TrackerLogo() {
    class ImageLogo(val resId: Int) : TrackerLogo()
    class LetterLogo(
        val trackerLetter: String = "",
    ) : TrackerLogo()

    class StackedLogo(val resId: Int = R.drawable.network_logo_more) : TrackerLogo()
}
