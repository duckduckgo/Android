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

package com.duckduckgo.duckchat.impl.nativeinput.footer.usagelimits

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.ImageView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.ui.view.button.DaxButtonSecondary
import com.duckduckgo.common.ui.view.text.DaxTextView
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.nativeinput.footer.usagelimits.UsageLimitFooterMessage.Icon
import com.duckduckgo.duckchat.impl.nativeinput.footer.usagelimits.UsageLimitFooterMessage.Severity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UsageLimitFooterViewTest {

    private val context: Context = ContextThemeWrapper(
        ApplicationProvider.getApplicationContext(),
        com.duckduckgo.mobile.android.R.style.Theme_DuckDuckGo_Light,
    )
    private val testee = UsageLimitFooterView(context)

    @Test
    fun whenApproachingMessageIsRenderedThenRingTitleResetAndDismissAreShown() {
        testee.render(approaching(), onDismiss = {})

        assertEquals("75% of weekly limit", testee.findViewById<DaxTextView>(R.id.usageLimitFooterTitle).text.toString())
        assertEquals("Resets in 2 days", testee.findViewById<DaxTextView>(R.id.usageLimitFooterResetText).text.toString())
        assertEquals(View.VISIBLE, testee.findViewById<UsageRingView>(R.id.usageLimitFooterRing).visibility)
        assertEquals(View.GONE, testee.findViewById<ImageView>(R.id.usageLimitFooterAlert).visibility)
        assertEquals(View.VISIBLE, testee.findViewById<ImageView>(R.id.usageLimitFooterDismiss).visibility)
        assertEquals(0.75f, testee.findViewById<UsageRingView>(R.id.usageLimitFooterRing).progress)
    }

    @Test
    fun whenReachedMessageIsRenderedThenAlertIsShownAndDismissIsHidden() {
        testee.render(
            UsageLimitFooterMessage(title = "Daily limit reached", resetText = "Resets in 5 hours", icon = Icon.Alert, dismissible = false),
            onDismiss = {},
        )

        assertEquals(View.GONE, testee.findViewById<UsageRingView>(R.id.usageLimitFooterRing).visibility)
        assertEquals(View.VISIBLE, testee.findViewById<ImageView>(R.id.usageLimitFooterAlert).visibility)
        assertEquals(View.GONE, testee.findViewById<ImageView>(R.id.usageLimitFooterDismiss).visibility)
    }

    @Test
    fun whenMessageHasACtaLabelThenButtonShowsItAndClickInvokesCallback() {
        var clicked = false
        testee.render(approaching().copy(ctaLabel = "Switch Model"), onDismiss = {}, onCta = { clicked = true })

        val button = testee.findViewById<DaxButtonSecondary>(R.id.usageLimitFooterCta)
        assertEquals(View.VISIBLE, button.visibility)
        assertEquals("Switch Model", button.text.toString())

        button.performClick()
        assertTrue(clicked)
    }

    @Test
    fun whenMessageHasNoCtaThenButtonIsHidden() {
        testee.render(approaching(), onDismiss = {})

        assertEquals(View.GONE, testee.findViewById<DaxButtonSecondary>(R.id.usageLimitFooterCta).visibility)
    }

    @Test
    fun whenDismissIsClickedThenCallbackIsInvoked() {
        var dismissed = false
        testee.render(approaching(), onDismiss = { dismissed = true })

        testee.findViewById<ImageView>(R.id.usageLimitFooterDismiss).performClick()

        assertTrue(dismissed)
    }

    @Test
    fun whenCreatedThenContentStartsBelowTheHostOverlap() {
        val content = testee.getChildAt(0)

        assertEquals(context.resources.getDimensionPixelSize(R.dimen.nativeInputFooterOverlap), content.paddingTop)
    }

    private fun approaching() = UsageLimitFooterMessage(
        title = "75% of weekly limit",
        resetText = "Resets in 2 days",
        icon = Icon.Ring(0.75f, Severity.WARNING),
        dismissible = true,
    )
}
