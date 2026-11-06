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

package com.duckduckgo.feedback.impl.ui.initial

import android.view.LayoutInflater
import android.widget.ImageView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.feedback.impl.R
import com.google.android.material.card.MaterialCardView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import com.duckduckgo.mobile.android.R as DesignSystemR

@RunWith(AndroidJUnit4::class)
class InitialFeedbackLayoutTest {

    @Test
    fun responseChoicesKeepLegacyImagesAndProvideHiddenBrandCards() {
        val context = ContextThemeWrapper(
            ApplicationProvider.getApplicationContext(),
            DesignSystemR.style.Theme_DuckDuckGo_Light,
        )
        val root = LayoutInflater.from(context)
            .inflate(R.layout.content_feedback, null, false)

        val positiveImage = root.findViewById<ImageView>(R.id.positiveFeedbackButton)
        val negativeImage = root.findViewById<ImageView>(R.id.negativeFeedbackButton)
        val positiveCard = root.findViewById<MaterialCardView>(R.id.positiveFeedbackBrandButton)
        val negativeCard = root.findViewById<MaterialCardView>(R.id.negativeFeedbackBrandButton)

        assertEquals(ImageView::class.java, positiveImage.javaClass)
        assertEquals(ImageView::class.java, negativeImage.javaClass)
        assertEquals(100, positiveImage.layoutParams.width)
        assertEquals(100, positiveImage.layoutParams.height)
        assertEquals(100, negativeImage.layoutParams.width)
        assertEquals(100, negativeImage.layoutParams.height)
        assertEquals(android.view.View.VISIBLE, positiveImage.visibility)
        assertEquals(android.view.View.VISIBLE, negativeImage.visibility)
        assertEquals(android.view.View.GONE, positiveCard.visibility)
        assertEquals(android.view.View.GONE, negativeCard.visibility)
        assertTrue(positiveCard.isClickable)
        assertTrue(positiveCard.isFocusable)
        assertTrue(negativeCard.isClickable)
        assertTrue(negativeCard.isFocusable)
        assertEquals(100, positiveCard.layoutParams.width)
        assertEquals(100, positiveCard.layoutParams.height)
        assertEquals(100, negativeCard.layoutParams.width)
        assertEquals(100, negativeCard.layoutParams.height)
        assertEquals(1, positiveCard.strokeWidth)
        assertEquals(1, negativeCard.strokeWidth)

        assertEquals(48, (positiveCard.getChildAt(0) as ImageView).layoutParams.width)
        assertEquals(48, (positiveCard.getChildAt(0) as ImageView).layoutParams.height)
        assertEquals(48, (negativeCard.getChildAt(0) as ImageView).layoutParams.width)
        assertEquals(48, (negativeCard.getChildAt(0) as ImageView).layoutParams.height)
    }
}
