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
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

/**
 * Robolectric-backed unit tests for [OnboardingDaxDialogCta.BrandDesignContextualDaxDialogCta].
 * Exercises the base-class state machine directly — [snapToFinished], [resetSharedViewState],
 * [getAllContentIncludes] — without booting the full fragment.
 */
@RunWith(AndroidJUnit4::class)
class BrandDesignContextualDaxDialogCtaTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val mockOnboardingStore: OnboardingStore = mock()
    private val mockAppInstallStore: AppInstallStore = mock()

    private lateinit var container: View
    private lateinit var titleView: DaxTypeAnimationTextView
    private lateinit var hiddenTitle: DaxTextView
    private lateinit var descriptionView: DaxTextView
    private lateinit var dismissButton: ImageView
    private lateinit var cardContainer: TouchInterceptingLinearLayout
    private lateinit var activeInclude: View

    private lateinit var testee: TestableBrandDesignContextualDaxDialogCta

    @Before
    fun before() {
        val themedContext = androidx.appcompat.view.ContextThemeWrapper(
            context,
            com.duckduckgo.mobile.android.R.style.Theme_DuckDuckGo_Light,
        )
        container = LayoutInflater.from(themedContext)
            .inflate(R.layout.include_onboarding_in_context_dax_dialog_brand_design_update, FrameLayout(themedContext), false)
        titleView = container.findViewById(R.id.contextualBrandDesignTitle)
        hiddenTitle = container.findViewById(R.id.contextualBrandDesignHiddenTitle)
        descriptionView = container.findViewById(R.id.contextualBrandDesignDescription)
        dismissButton = container.findViewById(R.id.contextualBrandDesignDismissButton)
        cardContainer = container.findViewById(R.id.contextualBrandDesignCardContainer)
        activeInclude = container.findViewById(R.id.contextualBrandDesignPrimaryCtaContent)

        testee = TestableBrandDesignContextualDaxDialogCta(
            mockOnboardingStore,
            mockAppInstallStore,
            isLightTheme = true,
        )
    }

    @Test
    fun snapToFinished_tapBeforeAnimationStarts_setsTitleDirectly() {
        hiddenTitle.text = "Dax title"
        titleView.text = ""

        testee.invokeSnap(alreadySettled = false)

        assertEquals("Dax title", titleView.text.toString())
        assertEquals(1f, titleView.alpha, 0f)
        assertEquals(1f, descriptionView.alpha, 0f)
        assertEquals(1f, dismissButton.alpha, 0f)
        assertEquals(1f, activeInclude.alpha, 0f)
        assertEquals(1, testee.settledInvocations)
    }

    @Test
    fun snapToFinished_tapMidAnimation_finishesWithoutBlankTitle() {
        hiddenTitle.text = "Full dax text"
        titleView.text = "Par"
        titleView.startTypingAnimation("Full dax text", isCancellable = true)

        testee.invokeSnap(alreadySettled = false)

        // finishAnimation() on TypeAnimationTextView sets text to the completeText; at minimum
        // the title must be non-empty so the user never sees a blank dialog after tapping.
        assertTrue(titleView.text.toString().isNotEmpty())
        assertEquals(1f, descriptionView.alpha, 0f)
        assertEquals(1f, dismissButton.alpha, 0f)
        assertEquals(1f, activeInclude.alpha, 0f)
    }

    @Test
    fun snapToFinished_tapAfterAnimationEnds_isNoOp() {
        hiddenTitle.text = "Dax title"
        titleView.text = "Dax title"

        testee.invokeSnap(alreadySettled = true)

        assertEquals(0, testee.settledInvocations)
    }

    @Test
    fun snapToFinished_rapidDoubleTap_firesCallbackOnlyOnce() {
        hiddenTitle.text = "Dax title"

        testee.invokeSnap(alreadySettled = false)
        // Simulate the base class flipping animationsSettled after the first call.
        testee.invokeSnap(alreadySettled = true)

        assertEquals(1, testee.settledInvocations)
    }

    @Test
    fun resetSharedViewState_resetsGravityTextAlphaVisibility() {
        titleView.alpha = 1f
        titleView.text = "leftover title"
        hiddenTitle.text = "leftover hidden"
        descriptionView.alpha = 1f
        descriptionView.text = "leftover description"
        dismissButton.alpha = 1f

        testee.invokeResetSharedViewState()

        assertEquals(0f, titleView.alpha, 0f)
        assertEquals("", titleView.text.toString())
        assertEquals("", hiddenTitle.text.toString())
        assertEquals(0f, descriptionView.alpha, 0f)
        assertEquals("", descriptionView.text.toString())
        assertEquals(0f, dismissButton.alpha, 0f)
    }

    @Test
    fun getAllContentIncludes_returnsExpectedIds() {
        val includes = testee.invokeGetAllContentIncludes()

        assertEquals(2, includes.size)
        val includeIds = includes.map { it.id }.toSet()
        assertTrue(includeIds.contains(R.id.contextualBrandDesignPrimaryCtaContent))
        assertTrue(includeIds.contains(R.id.contextualBrandDesignOptionsContent))
    }

    /**
     * Concrete subclass that exposes the base-class `internal` helpers so tests can drive the
     * state machine without booting a real fragment. Runs with the test layout inflated in
     * [before]; never mounted in a real FragmentBrowserTabBinding.
     */
    private inner class TestableBrandDesignContextualDaxDialogCta(
        override val onboardingStore: OnboardingStore,
        override val appInstallStore: AppInstallStore,
        override val isLightTheme: Boolean,
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
        isLightTheme = isLightTheme,
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

        fun invokeResetSharedViewState() {
            resetSharedViewState(container)
        }

        fun invokeGetAllContentIncludes(): List<View> = getAllContentIncludes(container)
    }
}
