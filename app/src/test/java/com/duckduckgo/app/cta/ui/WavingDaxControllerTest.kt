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
import android.graphics.Rect
import android.util.DisplayMetrics
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import com.airbnb.lottie.LottieAnimationView
import com.duckduckgo.app.browser.R
import com.duckduckgo.common.ui.view.shape.DaxOnboardingBubbleCardView
import com.duckduckgo.common.utils.device.DeviceInfo
import com.duckduckgo.common.utils.device.DeviceInfo.FormFactor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class WavingDaxControllerTest {

    private val deviceInfo: DeviceInfo = mock()
    private val testSpec = DaxBubbleCta.WavingDaxSpec(0f, 0f, 0f, 178f, 178f, false)
    private val controller = WavingDaxController(
        showArrow = true,
        deviceInfo = deviceInfo,
        wavingDaxSpec = testSpec,
    )

    @Test
    fun daxFitHeight_clampsToMax_whenRoomy() {
        assertEquals(800, controller.daxFitHeight(usableBottom = 2000, cardBottom = 1000, marginPx = 0, minHeightPx = 400, maxHeightPx = 800))
    }

    @Test
    fun daxFitHeight_returnsAvailable_betweenMinAndMax() {
        assertEquals(500, controller.daxFitHeight(usableBottom = 1600, cardBottom = 1000, marginPx = 100, minHeightPx = 400, maxHeightPx = 800))
    }

    @Test
    fun daxFitHeight_hidesBelowFloor() {
        assertNull(controller.daxFitHeight(usableBottom = 1300, cardBottom = 1000, marginPx = 0, minHeightPx = 400, maxHeightPx = 800))
    }

    @Test
    fun daxHorizontalScale_isOne_atFullHeight() {
        assertEquals(1f, controller.daxHorizontalScale(heightPx = 800, maxHeightPx = 800), 0f)
    }

    @Test
    fun daxHorizontalScale_shrinksProportionally_belowFullHeight() {
        assertEquals(0.5f, controller.daxHorizontalScale(heightPx = 400, maxHeightPx = 800), 0f)
    }

    @Test
    fun daxHorizontalScale_isOne_whenMaxHeightNonPositive() {
        assertEquals(1f, controller.daxHorizontalScale(heightPx = 400, maxHeightPx = 0), 0f)
    }

    @Test
    fun applyFit_thenSettle_usesAvailableRoomSizing() {
        val container: View = mock()
        val cardView: DaxOnboardingBubbleCardView = mock()
        val dax: LottieAnimationView = mock()
        val resources: Resources = mock()
        val context: Context = mock()
        val layoutParams = ConstraintLayout.LayoutParams(0, 0)
        val displayMetrics = DisplayMetrics().apply { density = 1f }
        val configuration = Configuration().apply { orientation = Configuration.ORIENTATION_PORTRAIT }
        val spec = DaxBubbleCta.WavingDaxSpec(
            rotationDegrees = 0f,
            translationXDp = -40f,
            translationYDp = -150f,
            minHeightDp = 400f,
            maxHeightDp = 800f,
            anchorToCardOnTablet = false,
        )
        val controller = WavingDaxController(showArrow = false, deviceInfo = deviceInfo, wavingDaxSpec = spec)

        whenever(deviceInfo.formFactor()).thenReturn(FormFactor.PHONE)
        whenever(container.isShown).thenReturn(true)
        whenever(container.context).thenReturn(context)
        whenever(container.resources).thenReturn(resources)
        whenever(context.resources).thenReturn(resources)
        whenever(resources.configuration).thenReturn(configuration)
        whenever(resources.displayMetrics).thenReturn(displayMetrics)
        whenever(container.findViewById<LottieAnimationView>(R.id.wavingDax)).thenReturn(dax)
        whenever(container.findViewById<DaxOnboardingBubbleCardView>(R.id.brandDesignCardView)).thenReturn(cardView)
        whenever(cardView.height).thenReturn(500)
        whenever(cardView.arrowDepthFraction).thenReturn(0f)
        whenever(dax.resources).thenReturn(resources)
        whenever(dax.layoutParams).thenReturn(layoutParams)
        whenever(dax.visibility).thenReturn(View.INVISIBLE)
        doAnswer { invocation ->
            (invocation.arguments[0] as IntArray)[1] = 500
            null
        }.whenever(cardView).getLocationOnScreen(any())
        doAnswer { invocation ->
            (invocation.arguments[0] as Rect).bottom = 1508
            null
        }.whenever(container).getWindowVisibleDisplayFrame(any())
        val settleRunnable = argumentCaptor<Runnable>()
        whenever(container.postDelayed(settleRunnable.capture(), eq(100L))).thenReturn(true)

        controller.applyFit(container)
        settleRunnable.firstValue.run()

        // Room-based sizing: 500px available height and proportional -25px peek.
        assertEquals(500, layoutParams.height)
        verify(dax, times(2)).translationX = -25f
        verify(dax).setMinFrame(17)
        verify(dax).progress = 0f
        verify(dax).isVisible = true
    }
}
