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

import android.view.View
import android.widget.ImageView
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.cta.model.CtaId
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.onboarding.ui.view.DaxTypeAnimationTextView
import com.duckduckgo.app.onboarding.ui.view.TouchInterceptingLinearLayout
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.ui.view.text.DaxTextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
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
    private val cardContainer: TouchInterceptingLinearLayout = mock()
    private val activeInclude: View = mock()
    private val primaryInclude: View = mock()
    private val optionsInclude: View = mock()

    private val onboardingStore: OnboardingStore = mock()
    private val appInstallStore: AppInstallStore = mock()

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

        testee = TestableBrandDesignContextualDaxDialogCta(onboardingStore, appInstallStore)
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
    fun getAllContentIncludes_returnsPrimaryAndOptionsIncludes() {
        val includes = testee.invokeGetAllContentIncludes()

        assertEquals(2, includes.size)
        assertSame(primaryInclude, includes[0])
        assertSame(optionsInclude, includes[1])
    }

    /** Concrete subclass that exposes the base-class `internal` helpers for direct test driving. */
    private inner class TestableBrandDesignContextualDaxDialogCta(
        override val onboardingStore: OnboardingStore,
        override val appInstallStore: AppInstallStore,
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
    ) {
        var settledInvocations: Int = 0

        override val activeIncludeId: Int = R.id.contextualBrandDesignPrimaryCtaContent

        override fun configureContentViews(view: View) {
            // No-op for tests.
        }

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
                onSettled = { settledInvocations++ },
            )
        }

        fun invokeResetSharedViewState(isContentTransition: Boolean) {
            resetSharedViewState(container, isContentTransition = isContentTransition)
        }

        fun invokeGetAllContentIncludes(): List<View> = getAllContentIncludes(container)
    }
}
