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
import android.view.ViewTreeObserver
import android.widget.ImageView
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.cta.ui.OnboardingDaxDialogCta.BrandDesignContextualDaxDialogCta.BackgroundBanner
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class BackgroundBannerTest {

    private val view: ImageView = mock()
    private val viewTreeObserver: ViewTreeObserver = mock()

    @Before
    fun before() {
        // doOnPreDraw resolves view.viewTreeObserver — stub a live one so show()/snapToFinalPosition()
        // can register their listeners without NPE.
        whenever(view.viewTreeObserver).thenReturn(viewTreeObserver)
        whenever(viewTreeObserver.isAlive).thenReturn(true)
    }

    @Test
    fun show_resIsZero_doesNothing() {
        BackgroundBanner(view, res = 0).show()

        verify(view, never()).setImageResource(0)
        verify(view, never()).visibility = View.VISIBLE
    }

    @Test
    fun show_resNonZero_setsImageResourceAndMakesVisible() {
        BackgroundBanner(view, res = R.drawable.bg_onboarding_serp).show()

        verify(view).setImageResource(R.drawable.bg_onboarding_serp)
        verify(view).visibility = View.VISIBLE
    }

    @Test
    fun isShowing_visibleView_returnsTrue() {
        whenever(view.visibility).thenReturn(View.VISIBLE)

        assertTrue(BackgroundBanner(view, res = R.drawable.bg_onboarding_serp).isShowing)
    }

    @Test
    fun isShowing_goneView_returnsFalse() {
        whenever(view.visibility).thenReturn(View.GONE)

        assertFalse(BackgroundBanner(view, res = R.drawable.bg_onboarding_serp).isShowing)
    }

    @Test
    fun slideOut_viewNotVisible_returnsNull() {
        whenever(view.visibility).thenReturn(View.GONE)

        val animator = BackgroundBanner(view, res = R.drawable.bg_onboarding_serp).slideOut()

        assertNull(animator)
    }

    @Test
    fun slideOut_resIsZero_returnsNull() {
        whenever(view.visibility).thenReturn(View.VISIBLE)

        val animator = BackgroundBanner(view, res = 0).slideOut()

        assertNull(animator)
    }

    @Test
    fun slideIn_viewNotVisible_doesNotAnimate() {
        whenever(view.visibility).thenReturn(View.GONE)

        BackgroundBanner(view, res = R.drawable.bg_onboarding_serp).slideIn()

        verify(view, never()).animate()
    }
}
