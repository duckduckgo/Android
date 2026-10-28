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

package com.duckduckgo.app.onboarding.ui.page.welcome

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.os.Looper
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.core.view.updateLayoutParams
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ContentOnboardingComposePageBinding
import com.duckduckgo.app.cta.ui.DaxBubbleCta.DaxDialogIntroOption
import com.duckduckgo.app.onboarding.orchestrator.NewUserOnboardingActivityDialog
import com.duckduckgo.app.onboarding.ui.page.OnboardingBackground
import com.duckduckgo.common.ui.compose.onboarding.DaxOnboardingDialogDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowChoreographer
import java.time.Duration
import kotlin.math.roundToInt
import com.duckduckgo.mobile.android.R as CommonR

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [28], qualifiers = "w480dp-h1040dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class ComposeOnboardingDialogRendererHostTest {
    private val resolver = DialogConfigResolver(mock())
    private val welcome = checkNotNull(resolver.resolve(NewUserOnboardingActivityDialog.InitialReinstallUser, isCustomAiFlow = false))
    private val comparison = checkNotNull(resolver.resolve(NewUserOnboardingActivityDialog.ComparisonChart, isCustomAiFlow = false))

    @Test
    fun tailStaysVisibleWhileWelcomeChangesToComparison() {
        withRenderer { renderer, binding ->
            renderer.render("welcome", welcome, animate = false)
            advance(500)
            assertTailVisible(binding, "before changing content")

            renderer.render("comparison", comparison, animate = true)
            repeat(30) { frame ->
                advance(16)
                assertTailVisible(binding, "at frame $frame of the comparison transition")
            }
        }
    }

    @Test
    fun welcomeToComparisonMovesTheCardAnchorAndCanBeSkippedAndReleased() {
        withRenderer { renderer, binding ->
            renderer.render("welcome", welcome, animate = false)
            advance(500)
            assertCardLaidOut(binding)
            val start = bounds(binding)

            renderer.render("comparison", comparison, animate = true)
            advance(DaxOnboardingDialogDefaults.BoundsAnimationDuration.inWholeMilliseconds / 2)
            val middle = bounds(binding)
            advance(5_000)
            val end = bounds(binding)
            assertBetween("top", middle.top, start.top, end.top)

            renderer.render("welcome-again", welcome, animate = true)
            advance(100)
            renderer.skipRunningAnimations()
            advance(100)
            val skipped = bounds(binding)
            advance(1_000)
            assertEquals("Skipping must settle both card edges", skipped, bounds(binding))

            renderer.render("comparison-again", comparison, animate = true)
            advance(100)
            val host = binding.root.parent as ViewGroup
            host.removeView(binding.root)
            try {
                assertNull("Release must work after the View root loses its window", binding.daxDialogCta.windowId)
                renderer.release()
            } finally {
                host.addView(binding.root)
            }
            assertTrue("Releasing must remove the Compose card", binding.daxDialogCta.children.none { it is ComposeView })
            val resizedHeight = binding.root.height - 100
            binding.root.updateLayoutParams { height = resizedHeight }
            advance(100)
            assertEquals("The host must still honor a resize after release", resizedHeight, binding.root.height)
        }
    }

    @Test
    @Config(sdk = [30], qualifiers = "w384dp-h811dp-450dpi")
    fun boundedComparisonKeepsItsBodyViewportToTheStageEdge() {
        RuntimeEnvironment.setFontScale(1.5f)
        withRenderer { renderer, binding ->
            renderer.render("bounded-comparison", comparison, animate = false)
            advance(1_000)
            assertCardLaidOut(binding)
            val dialog = binding.daxDialogCta
            val composeView = dialog.children.filterIsInstance<ComposeView>().single()
            val metrics = binding.root.resources.displayMetrics
            val tail = (30 * metrics.density).toInt() + (1.5f * metrics.density).roundToInt()
            val hostParams = dialog.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            val childParams = composeView.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            assertTrue("The large-font comparison must exercise the bounded host", hostParams.height >= 0)
            assertEquals(
                "host=${dialog.height}, padding=${dialog.paddingTop}, hostHeight=${hostParams.height}, " +
                    "childHeight=${childParams.height}, childMax=${childParams.matchConstraintMaxHeight}, " +
                    "constrained=${childParams.constrainedHeight}",
                dialog.height - dialog.paddingTop + tail,
                composeView.height,
            )
        }
    }

    @Test
    @Config(qualifiers = "w480dp-h600dp-mdpi")
    fun composeFieldOwnsKeyboardInsetsAtAutofocusThreshold() {
        withRenderer { renderer, binding ->
            renderer.render(
                "preview",
                welcome.copy(
                    content = ContentConfig.InputScreenPreview(
                        TextConfig.Literal("Try it"),
                        true,
                        true,
                        emptyList(),
                        emptyList(),
                    ),
                ),
                animate = false,
            )
            advance(500)
            assertCardLaidOut(binding)
            assertTrue(
                "The platform must recognize the Compose card as a resizable scroll container",
                binding.daxDialogCta.children.filterIsInstance<ComposeView>().single().isScrollContainer,
            )
            assertNotNull("Compose field focus at ${binding.root.resources.configuration.screenHeightDp} dp", binding.daxDialogCta.findFocus())
            val open = WindowInsetsCompat.Builder().setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, 24, 0, 280))
                .setVisible(WindowInsetsCompat.Type.ime(), true).build()
            assertEquals(296, binding.daxDialogCta.applyOnboardingDialogInsets(open, 16))
            val windowHeight = binding.root.height
            binding.root.updateLayoutParams { height = windowHeight - 280 }
            ViewCompat.dispatchApplyWindowInsets(binding.daxDialogCta, open)
            advance(500)
            assertCardLaidOut(binding)
            val keyboardHeight = binding.daxDialogCta.height
            assertTrue(
                "The card must fit above the keyboard: height=$keyboardHeight, params=${binding.daxDialogCta.layoutParams}",
                keyboardHeight <= 280,
            )
            assertEquals(24, (binding.daxDialogCta.layoutParams as ViewGroup.MarginLayoutParams).topMargin)
            val closed = WindowInsetsCompat.Builder().setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, 24, 0, 20))
                .setVisible(WindowInsetsCompat.Type.ime(), false).build()
            assertEquals(36, binding.daxDialogCta.applyOnboardingDialogInsets(closed, 296))
            binding.root.updateLayoutParams { height = windowHeight }
            ViewCompat.dispatchApplyWindowInsets(binding.daxDialogCta, closed)
            advance(500)
            assertTrue("Closing the keyboard must release the bounded card", binding.daxDialogCta.height >= keyboardHeight)
        }
    }

    @Test
    fun focusedChatEditorStaysInsideViewportWhenOpenKeyboardAndWindowShrink() {
        withRenderer(windowFocused = true) { renderer, binding ->
            renderer.render(
                "preview",
                DialogConfig(
                    background = OnboardingBackground.Shoreline,
                    content = ContentConfig.InputScreenPreview(
                        TextConfig.Resource(R.string.preOnboardingInputModeDemoTitle),
                        false,
                        true,
                        emptyList(),
                        emptyList(),
                    ),
                ),
                animate = false,
            )
            advance(500)
            val card = binding.daxDialogCta.children.filterIsInstance<ComposeView>().single()
            val owner = card.children.filterIsInstance<ViewRootForTest>().single()
            val editor = owner.semanticsOwner.getAllSemanticsNodes(mergingEnabled = false).single { SemanticsActions.SetText in it.config }
            assertTrue(editor.config[SemanticsActions.RequestFocus].action!!.invoke())
            advance(16)
            assertFocusedEditorInsideViewport(binding)
            val open = WindowInsetsCompat.Builder().setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, 24, 0, 20))
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, 280))
                .setVisible(WindowInsetsCompat.Type.ime(), true).build()
            val windowHeight = binding.root.height
            binding.root.updateLayoutParams { height = windowHeight - 280 }
            ViewCompat.dispatchApplyWindowInsets(binding.daxDialogCta, open)
            advance(500)
            assertFocusedEditorInsideViewport(binding)
            binding.root.updateLayoutParams { height = 480 - 280 }
            advance(1_000)
            assertFocusedEditorInsideViewport(binding)
        }
    }

    private fun assertFocusedEditorInsideViewport(binding: ContentOnboardingComposePageBinding) {
        val host = binding.daxDialogCta
        val params = host.layoutParams as ViewGroup.MarginLayoutParams
        val bottom = binding.root.height - binding.root.paddingBottom - params.bottomMargin
        assertTrue("Dialog bottom=${host.bottom} must fit the remaining host span=$bottom", host.bottom <= bottom)
        val card = binding.daxDialogCta.children.filterIsInstance<ComposeView>().single()
        val root = card.children.filterIsInstance<ViewRootForTest>().single()
        val nodes = root.semanticsOwner.getAllSemanticsNodes(mergingEnabled = false)
        val editors = nodes.filter { SemanticsActions.SetText in it.config && it.config.getOrNull(SemanticsProperties.Focused) == true }
        assertEquals("The real editor must retain focus", 1, editors.size)
        val editor = editors.single()
        val viewport = nodes.single { SemanticsProperties.VerticalScrollAxisRange in it.config }.boundsInRoot
        val top = editor.positionInRoot.y
        val editorBottom = top + editor.size.height
        assertTrue("The focused editor must have real bounds", editor.size.height > 0)
        assertTrue("Focused editor top=$top must be inside viewport=$viewport", top >= viewport.top)
        val scrollRange = nodes.single { SemanticsProperties.VerticalScrollAxisRange in it.config }
            .config[SemanticsProperties.VerticalScrollAxisRange]
        assertTrue("Focused editor bottom=$editorBottom must be inside viewport=$viewport, scroll=$scrollRange", editorBottom <= viewport.bottom)
    }

    @Test
    @Config(sdk = [30], qualifiers = "w384dp-h480dp-mdpi")
    fun focusedChatEditorStaysVisibleWhenInsetsBoundCardInsideFullHeightWindow() {
        withRenderer(windowFocused = true) { renderer, binding ->
            renderer.render(
                "preview",
                DialogConfig(
                    background = OnboardingBackground.Shoreline,
                    content = ContentConfig.InputScreenPreview(
                        TextConfig.Resource(R.string.preOnboardingInputModeDemoTitle),
                        true,
                        true,
                        emptyList(),
                        emptyList(),
                    ),
                ),
                animate = false,
            )
            advance(500)
            val card = binding.daxDialogCta.children.filterIsInstance<ComposeView>().single()
            val owner = card.children.filterIsInstance<ViewRootForTest>().single()
            val chat = owner.semanticsOwner.getAllSemanticsNodes(mergingEnabled = false).single {
                it.config.getOrNull(SemanticsProperties.Selected) == false
            }
            assertTrue(chat.config[SemanticsActions.OnClick].action!!.invoke())
            advance(500)
            val editor = owner.semanticsOwner.getAllSemanticsNodes(mergingEnabled = false).single { SemanticsActions.SetText in it.config }
            assertTrue(editor.config[SemanticsActions.RequestFocus].action!!.invoke())
            advance(16)
            assertFocusedEditorInsideViewport(binding)

            val windowHeight = binding.root.height
            val open = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, 28, 0, 306))
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, 306))
                .setVisible(WindowInsetsCompat.Type.ime(), true).build()
            ViewCompat.dispatchApplyWindowInsets(binding.daxDialogCta, open)
            advance(1_000)

            assertEquals("The edge-to-edge root must keep its full height", windowHeight, binding.root.height)
            assertTrue(
                "The card must be bounded by the IME: root=$windowHeight, card=${bounds(binding)}, " +
                    "margin=${(binding.daxDialogCta.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin}",
                binding.daxDialogCta.height < 150,
            )
            assertFocusedEditorInsideViewport(binding)

            val keyboardHeight = binding.daxDialogCta.height
            val closed = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, 28, 0, 20))
                .setVisible(WindowInsetsCompat.Type.ime(), false).build()
            ViewCompat.dispatchApplyWindowInsets(binding.daxDialogCta, closed)
            advance(500)

            assertEquals(windowHeight, binding.root.height)
            assertTrue("Closing the keyboard must release the bounded card", binding.daxDialogCta.height > keyboardHeight)
            assertFocusedEditorInsideViewport(binding)
        }
    }

    @Test
    @Config(sdk = [30], qualifiers = "w384dp-h480dp-mdpi")
    fun contentThatGrowsWithKeyboardOpenRegainsFullViewportAfterKeyboardHides() {
        assertEditedContentRestoresViewport(animate = false)
    }

    @Test
    @Config(sdk = [30], qualifiers = "w384dp-h480dp-mdpi")
    fun animatedContentThatGrowsWithKeyboardOpenRegainsFullViewportAfterKeyboardHides() {
        assertEditedContentRestoresViewport(animate = true)
    }

    private fun assertEditedContentRestoresViewport(animate: Boolean) {
        val config = DialogConfig(
            background = OnboardingBackground.Shoreline,
            content = ContentConfig.InputScreenPreview(
                TextConfig.Resource(R.string.preOnboardingInputModeDemoTitle),
                false,
                true,
                emptyList(),
                (1..3).map { DaxDialogIntroOption("Chat suggestion $it", CommonR.drawable.ic_find_search_16, "Suggestion $it") },
            ),
        )
        val editedText = AnnotatedString("One\nTwo\nThree\nFour")
        val closed = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, 28, 0, 48))
            .setVisible(WindowInsetsCompat.Type.ime(), false).build()
        var expectedViewportHeight = 0f
        withRenderer(windowFocused = true) { renderer, binding ->
            renderer.render("preview", config, animate = false)
            ViewCompat.dispatchApplyWindowInsets(binding.daxDialogCta, closed)
            advance(500)
            val card = binding.daxDialogCta.children.filterIsInstance<ComposeView>().single()
            val owner = card.children.filterIsInstance<ViewRootForTest>().single()
            val editor = owner.semanticsOwner.getAllSemanticsNodes(mergingEnabled = false).single { SemanticsActions.SetText in it.config }
            assertTrue(editor.config[SemanticsActions.RequestFocus].action!!.invoke())
            assertTrue(editor.config[SemanticsActions.SetText].action!!.invoke(editedText))
            advance(500)
            expectedViewportHeight = owner.semanticsOwner.getAllSemanticsNodes(mergingEnabled = false).single {
                SemanticsProperties.VerticalScrollAxisRange in it.config
            }.boundsInRoot.height
        }
        withRenderer(windowFocused = true) { renderer, binding ->
            renderer.render("preview", config, animate = animate)
            ViewCompat.dispatchApplyWindowInsets(binding.daxDialogCta, closed)
            advance(5_000)
            renderer.skipRunningAnimations()
            advance(500)
            val card = binding.daxDialogCta.children.filterIsInstance<ComposeView>().single()
            val owner = card.children.filterIsInstance<ViewRootForTest>().single()
            fun nodes() = owner.semanticsOwner.getAllSemanticsNodes(mergingEnabled = false)
            fun viewport() = nodes().single { SemanticsProperties.VerticalScrollAxisRange in it.config }
            val initialHeight = viewport().boundsInRoot.height
            val editor = nodes().single { SemanticsActions.SetText in it.config }
            assertTrue(editor.config[SemanticsActions.RequestFocus].action!!.invoke())
            advance(16)
            val open = WindowInsetsCompat.Builder(closed)
                .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, 28, 0, 306))
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, 306))
                .setVisible(WindowInsetsCompat.Type.ime(), true).build()
            ViewCompat.dispatchApplyWindowInsets(binding.daxDialogCta, open)
            advance(1_000)
            assertTrue("IME must shrink the viewport", viewport().boundsInRoot.height < initialHeight)
            assertFocusedEditorInsideViewport(binding)
            assertTrue(editor.config[SemanticsActions.SetText].action!!.invoke(editedText))
            advance(500)

            ViewCompat.dispatchApplyWindowInsets(binding.daxDialogCta, closed)
            advance(1_000)
            assertEquals(
                "Restoration must match the same edited content laid out with IME closed",
                expectedViewportHeight,
                viewport().boundsInRoot.height,
            )
            assertFocusedEditorInsideViewport(binding)
            assertEquals(editedText, nodes().single { SemanticsActions.SetText in it.config }.config[SemanticsProperties.EditableText])
            val viewportBounds = viewport().boundsInRoot
            assertEquals("The scrolling and painted card must share a bottom edge", card.height.toFloat(), viewportBounds.bottom)
            val suggestions = nodes().filter { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { it.text.startsWith("Chat suggestion") } == true
            }
            assertEquals("All chat suggestions must remain present", 3, suggestions.size)
            for (index in suggestions.indices) {
                val label = "Chat suggestion ${index + 1}"
                fun suggestion() = nodes().single { node -> node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == label } == true }
                val target = suggestion()
                val offset = target.positionInRoot.y + target.size.height - viewport().boundsInRoot.bottom
                if (offset > 0f) {
                    assertTrue(viewport().config[SemanticsActions.ScrollBy].action!!.invoke(0f, offset))
                    advance(500)
                }
                val visible = suggestion()
                assertTrue("$label must be reachable", visible.positionInRoot.y >= viewport().boundsInRoot.top)
                assertTrue(
                    "$label must fit inside the painted viewport",
                    visible.positionInRoot.y + visible.size.height <= viewport().boundsInRoot.bottom,
                )
            }
        }
    }

    @Test
    @Config(qualifiers = "w480dp-h599dp-mdpi")
    fun shortComposeHostDoesNotTakeKeyboardFocus() {
        withRenderer { renderer, binding ->
            renderer.render(
                "preview",
                welcome.copy(
                    content = ContentConfig.InputScreenPreview(
                        TextConfig.Literal("Try it"),
                        true,
                        true,
                        emptyList(),
                        emptyList(),
                    ),
                ),
                animate = false,
            )
            advance(500)
            assertNull(binding.daxDialogCta.findFocus())
        }
    }

    @Test
    @Config(qualifiers = "w800dp-h480dp-land-mdpi")
    fun landscapeHostKeepsExteriorViewsAndRendersComposeCard() {
        withRenderer { renderer, binding ->
            renderer.render("welcome", welcome, animate = false)
            advance(500)
            assertCardLaidOut(binding)
        }
    }

    @Test
    @Config(qualifiers = "sw600dp-w800dp-h1000dp-mdpi")
    fun tabletHostKeepsExteriorViewsAndRendersComposeCard() {
        withRenderer { renderer, binding ->
            renderer.render("welcome", welcome, animate = false)
            advance(500)
            assertCardLaidOut(binding)
        }
    }

    private fun withRenderer(
        windowFocused: Boolean = false,
        block: (ComposeOnboardingDialogRenderer, ContentOnboardingComposePageBinding) -> Unit,
    ) {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
        val controller = Robolectric.buildActivity(ComponentActivity::class.java)
        val activity = controller.get()
        activity.setTheme(CommonR.style.Theme_DuckDuckGo_Light_Onboarding)
        controller.setup()
        val binding = ContentOnboardingComposePageBinding.inflate(LayoutInflater.from(activity))
        assertEquals("The lean XML host must not inflate any legacy dialog content", 0, binding.daxDialogCta.childCount)
        activity.setContentView(binding.root)
        controller.visible()
        advance(100)
        if (windowFocused) {
            controller.windowFocusChanged(true)
            advance(16)
        }
        var bottomInset = 0
        ViewCompat.setOnApplyWindowInsetsListener(binding.daxDialogCta) { view, insets ->
            bottomInset = view.applyOnboardingDialogInsets(insets, bottomInset)
            insets
        }
        val renderer = ComposeOnboardingDialogRenderer(
            binding = binding,
            contentValues = ContentValueStore(),
            onBeforeContentBound = { _, _ -> },
            isLightMode = { true },
            isAddressBarRebrandEnabled = { true },
            isTablet = binding.root.resources.configuration.smallestScreenWidthDp >= 600,
            cardBottomInsetPx = { bottomInset },
            emit = {},
            execute = {},
        )
        try {
            assertEquals(1, binding.daxDialogCta.childCount)
            assertTrue(binding.daxDialogCta.getChildAt(0) is ComposeView)
            block(renderer, binding)
        } finally {
            renderer.release()
            controller.pause().stop().destroy()
            // Finish cancellation on the cached Compose dispatcher before Robolectric resets the looper.
            Snapshot.sendApplyNotifications()
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    private fun advance(milliseconds: Long) {
        repeat(((milliseconds + 15) / 16).toInt()) {
            Snapshot.sendApplyNotifications()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
        }
    }

    private fun assertCardLaidOut(binding: ContentOnboardingComposePageBinding) {
        val card = binding.daxDialogCta.children.filterIsInstance<ComposeView>().single()
        assertTrue(
            "Compose card=${card.width}x${card.height}, host=${binding.daxDialogCta.width}x${binding.daxDialogCta.height}, " +
                "root=${binding.root.width}x${binding.root.height}",
            card.height > 100,
        )
    }

    private fun assertTailVisible(binding: ContentOnboardingComposePageBinding, moment: String) {
        assertCardLaidOut(binding)
        val card = binding.daxDialogCta.children.filterIsInstance<ComposeView>().single()
        // ChangeBounds can temporarily make the host shorter than the painted bubble; preserve its overflow.
        val pixels = Bitmap.createBitmap(card.width, binding.root.height, Bitmap.Config.ARGB_8888)
        card.draw(Canvas(pixels))
        try {
            fun isStroke(x: Int, y: Int): Boolean {
                val pixel = pixels.getPixel(x, y)
                return Color.alpha(pixel) > 128 && Color.blue(pixel) - Color.red(pixel) >= 18 && Color.green(pixel) - Color.red(pixel) >= 8
            }
            val bodyBottom = (0 until pixels.height).last { y -> isStroke(card.width / 10, y) }
            val tailPainted = (bodyBottom + 8 until minOf(bodyBottom + 29, pixels.height)).any { y ->
                (16 until card.width - 16).any { x ->
                    isStroke(x, y)
                }
            }
            assertTrue("The bubble tail must stay painted $moment", tailPainted)
        } finally {
            pixels.recycle()
        }
    }

    private fun bounds(binding: ContentOnboardingComposePageBinding): Rect = binding.daxDialogCta.let {
        Rect(it.left, it.top, it.right, it.bottom)
    }

    private fun assertBetween(edge: String, actual: Int, start: Int, end: Int) {
        assertTrue("Card $edge must change between welcome and comparison: $start -> $end", start != end)
        assertTrue(
            "Card $edge must interpolate at half duration: $start -> $actual -> $end",
            actual > minOf(start, end) && actual < maxOf(start, end),
        )
    }
}
