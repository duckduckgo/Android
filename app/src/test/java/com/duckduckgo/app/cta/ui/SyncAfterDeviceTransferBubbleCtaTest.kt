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
import android.graphics.Typeface
import android.text.Spanned
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.common.utils.device.DeviceInfo
import com.google.android.material.button.MaterialButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.annotation.Config
import kotlin.math.roundToInt
import com.duckduckgo.mobile.android.R as DesignSystemR

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SyncAfterDeviceTransferBubbleCtaTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val onboardingStore: OnboardingStore = mock()
    private val appInstallStore: AppInstallStore = mock()
    private val deviceInfo: DeviceInfo = mock()

    private val syncCta = SyncAfterDeviceTransferBubbleCta(onboardingStore, appInstallStore, isLightTheme = true, deviceInfo = deviceInfo)
    private val subscriptionCta = DaxSubscriptionBubbleCta(
        onboardingStore,
        appInstallStore,
        isLightTheme = true,
        deviceInfo = deviceInfo,
        isCustomAiOnboardingFlow = false,
        isFreeTrialCopy = false,
        segmentedPath = null,
    )

    // Unit tests in :app run without the app's resources, so the bubble is assembled from plain views that mirror
    // include_onboarding_bubble_dax_dialog, and text appearances are verified rather than resolved.
    private val container: View = mock()
    private val titleBlock = FrameLayout(context)
    private val title = textView()
    private val hiddenTitle = textView()
    private val description = textView()
    private val cardContainer = LinearLayout(context).apply { setPaddingRelative(0, dp(24f), 0, dp(20f)) }
    private val primaryCta: MaterialButton = mock()
    private val primaryCtaParams = ViewGroup.MarginLayoutParams(0, 0).apply { topMargin = dp(20f) }
    private val dismissButton = View(context).apply {
        layoutParams = ViewGroup.MarginLayoutParams(0, 0).apply {
            topMargin = dp(-4f)
            marginEnd = dp(-4f)
        }
    }
    private var containerTag: Any? = null

    @Before
    fun before() {
        titleBlock.layoutParams = LinearLayout.LayoutParams(0, 0).apply {
            marginStart = dp(24f)
            marginEnd = dp(24f)
        }
        titleBlock.addView(title)
        description.layoutParams = LinearLayout.LayoutParams(0, 0).apply {
            marginStart = dp(24f)
            marginEnd = dp(24f)
            topMargin = dp(16f)
        }
        whenever(primaryCta.layoutParams).thenReturn(primaryCtaParams)
        whenever(primaryCta.resources).thenReturn(context.resources)
        mapOf(
            R.id.brandDesignTitle to title,
            R.id.brandDesignHiddenTitle to hiddenTitle,
            R.id.brandDesignDescription to description,
            R.id.brandDesignCardContainer to cardContainer,
            R.id.primaryCta to primaryCta,
            R.id.brandDesignDismissButton to dismissButton,
            R.id.brandDesignHeaderImage to mock<ImageView>(),
        ).forEach { (id, view) -> whenever(container.findViewById<View>(id)).thenReturn(view) }
        whenever(container.getTag(R.id.brandDesignCardContainer)).thenAnswer { containerTag }
        doAnswer { containerTag = it.arguments[1] }.whenever(container).setTag(eq(R.id.brandDesignCardContainer), any())
    }

    @Test
    fun whenConfiguredThenTitleAndBodyUseTheDesignedTypography() {
        configure(syncCta)

        listOf(title, hiddenTitle).forEach {
            verify(it).setTextAppearance(DesignSystemR.style.Typography_DuckDuckGo_Onboarding_Title)
            assertEquals(sp(28f), it.lineHeight)
        }
        verify(description).setTextAppearance(DesignSystemR.style.Typography_DuckDuckGo_Onboarding_Body)
        assertEquals(sp(22f), description.lineHeight)
    }

    @Test
    fun whenConfiguredThenSpacingMatchesTheDesign() {
        configure(syncCta)

        assertEquals(listOf(dp(32f), dp(32f)), titleBlock.horizontalMargins())
        assertEquals(listOf(dp(32f), dp(32f)), description.horizontalMargins())
        assertEquals(dp(8f), description.margins().topMargin)
        assertEquals(dp(20f), cardContainer.paddingTop)
        assertEquals(dp(27f), primaryCtaParams.topMargin)
        assertEquals(dp(-5.5f), dismissButton.margins().topMargin)
        assertEquals(dp(-5.5f), dismissButton.margins().marginEnd)
    }

    @Test
    fun whenDecoratedThenOnlyTheBlankLineBetweenParagraphsIsShortened() {
        val text = syncCta.decorateDescription(context, "First paragraph.\n\nSecond paragraph.") as Spanned

        val spans = text.getSpans(0, text.length, SyncAfterDeviceTransferBubbleCta.ExactLineHeightSpan::class.java)
        assertEquals(1, spans.size)
        val blankLine = text.indexOf("\n\n") + 1
        assertEquals(blankLine, text.getSpanStart(spans.single()))
        assertEquals(blankLine + 1, text.getSpanEnd(spans.single()))
    }

    @Test
    fun whenAnotherOnboardingDialogIsConfiguredAfterwardsThenItGetsTheLayoutDefaultsBack() {
        val defaults = snapshot()

        configure(syncCta)
        assertNotEquals(defaults, snapshot())
        configure(subscriptionCta)

        assertEquals(defaults, snapshot())
    }

    private fun configure(cta: DaxBubbleCta) {
        cta.resetContentStyle(container)
        cta.configureContentViews(container)
    }

    private fun textView(): TextView = spy(TextView(context)).also {
        doNothing().whenever(it).setTextAppearance(any<Int>())
        it.typeface = Typeface.SERIF
    }

    private fun snapshot(): List<Any> = listOf(
        listOf(title, hiddenTitle, description).map { listOf(it.textSize, it.lineHeight, it.typeface) },
        titleBlock.horizontalMargins(),
        description.horizontalMargins() + description.margins().topMargin,
        cardContainer.paddingTop,
        primaryCtaParams.topMargin,
        listOf(dismissButton.margins().topMargin, dismissButton.margins().marginEnd),
    )

    private fun View.margins() = layoutParams as ViewGroup.MarginLayoutParams

    private fun View.horizontalMargins() = listOf(margins().marginStart, margins().marginEnd)

    private fun sp(value: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, context.resources.displayMetrics).roundToInt()

    private fun dp(value: Float) = (value * context.resources.displayMetrics.density).roundToInt()
}
