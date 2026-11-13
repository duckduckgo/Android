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

package com.duckduckgo.common.ui.view.dialog

import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.drawable.ColorDrawable
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.ui.applyBottomSystemBarInsetPadding
import com.duckduckgo.common.ui.setRoundCorners
import com.duckduckgo.common.ui.view.button.DaxButtonDestructiveSecondary
import com.duckduckgo.common.ui.view.button.DaxButtonGhost
import com.duckduckgo.common.ui.view.button.DaxButtonPrimary
import com.duckduckgo.common.ui.view.button.DaxButtonSecondary
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.common.ui.view.text.DaxTextView
import com.duckduckgo.mobile.android.R
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.shape.MaterialShapeDrawable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class RebrandBottomSheetsTest {
    private fun context(enabled: Boolean, dark: Boolean = false, buttons: Boolean = false): Context =
        ContextThemeWrapper(RuntimeEnvironment.getApplication(), if (dark) R.style.Theme_DuckDuckGo_Dark else R.style.Theme_DuckDuckGo_Light).apply {
            if (buttons) theme.applyStyle(R.style.ThemeOverlay_Rebrand, true)
            if (enabled) theme.applyStyle(R.style.ThemeOverlay_Rebrand_BottomSheets, true)
        }

    private class Sheet(val context: Context) {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(7, 9, 11, 13)
        }
        val close = ImageView(context).apply {
            setImageResource(R.drawable.ic_close_24)
            setPadding(1, 2, 3, 4)
        }
        val header = ImageView(context)
        val title = DaxTextView(context, null).apply { text = "Title" }
        val message = DaxTextView(context, null).apply { text = "Message" }
        val primary = DaxButtonPrimary(context, null)
        val secondary = DaxButtonGhost(context, null)
        init {
            root.background = ColorDrawable(0xff123456.toInt())
            listOf(close, header, title, message, primary, secondary).forEach {
                root.addView(
                    it,
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 40).apply {
                        marginStart = 17
                        marginEnd = 19
                        topMargin = 23
                        bottomMargin = 29
                    },
                )
            }
            (close.layoutParams as LinearLayout.LayoutParams).apply {
                width = ViewGroup.LayoutParams.WRAP_CONTENT
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                gravity = Gravity.END
            }
            header.layoutParams.width = 96
        }
        fun apply() = root.applyRebrandBottomSheetSpacing(header, title, listOf(message), primary, listOf(secondary), close)
        fun dp(value: Int) = (root.resources.displayMetrics.density * value).toInt()
        fun measure(width: Int) {
            root.measure(
                View.MeasureSpec.makeMeasureSpec(dp(width), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            )
            root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        }
    }

    @Test
    fun disabledLeavesViewsUntouched() {
        val sheet = Sheet(context(false))
        val background = sheet.root.background
        val buttonTint = sheet.secondary.backgroundTintList
        val textColors = sheet.secondary.textColors
        sheet.root.applyRebrandBottomSheetSpacing(sheet.header, sheet.title, close = sheet.close, edgeToEdgeHeader = true)
        sheet.apply()
        assertSame(background, sheet.root.background)
        assertEquals(null, sheet.root.backgroundTintList)
        assertSame(buttonTint, sheet.secondary.backgroundTintList)
        assertSame(textColors, sheet.secondary.textColors)
        assertEquals(listOf(7, 9, 11, 13), listOf(sheet.root.paddingLeft, sheet.root.paddingTop, sheet.root.paddingRight, sheet.root.paddingBottom))
        listOf(sheet.close, sheet.header, sheet.title, sheet.message, sheet.primary, sheet.secondary).forEach {
            val margins = it.layoutParams as ViewGroup.MarginLayoutParams
            assertEquals(listOf(17, 23, 19, 29), listOf(margins.marginStart, margins.topMargin, margins.marginEnd, margins.bottomMargin))
        }
        assertEquals(listOf(1, 2, 3, 4), listOf(sheet.close.paddingLeft, sheet.close.paddingTop, sheet.close.paddingRight, sheet.close.paddingBottom))
    }

    @Test
    fun enabledAppliesFigmaSpacingTertiaryCloseAndSecondaryStyle() {
        listOf(false, true).forEach { dark ->
            listOf(false, true).forEach { buttons ->
                listOf(320, 800).forEach { width ->
                    val sheet = Sheet(context(true, dark, buttons))
                    val primaryTint = sheet.primary.backgroundTintList
                    val primaryHeight = sheet.primary.minHeight
                    sheet.apply()
                    sheet.measure(width)
                    assertEquals(sheet.dp(32), sheet.title.left)
                    assertEquals(sheet.dp(width - 32), sheet.title.right)
                    assertEquals(-sheet.dp(8), sheet.header.top - sheet.close.bottom)
                    assertEquals(sheet.dp(24), sheet.title.top - sheet.header.bottom)
                    assertEquals(sheet.dp(12), sheet.message.top - sheet.title.bottom)
                    assertEquals(sheet.dp(24), sheet.primary.top + sheet.dp(6) - sheet.message.bottom)
                    assertEquals(sheet.dp(8), sheet.secondary.top + sheet.dp(6) - (sheet.primary.bottom - sheet.dp(6)))
                    assertEquals(sheet.dp(24), sheet.root.height - (sheet.secondary.bottom - sheet.dp(6)))
                    assertEquals(sheet.context.getColorFromAttr(R.attr.daxColorSurfaceTertiary), sheet.root.backgroundTintList!!.defaultColor)
                    assertEquals(sheet.dp(16), sheet.close.top + sheet.close.paddingTop)
                    assertEquals(sheet.dp(16), sheet.root.width - sheet.close.right + sheet.close.paddingRight)
                    assertEquals(sheet.dp(20), sheet.close.top + (sheet.close.height - sheet.close.drawable.intrinsicHeight) / 2)
                    assertEquals(sheet.dp(20), sheet.root.width - sheet.close.right + (sheet.close.width - sheet.close.drawable.intrinsicWidth) / 2)
                    val reference = DaxButtonSecondary(sheet.context, null)
                    listOf(
                        reference.backgroundTintList to sheet.secondary.backgroundTintList,
                        reference.textColors to sheet.secondary.textColors,
                        reference.strokeColor to sheet.secondary.strokeColor,
                        reference.rippleColor to sheet.secondary.rippleColor,
                    ).forEach { (expected, actual) ->
                        listOf(
                            intArrayOf(android.R.attr.state_enabled),
                            intArrayOf(-android.R.attr.state_enabled),
                            intArrayOf(android.R.attr.state_enabled, android.R.attr.state_checked),
                            intArrayOf(android.R.attr.state_enabled, android.R.attr.state_pressed),
                            intArrayOf(android.R.attr.state_enabled, android.R.attr.state_focused),
                        ).forEach { state ->
                            assertEquals(expected?.getColorForState(state, 0), actual?.getColorForState(state, 0))
                        }
                    }
                    assertEquals(reference.strokeWidth, sheet.secondary.strokeWidth)
                    assertSame(primaryTint, sheet.primary.backgroundTintList)
                    assertEquals(primaryHeight, sheet.primary.minHeight)
                }
            }
        }
    }

    @Test
    fun secondaryDestructiveButtonKeepsItsStyle() {
        listOf(false, true).forEach { dark ->
            val sheet = Sheet(context(true, dark, buttons = true))
            val button = DaxButtonDestructiveSecondary(sheet.context, null)
            sheet.root.addView(button)
            val backgroundTint = button.backgroundTintList
            val textColors = button.textColors

            sheet.root.applyRebrandBottomSheetSpacing(sheet.header, sheet.title, secondary = listOf(button))

            assertSame(backgroundTint, button.backgroundTintList)
            assertSame(textColors, button.textColors)
            assertEquals(-sheet.dp(4), (button.layoutParams as ViewGroup.MarginLayoutParams).topMargin)
        }
    }

    @Test
    fun insetListenerCapturesRebrandedPaddingAndDoesNotAccumulate() {
        listOf(false, true).forEach { nested ->
            val sheet = Sheet(context(true))
            val outer = FrameLayout(sheet.context).apply { setPadding(0, 0, 0, sheet.dp(30)) }
            outer.addView(sheet.root)
            if (nested) {
                outer.applyRebrandBottomSheetSpacing(sheet.header, sheet.title, content = sheet.root)
            } else {
                sheet.apply()
            }
            val insetView = if (nested) outer else sheet.root
            insetView.applyBottomSystemBarInsetPadding()
            listOf(16, 32, 16).forEach { nav ->
                ViewCompat.dispatchApplyWindowInsets(
                    insetView,
                    WindowInsetsCompat.Builder().setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, 0, 0, sheet.dp(nav))).build(),
                )
                assertEquals(sheet.dp(18 + nav), sheet.root.paddingBottom + if (nested) outer.paddingBottom else 0)
            }
        }
    }

    @Test
    fun bottomOnlyButtonContainerSeparatesContentFromVisibleButton() {
        listOf(false, true).forEach { stacked ->
            val sheet = Sheet(context(true))
            sheet.root.removeAllViews()
            val root = ConstraintLayout(sheet.context)
            val content = LinearLayout(sheet.context).apply {
                id = View.generateViewId()
                orientation = LinearLayout.VERTICAL
            }
            listOf(sheet.header, sheet.title, sheet.message).forEach {
                content.addView(it, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, sheet.dp(40)))
            }
            val buttons = LinearLayout(sheet.context).apply {
                id = View.generateViewId()
                orientation = LinearLayout.VERTICAL
            }
            buttons.addView(sheet.primary, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, sheet.dp(48)))
            if (stacked) buttons.addView(sheet.secondary, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, sheet.dp(48)))
            root.addView(
                content,
                ConstraintLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                    bottomToTop = buttons.id
                    verticalBias = 0f
                },
            )
            root.addView(
                buttons,
                ConstraintLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                },
            )
            root.applyRebrandBottomSheetSpacing(
                sheet.header,
                sheet.title,
                listOf(sheet.message),
                buttons,
                if (stacked) listOf(sheet.secondary) else emptyList(),
                sections = listOf(content, buttons),
            )
            root.measure(
                View.MeasureSpec.makeMeasureSpec(sheet.dp(400), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            )
            root.layout(0, 0, root.measuredWidth, root.measuredHeight)
            assertEquals(sheet.dp(24), buttons.top + sheet.primary.top + sheet.dp(6) - (content.top + sheet.message.bottom))
            val last = if (stacked) sheet.secondary else sheet.primary
            assertEquals(sheet.dp(24), root.height - (buttons.top + last.bottom - sheet.dp(6)))
            if (stacked) assertEquals(sheet.dp(8), sheet.secondary.top - sheet.primary.bottom + sheet.dp(12))
        }
    }

    @Test
    fun edgeToEdgeArtworkStartsAtCloseBottomAndKeepsTextInsets() {
        listOf(false, true).forEach { dark ->
            listOf(0, 48).forEach { closeHeight ->
                val sheet = Sheet(context(true, dark))
                sheet.header.layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
                sheet.close.minimumHeight = sheet.dp(closeHeight)
                sheet.root.applyRebrandBottomSheetSpacing(sheet.header, sheet.title, close = sheet.close, edgeToEdgeHeader = true)
                sheet.measure(400)
                assertEquals(0, sheet.header.top - sheet.close.bottom)
                assertEquals(sheet.dp(56), sheet.header.top)
                assertEquals(sheet.dp(48), sheet.close.height)
                assertEquals(sheet.dp(48), sheet.close.width)
                assertEquals(0, sheet.header.left)
                assertEquals(sheet.root.width, sheet.header.right)
                assertEquals(sheet.dp(32), sheet.title.left)
                assertEquals(sheet.root.width - sheet.dp(32), sheet.title.right)
                assertEquals(sheet.dp(8), sheet.close.top)
                assertEquals(sheet.dp(8), sheet.root.width - sheet.close.right)
                assertEquals(sheet.dp(20), sheet.close.top + (sheet.close.height - sheet.close.drawable.intrinsicHeight) / 2)
                assertEquals(sheet.dp(20), sheet.root.width - sheet.close.right + (sheet.close.width - sheet.close.drawable.intrinsicWidth) / 2)
            }
        }
    }

    @Test
    fun sheetBackgroundIncludesInsetSpaceAndSurvivesCornerDrawableReplacement() {
        val sheet = Sheet(context(true))
        sheet.root.background = null
        sheet.apply()
        assertNull(sheet.root.background)
        val dialog = BottomSheetDialog(sheet.context, R.style.Widget_DuckDuckGo_BottomSheetDialog_EdgeToEdge)
        dialog.setContentView(sheet.root)
        dialog.show()
        dialog.setRoundCorners()
        shadowOf(Looper.getMainLooper()).idle()
        val surface = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)!!
        assertEquals(sheet.context.getColorFromAttr(R.attr.daxColorSurfaceTertiary), surface.backgroundTintList!!.defaultColor)
        assertTrue(surface.background is MaterialShapeDrawable)
        dialog.dismiss()
    }

    @Test
    fun physicalMarginsAreReplacedInRtlCompatibilityMode() {
        val context = context(true)
        val flags = context.applicationInfo.flags
        context.applicationInfo.flags = flags and ApplicationInfo.FLAG_SUPPORTS_RTL.inv()
        try {
            val sheet = Sheet(context)
            sheet.primary.layoutParams = LinearLayout.LayoutParams(
                context,
                Robolectric.buildAttributeSet()
                    .addAttribute(android.R.attr.layout_width, "match_parent")
                    .addAttribute(android.R.attr.layout_height, "40dp")
                    .addAttribute(android.R.attr.layout_marginHorizontal, "24dp")
                    .build(),
            )
            sheet.close.layoutParams = LinearLayout.LayoutParams(
                context,
                Robolectric.buildAttributeSet()
                    .addAttribute(android.R.attr.layout_width, "wrap_content")
                    .addAttribute(android.R.attr.layout_height, "wrap_content")
                    .addAttribute(android.R.attr.layout_marginHorizontal, "2dp")
                    .build(),
            ).apply {
                gravity = Gravity.END
            }
            assertFalse((sheet.primary.layoutParams as ViewGroup.MarginLayoutParams).isMarginRelative)
            assertFalse((sheet.close.layoutParams as ViewGroup.MarginLayoutParams).isMarginRelative)
            sheet.apply()
            sheet.measure(600)
            val primaryMargins = sheet.primary.layoutParams as ViewGroup.MarginLayoutParams
            val closeMargins = sheet.close.layoutParams as ViewGroup.MarginLayoutParams
            assertEquals(0, primaryMargins.leftMargin)
            assertEquals(0, primaryMargins.rightMargin)
            assertEquals(0, closeMargins.leftMargin)
            assertEquals(sheet.dp(8) - sheet.root.paddingEnd, closeMargins.rightMargin)
            assertEquals(sheet.dp(32), sheet.primary.left)
        } finally {
            context.applicationInfo.flags = flags
        }
    }

    @Test
    fun closePlacementFollowsRtlDirection() {
        val sheet = Sheet(context(true))
        sheet.context.applicationInfo.flags = sheet.context.applicationInfo.flags or ApplicationInfo.FLAG_SUPPORTS_RTL
        sheet.root.layoutDirection = View.LAYOUT_DIRECTION_RTL
        sheet.apply()
        sheet.measure(600)
        assertEquals(sheet.dp(16), sheet.close.left + sheet.close.paddingLeft)
        assertEquals(sheet.dp(32), sheet.title.left)
        assertEquals(sheet.dp(568), sheet.title.right)
    }

    @Test
    fun generatedPasswordCopyChainKeepsItsAnchorsAndSpacing() {
        val context = context(true)
        val root = ConstraintLayout(context)
        val header = ImageView(context).apply { id = View.generateViewId() }
        val title = DaxTextView(context, null).apply { id = View.generateViewId() }
        val password = DaxTextView(context, null).apply { id = View.generateViewId() }
        val copy = ImageView(context).apply { id = View.generateViewId() }
        root.addView(header, ConstraintLayout.LayoutParams(40, 40))
        root.addView(title, ConstraintLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(
            password,
            ConstraintLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, 40).apply {
                startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                endToStart = copy.id
            },
        )
        root.addView(
            copy,
            ConstraintLayout.LayoutParams(40, 40).apply {
                startToEnd = password.id
                endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                marginStart = 10
            },
        )
        root.applyRebrandBottomSheetSpacing(header, title, messages = listOf(password))
        assertEquals(copy.id, (password.layoutParams as ConstraintLayout.LayoutParams).endToStart)
        assertEquals(password.id, (copy.layoutParams as ConstraintLayout.LayoutParams).startToEnd)
        assertEquals(10, (copy.layoutParams as ConstraintLayout.LayoutParams).marginStart)
        assertFalse(root.clipToPadding)
    }

    @Test
    fun importStateOverlapsSharedCloseToolbarAndTintsExistingSheetBackground() {
        val sheet = Sheet(context(true))
        val toolbar = FrameLayout(sheet.context)
        sheet.root.setPadding(0, 0, 0, 0)
        sheet.root.removeView(sheet.close)
        toolbar.addView(sheet.close, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END))
        sheet.root.removeAllViews()
        sheet.root.addView(toolbar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, sheet.dp(56)))
        val state = LinearLayout(sheet.context).apply { orientation = LinearLayout.VERTICAL }
        sheet.root.addView(state)
        state.addView(sheet.header)
        state.addView(sheet.title)
        state.applyRebrandBottomSheetSpacing(sheet.header, sheet.title, close = sheet.close)
        assertEquals(-sheet.dp(56), (toolbar.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin)
        assertEquals(null, state.background)
        assertEquals(sheet.context.getColorFromAttr(R.attr.daxColorSurfaceTertiary), sheet.root.backgroundTintList!!.defaultColor)
        assertEquals(sheet.dp(48), state.paddingTop)
        assertEquals(sheet.dp(16), (sheet.close.layoutParams as ViewGroup.MarginLayoutParams).topMargin + sheet.close.paddingTop)
        var closed = false
        sheet.close.setOnClickListener { closed = true }
        state.setOnTouchListener { _, _ -> true }
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.setContentView(sheet.root)
        shadowOf(Looper.getMainLooper()).idle()
        sheet.measure(600)
        val x = toolbar.left + sheet.close.left + sheet.close.width / 2f
        val y = toolbar.top + sheet.close.top + sheet.close.height / 2f
        listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEach { action ->
            val event = MotionEvent.obtain(0, 1, action, x, y, 0)
            sheet.root.dispatchTouchEvent(event)
            event.recycle()
        }
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(closed)
        activity.finish()
    }

    @Test
    @Config(qualifiers = "sw600dp-w800dp-h1280dp")
    fun tabletSidesAreAlways64() {
        val sheet = Sheet(context(true))
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.setContentView(sheet.root)
        sheet.apply()
        listOf(320 to 64, 600 to 64, 620 to 64, 640 to 64, 800 to 64).forEach { (width, side) ->
            sheet.root.layoutParams = FrameLayout.LayoutParams(sheet.dp(width), ViewGroup.LayoutParams.WRAP_CONTENT)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(sheet.dp(width), sheet.root.width)
            assertEquals(sheet.dp(side), sheet.title.left)
            assertEquals(sheet.dp(width - side), sheet.title.right)
            assertEquals(sheet.dp(8), sheet.root.width - sheet.close.right)
        }
        activity.finish()
    }

    @Test
    fun fullHeightImportKeepsToolbarAndOriginalContentTopSpacing() {
        val sheet = Sheet(context(true))
        sheet.root.removeAllViews()
        val toolbar = FrameLayout(sheet.context)
        toolbar.addView(sheet.close)
        sheet.root.addView(toolbar, LinearLayout.LayoutParams(-1, sheet.dp(56)))
        val state = LinearLayout(sheet.context).apply { orientation = LinearLayout.VERTICAL }
        val section = LinearLayout(sheet.context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, sheet.dp(20), 0, 0)
        }
        section.addView(sheet.header)
        section.addView(sheet.title)
        state.addView(section)
        sheet.root.addView(state)
        state.applyRebrandBottomSheetSpacing(sheet.header, sheet.title, close = sheet.close, sections = listOf(section), fullHeight = true)
        assertEquals(sheet.dp(76), state.paddingTop)
        assertEquals(0, section.paddingTop)
        assertEquals(sheet.dp(24), (sheet.title.layoutParams as ViewGroup.MarginLayoutParams).topMargin)
    }

    @Test
    fun nestedConstraintContentUsesFixedSidesInsteadOfPercentGuidelines() {
        val context = context(true)
        val root = ConstraintLayout(context)
        val content = ConstraintLayout(context).apply { id = View.generateViewId() }
        root.addView(
            content,
            ConstraintLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                matchConstraintMaxWidth = 240
                topMargin = 40
            },
        )
        val header = ImageView(context).apply { id = View.generateViewId() }
        val title = DaxTextView(context, null).apply {
            id = View.generateViewId()
            text = "Title"
        }
        content.addView(header, ConstraintLayout.LayoutParams(40, 40).apply { topToTop = ConstraintLayout.LayoutParams.PARENT_ID })
        content.addView(title, ConstraintLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topToBottom = header.id })
        root.applyRebrandBottomSheetSpacing(header, title, content = content)
        val width = (800 * context.resources.displayMetrics.density).toInt()
        root.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        val side = (32 * context.resources.displayMetrics.density).toInt()
        assertEquals((30 * context.resources.displayMetrics.density).toInt(), content.top + header.top)
        assertEquals(side, content.left + title.left)
        assertEquals(width - side, content.left + title.right)
        assertEquals(0, (content.layoutParams as ConstraintLayout.LayoutParams).matchConstraintMaxWidth)
    }
}
