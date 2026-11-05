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

import android.app.Activity
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieCompositionFactory
import com.bumptech.glide.Glide
import com.duckduckgo.common.ui.view.button.DaxButton
import com.duckduckgo.common.ui.view.button.DaxButtonPrimary
import com.duckduckgo.mobile.android.R
import com.google.android.material.card.MaterialCardView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.GraphicsMode
import java.io.File
import android.R as AndroidR

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MessageCtaRebrandTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun cardContainerAndCloseButtonFollowOnlyTheRmfToggleInBothThemes() {
        listOf(R.style.Theme_DuckDuckGo_Light, R.style.Theme_DuckDuckGo_Dark).forEach { theme ->
            listOf(false, true).forEach { radius ->
                listOf(false, true).forEach { rebrand ->
                    val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), theme)
                    if (radius) context.theme.applyStyle(R.style.ThemeOverlay_Rebrand_Radius, true)
                    if (rebrand) context.theme.applyStyle(R.style.ThemeOverlay_Rebrand_RmfCards, true)
                    listOf(
                        Triple(MessageCta.MessageType.REMOTE_MESSAGE, R.id.remoteMessage, R.layout.view_remote_message_cta),
                        Triple(MessageCta.MessageType.REMOTE_PROMO_MESSAGE, R.id.promoRemoteMessage, R.layout.view_promo_message_cta),
                    ).forEach { (type, id, layout) ->
                        val cta = MessageCta(context).apply { setMessage(MessageCta.Message(messageType = type)) }
                        val card = cta.findViewById<MaterialCardView>(id)
                        val content = card.getChildAt(0) as ViewGroup
                        val close = content.findViewById<ImageView>(R.id.close)
                        val reference = LayoutInflater.from(context).inflate(layout, null) as MaterialCardView
                        reference.addBottomShadow()
                        val referenceContent = reference.getChildAt(0) as ViewGroup
                        val referenceClose = referenceContent.findViewById<ImageView>(R.id.close)
                        val title = content.findViewById<TextView>(R.id.messageTitle)
                        val body = content.findViewById<TextView>(R.id.messageSubtitle)
                        assertEquals(
                            if (rebrand) {
                                context.getColorFromAttr(R.attr.daxColorPrimaryText)
                            } else {
                                referenceContent.findViewById<TextView>(R.id.messageTitle).currentTextColor
                            },
                            title.currentTextColor,
                        )
                        assertEquals(
                            if (rebrand) {
                                context.getColorFromAttr(R.attr.daxColorSecondaryText)
                            } else {
                                referenceContent.findViewById<TextView>(R.id.messageSubtitle).currentTextColor
                            },
                            body.currentTextColor,
                        )
                        assertEquals(reference.radius, card.radius, 0f)
                        assertEquals(reference.cardElevation, card.cardElevation, 0f)
                        if (rebrand) {
                            val padding = context.resources.getDimensionPixelSize(R.dimen.keyline_5)
                            assertEquals(context.getColorFromAttr(R.attr.daxColorSurfaceSecondary), card.cardBackgroundColor.defaultColor)
                            assertEquals(
                                listOf(
                                    padding,
                                    padding,
                                    padding,
                                    padding - if (type == MessageCta.MessageType.REMOTE_PROMO_MESSAGE) {
                                        context.resources.getDimensionPixelSize(R.dimen.rebrandButtonSmallVerticalInset)
                                    } else {
                                        0
                                    },
                                ),
                                listOf(content.paddingLeft, content.paddingTop, content.paddingRight, content.paddingBottom),
                            )
                            assertEquals(context.resources.getDimensionPixelSize(R.dimen.rmfMessageCloseButtonSize), close.layoutParams.width)
                            assertEquals(close.layoutParams.width, close.layoutParams.height)
                            val params = close.layoutParams as ConstraintLayout.LayoutParams
                            val inset = context.resources.getDimensionPixelSize(R.dimen.keyline_4)
                            assertEquals(inset, content.paddingTop + params.topMargin)
                            assertEquals(inset, content.paddingEnd + params.marginEnd)
                            assertTrue(close.background is RippleDrawable)
                            val fill = (close.background as RippleDrawable).getDrawable(0) as GradientDrawable
                            assertEquals(GradientDrawable.OVAL, fill.shape)
                            val mask = (close.background as RippleDrawable).findDrawableByLayerId(AndroidR.id.mask) as GradientDrawable
                            assertEquals(GradientDrawable.OVAL, mask.shape)
                            assertEquals(context.getColorFromAttr(R.attr.daxColorContainer), fill.color!!.defaultColor)
                            assertEquals(context.resources.getDimensionPixelSize(R.dimen.keyline_5), close.drawable.intrinsicWidth)
                        } else {
                            assertEquals(reference.cardBackgroundColor.defaultColor, card.cardBackgroundColor.defaultColor)
                            assertEquals(referenceContent.paddingTop, content.paddingTop)
                            assertEquals(referenceContent.paddingBottom, content.paddingBottom)
                            assertEquals(referenceContent.paddingLeft, content.paddingLeft)
                            assertEquals(referenceContent.paddingRight, content.paddingRight)
                            assertEquals(referenceClose.layoutParams.width, close.layoutParams.width)
                            assertEquals(referenceClose.layoutParams.height, close.layoutParams.height)
                            assertEquals(referenceClose.background.javaClass, close.background.javaClass)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun actionsKeepThePrimaryButtonAppearanceInBothThemes() {
        listOf(R.style.Theme_DuckDuckGo_Light, R.style.Theme_DuckDuckGo_Dark).forEach { theme ->
            val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), theme).apply {
                getTheme().applyStyle(R.style.ThemeOverlay_Rebrand, true)
                getTheme().applyStyle(R.style.ThemeOverlay_Rebrand_RmfCards, true)
            }
            val cta = MessageCta(context).apply { setMessage(MessageCta.Message(action = "Go", action2 = "Later")) }
            val actual = cta.findViewById<DaxButton>(R.id.primaryActionButton)
            val expected = DaxButtonPrimary(context, null)
            assertEquals(expected.backgroundTintList!!.defaultColor, actual.backgroundTintList!!.defaultColor)
            assertEquals(expected.textColors.defaultColor, actual.textColors.defaultColor)
            assertEquals(expected.rippleColor!!.defaultColor, actual.rippleColor!!.defaultColor)
        }
    }

    @Test
    fun rebrandUsesCentered96DpIllustrationAndExtraTextInsets() {
        val context = rebrandContext()
        val cta = MessageCta(context).apply {
            setMessage(MessageCta.Message(topIllustration = R.drawable.announcement_96, title = "Title", subtitle = "Body"))
        }
        measure(cta, 400)
        val image = cta.findViewById<ImageView>(R.id.topIllustration)
        assertEquals(context.resources.getDimensionPixelSize(R.dimen.rebrandMessageCtaIllustrationSize), image.width)
        assertEquals(image.width, image.height)
        val title = cta.findViewById<View>(R.id.messageTitle)
        val body = cta.findViewById<View>(R.id.messageSubtitle)
        assertEquals(context.resources.getDimensionPixelSize(R.dimen.keyline_5), (title.layoutParams as ConstraintLayout.LayoutParams).marginStart)
        assertEquals(context.resources.getDimensionPixelSize(R.dimen.keyline_2), body.top - title.bottom)
        assertEquals(context.getColorFromAttr(R.attr.daxColorSecondaryText), (body as android.widget.TextView).currentTextColor)
    }

    @Test
    fun closeButtonKeepsA48DpTouchTarget() {
        val cta = MessageCta(rebrandContext())
        cta.setMessage(MessageCta.Message(title = "Title", subtitle = "Body"))
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            controller.get().setContentView(cta)
            var dismissals = 0
            cta.onCloseButtonClicked { dismissals++ }
            listOf(400, 600).forEach { width ->
                measure(cta, width)
                val close = cta.findViewById<View>(R.id.close)
                val parent = close.parent as ViewGroup
                val x = (close.left + close.right) / 2f
                val y = close.top - 5 * cta.resources.displayMetrics.density
                listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEach { action ->
                    val event = MotionEvent.obtain(0, 0, action, x, y, 0)
                    parent.dispatchTouchEvent(event)
                    event.recycle()
                }
                Shadows.shadowOf(Looper.getMainLooper()).idle()
            }
            assertEquals(2, dismissals)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun oneButtonFillsTheRowAndTwoShortLabelsShareEqualWidths() {
        val row = RmfMessageActions(rebrandContext())
        row.primary.text = "Go"
        row.secondary.visibility = View.GONE
        measure(row, 300)
        assertEquals(row.width, row.primary.width)
        row.secondary.visibility = View.VISIBLE
        row.secondary.text = "Later"
        measure(row, 300)
        assertEquals(0, row.secondary.left)
        assertEquals(row.primary.top, row.secondary.top)
        assertEquals(row.primary.width, row.secondary.width)
        assertEquals(row.resources.getDimensionPixelSize(R.dimen.keyline_2), row.primary.left - row.secondary.right)
        assertEquals(
            40 * row.resources.displayMetrics.density,
            (row.primary.height - row.primary.insetTop - row.primary.insetBottom).toFloat(),
            0f,
        )
    }

    @Test
    fun longLabelsStackPrimaryFirstWithoutTruncationAndReturnToARowWhenWidthGrows() {
        val row = RmfMessageActions(rebrandContext())
        row.primary.text = "Get the latest DuckDuckGo browser update"
        row.secondary.text = "Later"
        measure(row, 240)
        assertEquals(0, row.primary.top)
        assertEquals(row.primary.bottom, row.secondary.top)
        assertEquals(
            row.resources.getDimensionPixelSize(R.dimen.keyline_2),
            row.secondary.top + row.secondary.insetTop - (row.primary.bottom - row.primary.insetBottom),
        )
        assertEquals(row.width, row.primary.width)
        assertEquals(row.width, row.secondary.width)
        assertTrue(row.primary.lineCount > 1)
        for (line in 0 until row.primary.lineCount) assertEquals(0, row.primary.layout.getEllipsisCount(line))
        measure(row, 900)
        assertEquals(row.primary.top, row.secondary.top)
    }

    @Test
    fun cardMeasuresTheActionRowBelowItsBodyAndReflowsWhenRebound() {
        val cta = MessageCta(rebrandContext())
        cta.setMessage(MessageCta.Message(title = "Title", subtitle = "Body", action = "Go", action2 = "Later"))
        measure(cta, 400)
        val primary = cta.findViewById<View>(R.id.primaryActionButton)
        val secondary = cta.findViewById<View>(R.id.secondaryActionButton)
        val row = primary.parent as ViewGroup
        val container = row.parent as ViewGroup
        val body = cta.findViewById<View>(R.id.messageSubtitle)
        assertEquals(container.width - container.paddingLeft - container.paddingRight, row.width)
        assertEquals(primary.top, secondary.top)
        assertEquals(cta.resources.getDimensionPixelSize(R.dimen.keyline_4), row.top - body.bottom)
        assertEquals(container.height - container.paddingBottom, row.bottom)
        cta.setMessage(MessageCta.Message(title = "Title", subtitle = "Body", action = "Get the latest DuckDuckGo browser update", action2 = "Later"))
        measure(cta, 240)
        assertEquals(primary.bottom, secondary.top)
        assertEquals(primary.height + secondary.height, row.height)
        assertEquals(container.height - container.paddingBottom, row.bottom)
    }

    @Test
    fun visibleBottomPaddingStays24DpWhenActionsChange() {
        val cta = MessageCta(rebrandContext())
        val padding = cta.resources.getDimensionPixelSize(R.dimen.keyline_5)
        val inset = cta.resources.getDimensionPixelSize(R.dimen.rebrandButtonSmallVerticalInset)
        listOf(
            MessageCta.Message(action = "Go"),
            MessageCta.Message(action = "Go", action2 = "Later"),
            MessageCta.Message(action = "Get the latest DuckDuckGo browser update", action2 = "Later"),
            MessageCta.Message(),
            MessageCta.Message(action = "Go"),
        ).forEach { message ->
            cta.setMessage(message)
            measure(cta, 240)
            val primary = cta.findViewById<DaxButton>(R.id.primaryActionButton)
            val secondary = cta.findViewById<DaxButton>(R.id.secondaryActionButton)
            val row = primary.parent as ViewGroup
            val container = row.parent as ViewGroup
            assertEquals(padding - if (row.visibility == View.VISIBLE) inset else 0, container.paddingBottom)
            if (row.visibility == View.VISIBLE) {
                val lastButton = listOf(primary, secondary).filter { it.visibility == View.VISIBLE }.maxBy { it.bottom }
                assertEquals(padding, container.height - (row.top + lastButton.bottom - lastButton.insetBottom))
            }
        }
    }

    @Test
    fun rtlContextDoesNotChangeTheSharedApplicationFlags() {
        val application = RuntimeEnvironment.getApplication()
        val originalFlags = application.applicationInfo.flags
        val context = rebrandContext()
        assertTrue(context.applicationInfo.flags and ApplicationInfo.FLAG_SUPPORTS_RTL != 0)
        assertEquals(originalFlags, application.applicationInfo.flags)
    }

    @Test
    fun twoShortLabelsGiveTheOddPixelToPrimary() {
        val row = RmfMessageActions(rebrandContext())
        row.primary.text = "Primary"
        row.secondary.text = "Secondary"
        val gap = row.resources.getDimensionPixelSize(R.dimen.keyline_2)
        val width = gap + 1001
        row.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        assertEquals(501, row.primary.measuredWidth)
        assertEquals(500, row.secondary.measuredWidth)
    }

    @Test
    fun longPrimaryKeepsSecondaryAtContentWidth() {
        assertContentWidthSecondary("Subscribe to DuckDuckGo Pro", "No")
    }

    @Test
    fun longSecondaryKeepsItsContentWidthAndPrimaryFillsTheRest() {
        assertContentWidthSecondary("Go", "Subscribe to DuckDuckGo Pro")
    }

    private fun assertContentWidthSecondary(primaryLabel: String, secondaryLabel: String) {
        val row = RmfMessageActions(rebrandContext())
        row.primary.text = primaryLabel
        row.secondary.text = secondaryLabel
        row.primary.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        row.secondary.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val primaryContentWidth = row.primary.measuredWidth
        val secondaryContentWidth = row.secondary.measuredWidth
        val gap = row.resources.getDimensionPixelSize(R.dimen.keyline_2)
        val available = primaryContentWidth + secondaryContentWidth + minOf(primaryContentWidth, secondaryContentWidth) / 2
        assertTrue(maxOf(primaryContentWidth, secondaryContentWidth) > available / 2)
        row.measure(View.MeasureSpec.makeMeasureSpec(available + gap, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        row.layout(0, 0, row.measuredWidth, row.measuredHeight)
        assertEquals(secondaryContentWidth, row.secondary.measuredWidth)
        assertEquals(available - secondaryContentWidth, row.primary.measuredWidth)
        assertTrue(row.primary.measuredWidth > primaryContentWidth)
        assertEquals(row.primary.top, row.secondary.top)
        listOf(row.primary, row.secondary).forEach { button ->
            assertEquals(1, button.lineCount)
            assertEquals(0, button.layout.getEllipsisCount(0))
        }
    }

    @Test
    fun rtlKeepsPrimaryAtTheEndAndActionsAndStateSavingSurviveRebinding() {
        val cta = MessageCta(rebrandContext())
        var primaryClicks = 0
        var secondaryClicks = 0
        cta.onPrimaryActionClicked { primaryClicks++ }
        cta.onSecondaryActionClicked { secondaryClicks++ }
        cta.setMessage(MessageCta.Message(action = "Go", action2 = "Later"))
        val primary = cta.findViewById<View>(R.id.primaryActionButton)
        val secondary = cta.findViewById<View>(R.id.secondaryActionButton)
        val row = primary.parent as ViewGroup
        row.layoutDirection = View.LAYOUT_DIRECTION_RTL
        measure(row, 300)
        assertEquals(0, primary.left)
        assertEquals(row.width, secondary.right)
        primary.performClick()
        secondary.performClick()
        assertEquals(1, primaryClicks)
        assertEquals(1, secondaryClicks)
        cta.disableStateSaving()
        assertFalse(primary.isSaveEnabled)
        assertFalse(secondary.isSaveEnabled)
        cta.setMessage(MessageCta.Message())
        assertEquals(View.GONE, row.visibility)
        cta.setMessage(MessageCta.Message(action = "Go"))
        assertEquals(View.VISIBLE, row.visibility)
        assertEquals(View.GONE, secondary.visibility)
    }

    @Test
    fun displayedBodyDeterminesActionAnchorOnEveryBind() {
        listOf(false, true).forEach { enabled ->
            val context = if (enabled) {
                rebrandContext()
            } else {
                ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_DuckDuckGo_Light)
            }
            val cta = MessageCta(context)
            listOf("", "Body", "<b>HTML body</b>", "<b></b>", "Body again", "").forEach { bodyText ->
                listOf("", "Later").forEach { secondaryText ->
                    cta.setMessage(MessageCta.Message(title = "Title", subtitle = bodyText, action = "Go", action2 = secondaryText))
                    measure(cta, 400)
                    val body = cta.findViewById<TextView>(R.id.messageSubtitle)
                    val title = cta.findViewById<TextView>(R.id.messageTitle)
                    val primary = cta.findViewById<DaxButton>(R.id.primaryActionButton)
                    if (enabled) {
                        assertEquals(if (body.text.isEmpty()) View.GONE else View.VISIBLE, body.visibility)
                        val lastText = if (body.visibility == View.VISIBLE) body else title
                        val actions = primary.parent as ViewGroup
                        assertEquals(
                            20 * context.resources.displayMetrics.density,
                            (actions.top + primary.top + primary.insetTop - lastText.bottom).toFloat(),
                            1f,
                        )
                    } else {
                        assertEquals(View.VISIBLE, body.visibility)
                        assertEquals(R.id.messageSubtitle, (primary.layoutParams as ConstraintLayout.LayoutParams).topToBottom)
                        assertEquals(
                            context.resources.getDimensionPixelSize(R.dimen.keyline_3),
                            (primary.layoutParams as ConstraintLayout.LayoutParams).topMargin,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun promoKeepsIconCallbackAndStateSavingWithFullWidthAndMeasuredSpacing() {
        listOf(R.style.Theme_DuckDuckGo_Light, R.style.Theme_DuckDuckGo_Dark).forEach { theme ->
            listOf(false, true).forEach { enabled ->
                val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), theme)
                if (enabled) context.theme.applyStyle(R.style.ThemeOverlay_Rebrand_RmfCards, true)
                val cta = MessageCta(context)
                var clicks = 0
                cta.onPromoActionClicked { clicks++ }
                listOf("Share", "Diesen Browser mit Freunden und Familie teilen").forEachIndexed { index, label ->
                    cta.setMessage(
                        MessageCta.Message(
                            title = "Desktop browser",
                            subtitle = "<b>Browse privately</b>",
                            middleIllustration = if (index == 0) R.drawable.desktop_promo_artwork else null,
                            promoAction = label,
                            messageType = MessageCta.MessageType.REMOTE_PROMO_MESSAGE,
                        ),
                    )
                    measure(cta, 320)
                    val card = cta.findViewById<MaterialCardView>(R.id.promoRemoteMessage)
                    val button = card.findViewById<DaxButton>(R.id.actionButton)
                    val body = card.findViewById<View>(R.id.messageSubtitle)
                    val illustration = card.findViewById<View>(R.id.illustration)
                    val content = button.parent as ViewGroup
                    assertEquals(label, button.text.toString())
                    assertNotNull(button.icon)
                    val expectedIcon = AppCompatResources.getDrawable(context, R.drawable.ic_share_android_16)!!
                    assertEquals(expectedIcon.intrinsicWidth, button.icon!!.intrinsicWidth)
                    assertEquals(if (index == 0) View.VISIBLE else View.GONE, illustration.visibility)
                    cta.disableStateSaving()
                    assertFalse(button.isSaveEnabled)
                    button.performClick()
                    assertEquals(index + 1, clicks)
                    if (enabled) {
                        assertEquals(content.width - content.paddingLeft - content.paddingRight, button.width)
                        assertEquals(
                            24 * context.resources.displayMetrics.density,
                            (content.height - button.bottom + button.insetBottom).toFloat(),
                            1f,
                        )
                        assertEquals(20 * context.resources.displayMetrics.density, (button.top + button.insetTop - body.bottom).toFloat(), 1f)
                        for (line in 0 until button.lineCount) assertEquals(0, button.layout.getEllipsisCount(line))
                        if (index == 1) assertTrue(button.lineCount > 1)
                    } else {
                        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, button.layoutParams.width)
                        assertEquals(context.resources.getDimensionPixelSize(R.dimen.keyline_4), content.paddingBottom)
                    }
                }
            }
        }
    }

    @Test
    fun shortSecondaryUsesMeasuredWidthAtTheRowThresholdIncludingRtlAndFontScale() {
        listOf(1f, 1.3f).forEach { scale ->
            val context = rebrandContext(scale)
            listOf(View.LAYOUT_DIRECTION_LTR, View.LAYOUT_DIRECTION_RTL).forEach { direction ->
                val row = RmfMessageActions(context)
                row.layoutDirection = direction
                row.primary.text = "Subscribe to DuckDuckGo Pro"
                row.secondary.text = "No"
                row.primary.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
                row.secondary.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
                val naturalSecondary = row.secondary.measuredWidth
                val naturalPrimary = row.primary.measuredWidth
                val density = row.resources.displayMetrics.density
                val oldMinimum = (64 * density).toInt()
                assertEquals(0, row.secondary.minWidth)
                assertEquals(0, row.secondary.minimumWidth)
                assertEquals((16 * density).toInt(), row.secondary.paddingStart)
                assertEquals((16 * density).toInt(), row.secondary.paddingEnd)
                assertTrue(naturalSecondary < oldMinimum)
                assertTrue(naturalSecondary >= 48 * density)
                assertTrue(row.secondary.measuredHeight >= 48 * density)
                val threshold = naturalPrimary + naturalSecondary + row.resources.getDimensionPixelSize(R.dimen.keyline_2)
                fun layoutAt(width: Int) {
                    row.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
                    row.layout(0, 0, width, row.measuredHeight)
                }
                layoutAt(threshold)
                assertEquals(row.primary.top, row.secondary.top)
                assertEquals(1, row.primary.lineCount)
                assertEquals(1, row.secondary.lineCount)
                assertEquals(if (direction == View.LAYOUT_DIRECTION_RTL) 0 else threshold - row.primary.width, row.primary.left)
                layoutAt(threshold - 1)
                assertEquals(0, row.primary.top)
                assertEquals(row.primary.bottom, row.secondary.top)
                row.secondary.minWidth = oldMinimum
                row.secondary.minimumWidth = oldMinimum
                layoutAt(threshold)
                assertEquals(row.primary.bottom, row.secondary.top)
                row.secondary.minWidth = 0
                row.secondary.minimumWidth = 0
                layoutAt(threshold)
                assertEquals(row.primary.top, row.secondary.top)
            }
        }
    }

    @Test
    fun veryShortSecondaryRetains48DpTouchWidthWithoutThe64DpFloor() {
        val row = RmfMessageActions(rebrandContext())
        row.primary.text = "Subscribe to DuckDuckGo Pro"
        row.secondary.text = "I"
        row.primary.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val target = row.resources.getDimensionPixelSize(R.dimen.keyline_7)
        val width = row.primary.measuredWidth + target + row.resources.getDimensionPixelSize(R.dimen.keyline_2)
        row.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        row.layout(0, 0, width, row.measuredHeight)
        assertEquals(row.primary.top, row.secondary.top)
        assertEquals(target, row.secondary.width)
        assertTrue(row.secondary.height >= target)
        assertEquals(0, row.secondary.minWidth)
        assertEquals(0, row.secondary.minimumWidth)
    }

    @Test
    fun squareAndWideVectorsFitInsideThe96DpSlot() {
        val cta = MessageCta(rebrandContext())
        listOf(R.drawable.announcement_96, R.drawable.desktop_promo_artwork).forEach { resource ->
            cta.setMessage(MessageCta.Message(topIllustration = resource))
            measure(cta, 400)
            val image = cta.findViewById<ImageView>(R.id.topIllustration)
            val slot = cta.resources.getDimensionPixelSize(R.dimen.rebrandMessageCtaIllustrationSize)
            assertEquals(slot, image.width)
            assertEquals(slot, image.height)
            assertEquals(ImageView.ScaleType.FIT_CENTER, image.scaleType)
            val bounds = RectF(image.drawable.bounds)
            image.imageMatrix.mapRect(bounds)
            assertEquals(slot.toFloat(), bounds.width(), 1f)
            assertEquals(image.drawable.intrinsicWidth.toFloat() / image.drawable.intrinsicHeight, bounds.width() / bounds.height(), 0.01f)
        }
    }

    @Test
    fun animationAndStaticReplacementUseTheSame96DpSlot() {
        val cta = MessageCta(rebrandContext())
        cta.setMessage(MessageCta.Message(topAnimation = R.raw.cookie_banner_dark))
        val composition = LottieCompositionFactory.fromJsonStringSync(
            """{"v":"5.5.7","fr":30,"ip":0,"op":30,"w":1280,"h":960,"layers":[]}""",
            null,
        ).value!!
        cta.onTopAnimationConfigured { it.setComposition(composition) }
        measure(cta, 400)
        val animated = cta.findViewById<LottieAnimationView>(R.id.topIllustrationAnimated)
        val slot = cta.resources.getDimensionPixelSize(R.dimen.rebrandMessageCtaIllustrationSize)
        assertEquals(slot, animated.width)
        assertEquals(slot, animated.height)
        assertEquals(ImageView.ScaleType.FIT_CENTER, animated.scaleType)
        assertEquals(View.VISIBLE, animated.visibility)
        cta.onTopAnimationConfigured { it.setImageResource(R.drawable.desktop_promo_artwork) }
        measure(cta, 400)
        assertEquals(slot, animated.width)
        assertEquals(slot, animated.height)
        assertNotNull(animated.drawable)
        val remote = cta.findViewById<ImageView>(R.id.topImage)
        assertEquals(slot, remote.layoutParams.width)
        assertEquals(slot, remote.layoutParams.height)
    }

    @Test
    fun remoteImageKeepsSuccessFailureAndFallbackBehaviorAt96Dp() {
        val cta = MessageCta(rebrandContext())
        var successes = 0
        var failures = 0
        cta.onRemoteImageLoadSuccess { successes++ }
        cta.onRemoteImageLoadFailed { failures++ }
        val imageFile = temporaryFolder.newFile("image.png")
        val bitmap = Bitmap.createBitmap(128, 96, Bitmap.Config.ARGB_8888)
        imageFile.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
        val image = cta.findViewById<ImageView>(R.id.topImage)
        fun awaitImage(predicate: () -> Boolean) {
            val deadline = System.nanoTime() + 5_000_000_000L
            while (!predicate() && System.nanoTime() < deadline) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(10)
            }
            assertTrue("Glide callback did not arrive", predicate())
            Shadows.shadowOf(Looper.getMainLooper()).idle()
        }
        try {
            cta.setMessage(MessageCta.Message(title = "Title", imageUrl = imageFile.absolutePath))
            measure(cta, 400)
            awaitImage { successes == 1 }
            assertEquals(0, failures)
            assertEquals(View.VISIBLE, image.visibility)
            assertEquals(cta.resources.getDimensionPixelSize(R.dimen.rebrandMessageCtaIllustrationSize), image.width)
            assertNotNull(image.drawable)
            cta.setMessage(
                MessageCta.Message(
                    title = "Title",
                    imageUrl = File(temporaryFolder.root, "missing.png").absolutePath,
                    topIllustration = R.drawable.announcement_96,
                ),
            )
            measure(cta, 400)
            awaitImage { failures == 1 }
            assertEquals(View.VISIBLE, image.visibility)
            assertNotNull(image.drawable)
            cta.setMessage(MessageCta.Message(title = "Title", imageUrl = File(temporaryFolder.root, "also-missing.png").absolutePath))
            measure(cta, 400)
            awaitImage { failures == 2 }
            measure(cta, 400)
            assertEquals(View.GONE, image.visibility)
            val title = cta.findViewById<View>(R.id.messageTitle)
            assertEquals(0, (title.layoutParams as ConstraintLayout.LayoutParams).topMargin)
            assertEquals(1, successes)
        } finally {
            Glide.with(image).clear(image)
        }
    }

    private fun rebrandContext(fontScale: Float = 1f): ContextThemeWrapper {
        val application = RuntimeEnvironment.getApplication()
        val configuration = Configuration(application.resources.configuration).apply { this.fontScale = fontScale }
        return object : ContextThemeWrapper(application.createConfigurationContext(configuration), R.style.Theme_DuckDuckGo_Light) {
            private val localApplicationInfo = ApplicationInfo(application.applicationInfo).apply {
                flags = flags or ApplicationInfo.FLAG_SUPPORTS_RTL
            }

            override fun getApplicationInfo(): ApplicationInfo = localApplicationInfo
        }.apply {
            theme.applyStyle(R.style.ThemeOverlay_Rebrand_RmfCards, true)
        }
    }

    private fun measure(view: View, widthDp: Int) {
        val width = (widthDp * view.resources.displayMetrics.density).toInt()
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        view.layout(0, 0, width, view.measuredHeight)
    }
}
