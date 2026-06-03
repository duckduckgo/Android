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
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import com.airbnb.lottie.LottieAnimationView
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.cta.model.CtaId
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.utils.device.DeviceInfo
import com.duckduckgo.common.utils.device.DeviceInfo.FormFactor
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
    private val mockDeviceInfo: DeviceInfo = mock()

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

        verify(showsWavingDax).configureWavingDax(dax, mockDeviceInfo)
        verify(dax).isVisible = true
    }

    @Test
    fun applyWavingDaxState_nullCtaOptIn_hidesDax_noOrientationLookup() {
        val cta = TestableBubbleCta()

        cta.applyWavingDaxState(container, showsWavingDax = null)

        verify(dax).isVisible = false
    }

    @Test
    fun daxFits_false_whenHeadIntrudesIntoCardBody() {
        val cta = TestableBubbleCta()
        assertEquals(false, cta.daxFits(daxTop = 90, daxLeft = 0, daxRight = 40, cardBodyBottom = 100, finBottom = 130, finLeft = 50, finRight = 100))
    }

    @Test
    fun daxFits_false_whenInFinBandAndOverlapsFinHorizontally() {
        val cta = TestableBubbleCta()
        assertEquals(
            false,
            cta.daxFits(daxTop = 110, daxLeft = 60, daxRight = 90, cardBodyBottom = 100, finBottom = 130, finLeft = 50, finRight = 100),
        )
    }

    @Test
    fun daxFits_true_whenInFinBandButHorizontallyClearOfFin() {
        val cta = TestableBubbleCta()
        assertEquals(true, cta.daxFits(daxTop = 110, daxLeft = 0, daxRight = 40, cardBodyBottom = 100, finBottom = 130, finLeft = 50, finRight = 100))
    }

    @Test
    fun daxFits_true_whenEntirelyBelowFinTip() {
        val cta = TestableBubbleCta()
        assertEquals(
            true,
            cta.daxFits(daxTop = 140, daxLeft = 60, daxRight = 90, cardBodyBottom = 100, finBottom = 130, finLeft = 50, finRight = 100),
        )
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

    @Test
    fun configureWavingDax_tablet_anchorsStartToCard() {
        val dax: LottieAnimationView = mock()
        val lp = stubDaxForFormFactor(dax, FormFactor.TABLET)
        val cta = WavingDaxBubbleCta()

        cta.configureWavingDax(dax, mockDeviceInfo)

        assertEquals(R.id.brandDesignCardView, lp.startToStart)
    }

    @Test
    fun configureWavingDax_phone_anchorsStartToParent() {
        val dax: LottieAnimationView = mock()
        val lp = stubDaxForFormFactor(dax, FormFactor.PHONE)
        val cta = WavingDaxBubbleCta()

        cta.configureWavingDax(dax, mockDeviceInfo)

        assertEquals(
            ConstraintLayout.LayoutParams.PARENT_ID,
            lp.startToStart,
        )
    }

    @Test
    fun subscriptionConfigureWavingDax_tablet_anchorsStartToParent() {
        val dax: LottieAnimationView = mock()
        val lp = stubDaxForFormFactor(dax, FormFactor.TABLET)
        lp.startToStart = R.id.brandDesignCardView
        val cta = DaxSubscriptionBrandDesignUpdateBubbleCta(
            onboardingStore = onboardingStore,
            appInstallStore = appInstallStore,
            isLightTheme = true,
            deviceInfo = mockDeviceInfo,
            isFreeTrialCopy = false,
        )

        cta.configureWavingDax(dax, mockDeviceInfo)

        assertEquals(
            ConstraintLayout.LayoutParams.PARENT_ID,
            lp.startToStart,
        )
    }

    @Test
    fun visitSiteConfigureWavingDax_resetsRotationToZero() {
        val dax: LottieAnimationView = mock()
        stubDaxForFormFactor(dax, FormFactor.PHONE)
        val cta = DaxVisitSiteOptionsBrandDesignUpdateBubbleCta(
            onboardingStore = onboardingStore,
            appInstallStore = appInstallStore,
            isLightTheme = true,
            deviceInfo = mockDeviceInfo,
        )

        cta.configureWavingDax(dax, mockDeviceInfo)

        verify(dax).rotation = 0f
    }

    @Test
    fun subscriptionConfigureWavingDax_phone_anchorsStartToParent() {
        val dax: LottieAnimationView = mock()
        val lp = stubDaxForFormFactor(dax, FormFactor.PHONE)
        lp.startToStart = R.id.brandDesignCardView
        val cta = DaxSubscriptionBrandDesignUpdateBubbleCta(
            onboardingStore = onboardingStore,
            appInstallStore = appInstallStore,
            isLightTheme = true,
            deviceInfo = mockDeviceInfo,
            isFreeTrialCopy = false,
        )

        cta.configureWavingDax(dax, mockDeviceInfo)

        assertEquals(
            ConstraintLayout.LayoutParams.PARENT_ID,
            lp.startToStart,
        )
    }

    private fun stubDaxForFormFactor(
        dax: LottieAnimationView,
        formFactor: FormFactor,
    ): ConstraintLayout.LayoutParams {
        val lp = ConstraintLayout.LayoutParams(0, 0)
        val resources: Resources = mock()
        whenever(dax.layoutParams).thenReturn(lp)
        whenever(dax.resources).thenReturn(resources)
        whenever(resources.displayMetrics).thenReturn(android.util.DisplayMetrics().apply { density = 1f })
        whenever(mockDeviceInfo.formFactor()).thenReturn(formFactor)
        return lp
    }

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
        deviceInfo = this@BrandDesignUpdateBubbleCtaTest.mockDeviceInfo,
    ) {
        override val activeIncludeId: Int = R.id.primaryCta
        override val showArrow: Boolean = false

        override fun configureContentViews(view: View) {}
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
            deviceInfo = this@BrandDesignUpdateBubbleCtaTest.mockDeviceInfo,
        ),
        DaxBubbleCta.ShowsWavingDax {
        override val activeIncludeId: Int = R.id.primaryCta
        override val showArrow: Boolean = false
        override val wavingDaxSpec = DaxBubbleCta.WavingDaxSpec(
            rotationDegrees = 0f,
            translationXDp = -40f,
            translationYDp = -150f,
            heightDp = 178f,
            anchorToCardOnTablet = true,
        )

        override fun configureContentViews(view: View) {}
    }
}
