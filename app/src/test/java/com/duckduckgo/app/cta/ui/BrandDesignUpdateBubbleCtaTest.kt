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
import android.util.DisplayMetrics
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import com.airbnb.lottie.LottieAnimationView
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.cta.model.CtaId
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class BrandDesignUpdateBubbleCtaTest {

    private val container: View = mock()
    private val dax: LottieAnimationView = mock()

    private val onboardingStore: OnboardingStore = mock()
    private val appInstallStore: AppInstallStore = mock()

    @Before
    fun before() {
        whenever(container.findViewById<LottieAnimationView>(R.id.wavingDax)).thenReturn(dax)
    }

    @Test
    fun applyWavingDaxState_phoneLandscape_hidesDax_evenWhenCtaOptsIn() {
        configureContainerForPhoneLandscape()
        val cta = TestableBubbleCta()
        val showsWavingDax: DaxBubbleCta.ShowsWavingDax = mock()

        cta.applyWavingDaxState(container, showsWavingDax)

        verify(dax).isVisible = false
    }

    @Test
    fun applyWavingDaxState_notPhoneLandscape_showsAndConfiguresDax_whenCtaOptsIn() {
        configureContainerForPhonePortrait()
        whenever(dax.alpha).thenReturn(0f)
        val cta = TestableBubbleCta()
        val showsWavingDax: DaxBubbleCta.ShowsWavingDax = mock()

        cta.applyWavingDaxState(container, showsWavingDax)

        verify(showsWavingDax).configureWavingDax(dax)
        verify(dax).isVisible = true
    }

    @Test
    fun applyWavingDaxState_nullCtaOptIn_hidesDax_noOrientationLookup() {
        val cta = TestableBubbleCta()

        cta.applyWavingDaxState(container, showsWavingDax = null)

        verify(dax).isVisible = false
    }

    private fun configureContainer(orientation: Int, smallestScreenWidthDp: Int) {
        val configuration = Configuration().apply {
            this.orientation = orientation
            this.smallestScreenWidthDp = smallestScreenWidthDp
        }
        val resources: Resources = mock()
        val context: Context = mock()
        whenever(container.context).thenReturn(context)
        whenever(context.resources).thenReturn(resources)
        whenever(resources.configuration).thenReturn(configuration)
    }

    private fun configureContainerForPhoneLandscape() = configureContainer(
        orientation = Configuration.ORIENTATION_LANDSCAPE,
        smallestScreenWidthDp = 360,
    )

    private fun configureContainerForPhonePortrait() = configureContainer(
        orientation = Configuration.ORIENTATION_PORTRAIT,
        smallestScreenWidthDp = 360,
    )

    @Test
    fun configureWavingDax_tablet_anchorsStartToCard() {
        val dax: LottieAnimationView = mock()
        val lp = stubDaxForFormFactor(dax, smallestScreenWidthDp = 800)
        val cta = WavingDaxBubbleCta()

        cta.configureWavingDax(dax)

        assertEquals(R.id.brandDesignCardView, lp.startToStart)
    }

    @Test
    fun configureWavingDax_phone_anchorsStartToParent() {
        val dax: LottieAnimationView = mock()
        val lp = stubDaxForFormFactor(dax, smallestScreenWidthDp = 360)
        val cta = WavingDaxBubbleCta()

        cta.configureWavingDax(dax)

        assertEquals(
            ConstraintLayout.LayoutParams.PARENT_ID,
            lp.startToStart,
        )
    }

    @Test
    fun subscriptionConfigureWavingDax_tablet_anchorsStartToParent() {
        val dax: LottieAnimationView = mock()
        val lp = stubDaxForFormFactor(dax, smallestScreenWidthDp = 800)
        lp.startToStart = R.id.brandDesignCardView
        val cta = DaxSubscriptionBrandDesignUpdateBubbleCta(
            onboardingStore = onboardingStore,
            appInstallStore = appInstallStore,
            isLightTheme = true,
            isFreeTrialCopy = false,
        )

        cta.configureWavingDax(dax)

        assertEquals(
            ConstraintLayout.LayoutParams.PARENT_ID,
            lp.startToStart,
        )
    }

    @Test
    fun subscriptionConfigureWavingDax_phone_anchorsStartToParent() {
        val dax: LottieAnimationView = mock()
        val lp = stubDaxForFormFactor(dax, smallestScreenWidthDp = 360)
        lp.startToStart = R.id.brandDesignCardView
        val cta = DaxSubscriptionBrandDesignUpdateBubbleCta(
            onboardingStore = onboardingStore,
            appInstallStore = appInstallStore,
            isLightTheme = true,
            isFreeTrialCopy = false,
        )

        cta.configureWavingDax(dax)

        assertEquals(
            ConstraintLayout.LayoutParams.PARENT_ID,
            lp.startToStart,
        )
    }

    private fun stubDaxForFormFactor(
        dax: LottieAnimationView,
        smallestScreenWidthDp: Int,
    ): ConstraintLayout.LayoutParams {
        val lp = ConstraintLayout.LayoutParams(0, 0)
        val configuration = Configuration().apply {
            this.smallestScreenWidthDp = smallestScreenWidthDp
        }
        val displayMetrics = DisplayMetrics().apply { density = 1f }
        val resources: Resources = mock()
        val context: Context = mock()
        whenever(dax.layoutParams).thenReturn(lp)
        whenever(dax.context).thenReturn(context)
        whenever(dax.resources).thenReturn(resources)
        whenever(context.resources).thenReturn(resources)
        whenever(resources.configuration).thenReturn(configuration)
        whenever(resources.displayMetrics).thenReturn(displayMetrics)
        return lp
    }

    /** Concrete subclass that exposes [applyWavingDaxState] for direct test driving. */
    private inner class TestableBubbleCta : DaxBubbleCta.BrandDesignUpdateBubbleCta(
        ctaId = CtaId.DAX_END,
        title = R.string.onboardingEndDaxDialogTitle,
        description = R.string.onboardingEndDaxDialogDescription,
        shownPixel = AppPixelName.ONBOARDING_DAX_CTA_SHOWN,
        okPixel = AppPixelName.ONBOARDING_DAX_CTA_OK_BUTTON,
        ctaPixelParam = Pixel.PixelValues.DAX_END_CTA,
        onboardingStore = this@BrandDesignUpdateBubbleCtaTest.onboardingStore,
        appInstallStore = this@BrandDesignUpdateBubbleCtaTest.appInstallStore,
        isLightTheme = true,
    ) {
        override val activeIncludeId: Int = R.id.primaryCta
        override val showArrow: Boolean = false

        override fun configureContentViews(view: View) {
            // No-op for tests.
        }
    }

    private inner class WavingDaxBubbleCta :
        DaxBubbleCta.BrandDesignUpdateBubbleCta(
            ctaId = CtaId.DAX_END,
            title = R.string.onboardingEndDaxDialogTitle,
            description = R.string.onboardingEndDaxDialogDescription,
            shownPixel = AppPixelName.ONBOARDING_DAX_CTA_SHOWN,
            okPixel = AppPixelName.ONBOARDING_DAX_CTA_OK_BUTTON,
            ctaPixelParam = Pixel.PixelValues.DAX_END_CTA,
            onboardingStore = this@BrandDesignUpdateBubbleCtaTest.onboardingStore,
            appInstallStore = this@BrandDesignUpdateBubbleCtaTest.appInstallStore,
            isLightTheme = true,
        ),
        DaxBubbleCta.ShowsWavingDax {
        override val activeIncludeId: Int = R.id.primaryCta
        override val showArrow: Boolean = false

        override fun configureContentViews(view: View) {}
    }
}
