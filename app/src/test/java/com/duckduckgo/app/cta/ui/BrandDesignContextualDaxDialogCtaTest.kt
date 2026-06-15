/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.app.cta.ui

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.view.View
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import com.airbnb.lottie.LottieAnimationView
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.cta.model.CtaId
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.onboarding.ui.view.DaxTypeAnimationTextView
import com.duckduckgo.app.onboarding.ui.view.TouchInterceptingLinearLayout
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.ui.view.shape.DaxOnboardingBubbleBrandDesignUpdateCardView
import com.duckduckgo.common.ui.view.text.DaxTextView
import com.duckduckgo.common.utils.device.DeviceInfo
import com.duckduckgo.common.utils.device.DeviceInfo.FormFactor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class BrandDesignContextualDaxDialogCtaTest {

    private val container: View = mock()
    private val titleView: DaxTypeAnimationTextView = mock()
    private val hiddenTitle: DaxTextView = mock()
    private val descriptionView: DaxTextView = mock()
    private val dismissButton: ImageView = mock()
    private val backgroundView: ImageView = mock()
    private val cardContainer: TouchInterceptingLinearLayout = mock()
    private val activeInclude: View = mock()
    private val primaryInclude: View = mock()
    private val optionsInclude: View = mock()

    private val cardView: DaxOnboardingBubbleBrandDesignUpdateCardView = mock()

    private val onboardingStore: OnboardingStore = mock()
    private val appInstallStore: AppInstallStore = mock()
    private val mockDeviceInfo: DeviceInfo = mock()

    private lateinit var testee: TestableBrandDesignContextualDaxDialogCta

    @Before
    fun before() {
        whenever(container.findViewById<DaxTypeAnimationTextView>(R.id.contextualBrandDesignTitle))
            .thenReturn(titleView)
        whenever(container.findViewById<DaxTextView>(R.id.contextualBrandDesignHiddenTitle))
            .thenReturn(hiddenTitle)
        whenever(container.findViewById<DaxTextView>(R.id.contextualBrandDesignDescription))
            .thenReturn(descriptionView)
        whenever(container.findViewById<View>(R.id.contextualBrandDesignDismissButton))
            .thenReturn(dismissButton)
        whenever(container.findViewById<View>(R.id.contextualBrandDesignPrimaryCtaContent))
            .thenReturn(primaryInclude)
        whenever(container.findViewById<View>(R.id.contextualBrandDesignOptionsContent))
            .thenReturn(optionsInclude)
        whenever(container.findViewById<ImageView>(R.id.contextualBrandDesignBackground))
            .thenReturn(backgroundView)
        whenever(container.findViewById<DaxOnboardingBubbleBrandDesignUpdateCardView>(R.id.contextualBrandDesignCardView))
            .thenReturn(cardView)

        testee = TestableBrandDesignContextualDaxDialogCta(onboardingStore, appInstallStore, mockDeviceInfo)
    }

    @Test
    fun snapToFinished_tapBeforeAnimationStarts_setsTitleDirectly() {
        whenever(hiddenTitle.text).thenReturn("Dax title")
        whenever(titleView.hasAnimationStarted()).thenReturn(false)

        testee.invokeSnap(alreadySettled = false)

        verify(cardContainer).interceptChildTouches = false
        verify(titleView).finishAnimation()
        verify(titleView).text = "Dax title"
        verify(titleView).alpha = 1f
        verify(descriptionView).alpha = 1f
        verify(dismissButton).alpha = 1f
        verify(activeInclude).alpha = 1f
        assertEquals(1, testee.settledInvocations)
    }

    @Test
    fun snapToFinished_tapMidAnimation_finishesAnimationWithoutOverwritingText() {
        whenever(hiddenTitle.text).thenReturn("Full dax text")
        whenever(titleView.hasAnimationStarted()).thenReturn(true)

        testee.invokeSnap(alreadySettled = false)

        verify(titleView).finishAnimation()
        // Animation already running — finishAnimation() lands the full text via its end-action.
        // Setting titleView.text directly here would race with that and risk a partial render.
        verify(titleView, never()).text = any<CharSequence>()
        verify(titleView).alpha = 1f
        verify(descriptionView).alpha = 1f
        verify(dismissButton).alpha = 1f
        verify(activeInclude).alpha = 1f
    }

    @Test
    fun snapToFinished_emptyTitle_keepsTitleAlphaZero() {
        whenever(hiddenTitle.text).thenReturn("")
        whenever(titleView.hasAnimationStarted()).thenReturn(false)

        testee.invokeSnap(alreadySettled = false)

        // No-title CTAs must not lift the title alpha — an empty title view at alpha=1 would still
        // occupy layout space but render nothing. Description + active include must still fade in.
        verify(titleView, never()).alpha = 1f
        verify(descriptionView).alpha = 1f
        verify(activeInclude).alpha = 1f
        assertEquals(1, testee.settledInvocations)
    }

    @Test
    fun snapToFinished_respectsAlreadySettledFlag() {
        whenever(hiddenTitle.text).thenReturn("Dax title")
        whenever(titleView.hasAnimationStarted()).thenReturn(false)

        testee.invokeSnap(alreadySettled = false)
        // Second call with alreadySettled=true (as the outer state machine would pass after the
        // first settlement) must not re-fire the settled callback.
        testee.invokeSnap(alreadySettled = true)

        assertEquals(1, testee.settledInvocations)
    }

    @Test
    fun resetSharedViewState_firstShow_zeroesAllSharedViewsIncludingDismissButton() {
        testee.invokeResetSharedViewState(isContentTransition = false)

        verify(titleView).alpha = 0f
        verify(titleView).text = ""
        verify(hiddenTitle).text = ""
        verify(descriptionView).alpha = 0f
        verify(descriptionView).text = ""
        verify(dismissButton).alpha = 0f
    }

    @Test
    fun resetSharedViewState_contentTransition_leavesDismissButtonAlphaUntouched() {
        testee.invokeResetSharedViewState(isContentTransition = true)

        verify(titleView).alpha = 0f
        verify(titleView).text = ""
        verify(hiddenTitle).text = ""
        verify(descriptionView).alpha = 0f
        verify(descriptionView).text = ""
        // Dismiss button must remain visible across content transitions — guard against the
        // regression where it was snapped to 0 then re-faded in, causing a visible flicker.
        verify(dismissButton, never()).alpha = 0f
    }

    @Test
    fun resetSharedViewState_firstShow_hidesBackground() {
        testee.invokeResetSharedViewState(isContentTransition = false)

        verify(backgroundView).visibility = View.GONE
    }

    @Test
    fun resetSharedViewState_contentTransition_leavesBackgroundUntouched() {
        testee.invokeResetSharedViewState(isContentTransition = true)

        verify(backgroundView, never()).visibility = View.GONE
        verify(backgroundView, never()).visibility = View.VISIBLE
    }

    @Test
    fun isContentTransition_containerGoneAndAlphaZero_returnsFalse() {
        whenever(container.alpha).thenReturn(0f)
        whenever(container.visibility).thenReturn(View.GONE)

        assertFalse(testee.invokeIsContentTransition())
    }

    @Test
    fun isContentTransition_containerVisibleAndAlphaOne_returnsTrue() {
        whenever(container.alpha).thenReturn(1f)
        whenever(container.visibility).thenReturn(View.VISIBLE)

        assertTrue(testee.invokeIsContentTransition())
    }

    @Test
    fun isContentTransition_containerGoneButAlphaOne_returnsFalse() {
        // After hideOnboardingCta() the layout is gone() but alpha is left at 1f from the previous
        // animated show. The next show must be treated as first-show, not a content transition,
        // so the dismiss button correctly fades back in from alpha=0.
        whenever(container.alpha).thenReturn(1f)
        whenever(container.visibility).thenReturn(View.GONE)

        assertFalse(testee.invokeIsContentTransition())
    }

    @Test
    fun getAllContentIncludes_returnsPrimaryAndOptionsIncludes() {
        val includes = testee.invokeGetAllContentIncludes()

        assertEquals(2, includes.size)
        assertSame(primaryInclude, includes[0])
        assertSame(optionsInclude, includes[1])
    }

    @Test
    fun snapToFinished_phoneLandscape_setsArrowDepthZero_evenWhenShowArrowTrue() {
        configureContainerForPhoneLandscape()
        whenever(hiddenTitle.text).thenReturn("Title")
        whenever(titleView.hasAnimationStarted()).thenReturn(false)
        val cta = WingAndArrowContextualCta(onboardingStore, appInstallStore, mockDeviceInfo)

        cta.invokeSnap(alreadySettled = false)

        verify(cardView).setArrowDepthFraction(0f)
    }

    @Test
    fun snapToFinished_notPhoneLandscape_setsArrowDepthOne_whenShowArrowTrue() {
        configureContainerForPhonePortrait()
        whenever(hiddenTitle.text).thenReturn("Title")
        whenever(titleView.hasAnimationStarted()).thenReturn(false)
        val cta = WingAndArrowContextualCta(onboardingStore, appInstallStore, mockDeviceInfo)

        cta.invokeSnap(alreadySettled = false)

        verify(cardView).setArrowDepthFraction(1f)
    }

    @Test
    fun applyWingBottomState_phoneLandscape_hidesWing() {
        configureContainerForPhoneLandscape()
        val wing: LottieAnimationView = mock()
        whenever(container.findViewById<LottieAnimationView>(R.id.wingBottom)).thenReturn(wing)
        whenever(wing.isAnimating).thenReturn(false)
        val cta = WingAndArrowContextualCta(onboardingStore, appInstallStore, mockDeviceInfo)

        cta.invokeApplyWingBottomState()

        verify(wing).isVisible = false
    }

    private fun configureContainer(orientation: Int, formFactor: FormFactor) {
        val configuration = Configuration().apply { this.orientation = orientation }
        val resources: Resources = mock()
        val context: Context = mock()
        whenever(container.context).thenReturn(context)
        whenever(context.resources).thenReturn(resources)
        whenever(resources.configuration).thenReturn(configuration)
        whenever(mockDeviceInfo.formFactor()).thenReturn(formFactor)
    }

    private fun configureContainerForPhoneLandscape() = configureContainer(
        orientation = Configuration.ORIENTATION_LANDSCAPE,
        formFactor = FormFactor.PHONE,
    )

    private fun configureContainerForPhonePortrait() = configureContainer(
        orientation = Configuration.ORIENTATION_PORTRAIT,
        formFactor = FormFactor.PHONE,
    )

    private fun configureContainerForTabletPortrait() = configureContainer(
        orientation = Configuration.ORIENTATION_PORTRAIT,
        formFactor = FormFactor.TABLET,
    )

    private fun stubWingLayoutParams(wing: LottieAnimationView): ConstraintLayout.LayoutParams {
        val lp = ConstraintLayout.LayoutParams(0, 0)
        whenever(wing.layoutParams).thenReturn(lp)
        return lp
    }

    @Test
    fun applyWingBottomState_tabletPortrait_anchorsWingStartToCard() {
        configureContainerForTabletPortrait()
        val wing: LottieAnimationView = mock()
        whenever(container.findViewById<LottieAnimationView>(R.id.wingBottom))
            .thenReturn(wing)
        whenever(wing.isAnimating).thenReturn(false)
        val lp = stubWingLayoutParams(wing)
        val cta = WingAndArrowContextualCta(onboardingStore, appInstallStore, mockDeviceInfo)

        cta.invokeApplyWingBottomState()

        assertEquals(R.id.contextualBrandDesignCardView, lp.startToStart)
    }

    @Test
    fun applyWingBottomState_phonePortrait_anchorsWingStartToParent() {
        configureContainerForPhonePortrait()
        val wing: LottieAnimationView = mock()
        whenever(container.findViewById<LottieAnimationView>(R.id.wingBottom))
            .thenReturn(wing)
        whenever(wing.isAnimating).thenReturn(false)
        val lp = stubWingLayoutParams(wing)
        val cta = WingAndArrowContextualCta(onboardingStore, appInstallStore, mockDeviceInfo)

        cta.invokeApplyWingBottomState()

        assertEquals(ConstraintLayout.LayoutParams.PARENT_ID, lp.startToStart)
    }

    @Test
    fun applyWingBottomState_wingMidExitWhenWingCtaArrives_abortsExitAndSnapsToResting() {
        configureContainerForPhonePortrait()
        val wing: LottieAnimationView = mock()
        whenever(container.findViewById<LottieAnimationView>(R.id.wingBottom)).thenReturn(wing)
        stubWingLayoutParams(wing)
        whenever(wing.visibility).thenReturn(View.VISIBLE)
        whenever(wing.isAnimating).thenReturn(true)
        whenever(wing.progress).thenReturn(0.7f)
        val cta = WingAndArrowContextualCta(onboardingStore, appInstallStore, mockDeviceInfo)

        cta.invokeApplyWingBottomState()

        verify(wing).removeAllAnimatorListeners()
        verify(wing).cancelAnimation()
        verify(wing).setMinAndMaxProgress(0f, 0.5f)
        verify(wing).progress = 0.5f
    }

    @Test
    fun applyWingBottomState_wingMidPlayInWhenWingCtaArrives_snapsToResting() {
        configureContainerForPhonePortrait()
        val wing: LottieAnimationView = mock()
        whenever(container.findViewById<LottieAnimationView>(R.id.wingBottom)).thenReturn(wing)
        stubWingLayoutParams(wing)
        whenever(wing.visibility).thenReturn(View.VISIBLE)
        whenever(wing.isAnimating).thenReturn(true)
        whenever(wing.progress).thenReturn(0.3f)
        val cta = WingAndArrowContextualCta(onboardingStore, appInstallStore, mockDeviceInfo)

        cta.invokeApplyWingBottomState()

        verify(wing).removeAllAnimatorListeners()
        verify(wing).cancelAnimation()
        verify(wing).setMinAndMaxProgress(0f, 0.5f)
        verify(wing).progress = 0.5f
    }

    @Test
    fun applyWingBottomState_wingAtRestingWhenWingCtaArrives_snapsToRestingAndDoesNotCancel() {
        configureContainerForPhonePortrait()
        val wing: LottieAnimationView = mock()
        whenever(container.findViewById<LottieAnimationView>(R.id.wingBottom)).thenReturn(wing)
        stubWingLayoutParams(wing)
        whenever(wing.visibility).thenReturn(View.VISIBLE)
        whenever(wing.isAnimating).thenReturn(false)
        whenever(wing.progress).thenReturn(0.5f)
        val cta = WingAndArrowContextualCta(onboardingStore, appInstallStore, mockDeviceInfo)

        cta.invokeApplyWingBottomState()

        verify(wing, never()).removeAllAnimatorListeners()
        verify(wing, never()).cancelAnimation()
        verify(wing).setMinAndMaxProgress(0f, 0.5f)
        verify(wing).progress = 0.5f
    }

    @Test
    fun startWingBottomPlayIn_runnableSupersededByNextCtaStaging_doesNotPlay() {
        configureContainerForPhonePortrait()
        val wing: LottieAnimationView = mock()
        whenever(container.findViewById<LottieAnimationView>(R.id.wingBottom)).thenReturn(wing)
        whenever(wing.visibility).thenReturn(View.VISIBLE)
        whenever(wing.isAnimating).thenReturn(false)
        // Mid-play-in: wing has reached frame 10 of a 45-frame stop window.
        whenever(wing.frame).thenReturn(10)
        whenever(wing.maxFrame).thenReturn(45f)

        val ctaA = WingAndArrowContextualCta(onboardingStore, appInstallStore, mockDeviceInfo)
        ctaA.invokeStartWingBottomPlayIn()

        val captor = argumentCaptor<Runnable>()
        verify(wing).postDelayed(captor.capture(), any())

        val ctaB = WingAndArrowContextualCta(onboardingStore, appInstallStore, mockDeviceInfo)
        ctaB.invokeApplyWingBottomState()

        captor.firstValue.run()

        verify(wing, never()).playAnimation()
    }

    private inner class TestableBrandDesignContextualDaxDialogCta(
        override val onboardingStore: OnboardingStore,
        override val appInstallStore: AppInstallStore,
        override val deviceInfo: DeviceInfo,
    ) : OnboardingDaxDialogCta.BrandDesignContextualDaxDialogCta(
        ctaId = CtaId.DAX_DIALOG_SERP,
        description = null,
        buttonText = null,
        shownPixel = AppPixelName.ONBOARDING_DAX_CTA_SHOWN,
        okPixel = AppPixelName.ONBOARDING_DAX_CTA_OK_BUTTON,
        cancelPixel = null,
        closePixel = null,
        ctaPixelParam = Pixel.PixelValues.DAX_SERP_CTA,
        onboardingStore = onboardingStore,
        appInstallStore = appInstallStore,
        isLightTheme = true,
        deviceInfo = deviceInfo,
    ) {
        var settledInvocations: Int = 0

        override val activeIncludeId: Int = R.id.contextualBrandDesignPrimaryCtaContent
        override val showArrow: Boolean = false

        override fun configureContentViews(view: View) {}

        fun invokeSnap(alreadySettled: Boolean) {
            snapToFinished(
                container = container,
                titleView = titleView,
                descriptionView = descriptionView,
                dismissButton = dismissButton,
                activeInclude = activeInclude,
                cardContainer = cardContainer,
                alreadySettled = alreadySettled,
                contentFadeInAnimator = null,
                fadeOutAnimator = null,
                onSettled = { settledInvocations++ },
            )
        }

        fun invokeResetSharedViewState(isContentTransition: Boolean) {
            resetSharedViewState(container, isContentTransition = isContentTransition)
        }

        fun invokeGetAllContentIncludes(): List<View> = getAllContentIncludes(container)

        fun invokeIsContentTransition(): Boolean = isContentTransition(container)
    }

    private inner class WingAndArrowContextualCta(
        override val onboardingStore: OnboardingStore,
        override val appInstallStore: AppInstallStore,
        override val deviceInfo: DeviceInfo,
    ) : OnboardingDaxDialogCta.BrandDesignContextualDaxDialogCta(
        ctaId = CtaId.DAX_DIALOG_TRACKERS_FOUND,
        description = null,
        buttonText = null,
        shownPixel = AppPixelName.ONBOARDING_DAX_CTA_SHOWN,
        okPixel = AppPixelName.ONBOARDING_DAX_CTA_OK_BUTTON,
        cancelPixel = null,
        closePixel = null,
        ctaPixelParam = Pixel.PixelValues.DAX_TRACKERS_BLOCKED_CTA,
        onboardingStore = onboardingStore,
        appInstallStore = appInstallStore,
        isLightTheme = true,
        deviceInfo = deviceInfo,
    ),
        OnboardingDaxDialogCta.ShowsWingBottom {

        var settledInvocations: Int = 0

        override val activeIncludeId: Int = R.id.contextualBrandDesignPrimaryCtaContent
        override val showArrow: Boolean = true

        override fun configureContentViews(view: View) {}

        fun invokeSnap(alreadySettled: Boolean) {
            snapToFinished(
                container = container,
                titleView = titleView,
                descriptionView = descriptionView,
                dismissButton = dismissButton,
                activeInclude = activeInclude,
                cardContainer = cardContainer,
                alreadySettled = alreadySettled,
                contentFadeInAnimator = null,
                fadeOutAnimator = null,
                onSettled = { settledInvocations++ },
            )
        }

        fun invokeApplyWingBottomState() {
            applyWingBottomState(container)
        }

        fun invokeStartWingBottomPlayIn() {
            startWingBottomPlayIn(container)
        }
    }
}
