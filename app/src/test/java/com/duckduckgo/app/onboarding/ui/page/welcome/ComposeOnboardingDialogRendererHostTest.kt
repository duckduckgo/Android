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
import android.graphics.Rect
import android.os.Looper
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.children
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.browser.databinding.ContentOnboardingWelcomePageBinding
import com.duckduckgo.app.onboarding.orchestrator.NewUserOnboardingActivityDialog
import com.duckduckgo.common.ui.compose.onboarding.DaxOnboardingDialogDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowChoreographer
import java.time.Duration
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
                assertNull("Release must work after the View root loses its window", binding.daxDialogCta.root.windowId)
                renderer.release()
            } finally {
                host.addView(binding.root)
            }
            assertTrue("Releasing must remove the Compose card", binding.daxDialogCta.root.children.none { it is ComposeView })
            val resizedHeight = binding.root.height - 100
            binding.root.updateLayoutParams { height = resizedHeight }
            advance(100)
            assertEquals("The host must still honor a resize after release", resizedHeight, binding.root.height)
        }
    }

    private fun withRenderer(block: (ComposeOnboardingDialogRenderer, ContentOnboardingWelcomePageBinding) -> Unit) {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
        val controller = Robolectric.buildActivity(ComponentActivity::class.java)
        val activity = controller.get()
        activity.setTheme(CommonR.style.Theme_DuckDuckGo_Light_Onboarding)
        controller.setup()
        val binding = ContentOnboardingWelcomePageBinding.inflate(LayoutInflater.from(activity))
        // The replaced View card contains an unrelated input picker that requires app injection on attach.
        binding.daxDialogCta.cardView.removeAllViews()
        activity.setContentView(binding.root)
        controller.visible()
        advance(100)
        val renderer = ComposeOnboardingDialogRenderer(
            binding = binding,
            contentValues = ContentValueStore(),
            onBeforeContentBound = { _, _ -> },
            isLightMode = { true },
            isAddressBarRebrandEnabled = { true },
            isTablet = false,
            cardBottomInsetPx = { 0 },
            emit = {},
            execute = {},
            coroutineScope = activity.lifecycleScope,
        )
        try {
            block(renderer, binding)
        } finally {
            renderer.release()
            controller.pause().stop().destroy()
        }
    }

    private fun advance(milliseconds: Long) {
        repeat(((milliseconds + 15) / 16).toInt()) {
            Snapshot.sendApplyNotifications()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
        }
    }

    private fun assertCardLaidOut(binding: ContentOnboardingWelcomePageBinding) {
        val card = binding.daxDialogCta.root.children.filterIsInstance<ComposeView>().single()
        assertTrue("The actual Compose card must be laid out before testing its transition", card.height > 100)
    }

    private fun bounds(binding: ContentOnboardingWelcomePageBinding): Rect = binding.daxDialogCta.root.let {
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
