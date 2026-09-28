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

package com.duckduckgo.common.ui.view

import android.content.Context
import android.view.View.MeasureSpec
import android.widget.ImageView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.mobile.android.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MessageCtaIllustrationSizeTest {

    private val context = ApplicationProvider.getApplicationContext<Context>().apply {
        setTheme(R.style.Theme_DuckDuckGo_Light)
    }

    private val slot get() = context.resources.getDimensionPixelSize(R.dimen.messageCtaIllustrationSize)

    @Test
    fun whenSquareIllustrationIsLargerThanTheSlotThenItScalesDownWholeInsteadOfCropping() {
        val illustration = measureTopIllustration(R.drawable.announcement_96)

        assertEquals(ImageView.ScaleType.FIT_CENTER, illustration.scaleType)
        assertEquals(96, illustration.drawable.intrinsicWidth)
        assertEquals(slot, illustration.width)
        assertEquals(slot, illustration.height)
    }

    @Test
    fun whenWideIllustrationIsUsedThenAspectRatioIsPreservedAndOnlyHeightIsCapped() {
        val illustration = measureTopIllustration(R.drawable.desktop_promo_artwork)

        val intrinsicWidth = illustration.drawable.intrinsicWidth
        val intrinsicHeight = illustration.drawable.intrinsicHeight
        val expectedWidth = slot * intrinsicWidth / intrinsicHeight

        assertEquals(slot, illustration.height)
        assertTrue(
            "expected width close to $expectedWidth but was ${illustration.width}",
            Math.abs(illustration.width - expectedWidth) <= 1,
        )
        assertTrue("wide artwork must stay wider than the slot", illustration.width > slot)
    }

    private fun measureTopIllustration(drawableRes: Int): ImageView {
        val cta = MessageCta(context).apply {
            setMessage(
                MessageCta.Message(
                    topIllustration = drawableRes,
                    title = "title",
                    subtitle = "subtitle",
                ),
            )
            measure(
                MeasureSpec.makeMeasureSpec(1080, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
            )
            layout(0, 0, 1080, measuredHeight)
        }
        return cta.findViewById(R.id.topIllustration)
    }
}
