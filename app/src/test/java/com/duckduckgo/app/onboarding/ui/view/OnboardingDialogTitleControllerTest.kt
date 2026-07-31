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

package com.duckduckgo.app.onboarding.ui.view

import android.os.Looper
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.common.ui.view.TypeAnimationTextView
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class OnboardingDialogTitleControllerTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val animatedText = TypeAnimationTextView(context)
    private val sizingText = TextView(context)
    private val controller = OnboardingDialogTitleController(animatedText, sizingText)

    private fun letTypingRun() = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))

    @Test
    fun `when title set then sizing text reserves space for the decoded title`() {
        controller.setTitle("Ready to get started?<br/>Try a search or AI chat!")

        assertEquals("Ready to get started?\nTry a search or AI${NBSP}chat!", sizingText.text.toString())
    }

    @Test
    fun `when title set then the last space becomes non-breaking`() {
        controller.setTitle("Where should I put your address bar?")

        assertEquals("Where should I put your address${NBSP}bar?", sizingText.text.toString())
    }

    @Test
    fun `when title has a single space then it is left alone`() {
        controller.setTitle("Hi there.")

        assertEquals("Hi there.", sizingText.text.toString())
    }

    @Test
    fun `when title set then animated text is cleared`() {
        controller.setTitle("Protections activated!")
        controller.snapTitle()

        controller.setTitle("Where should I put your address bar?")

        assertEquals("", animatedText.text.toString())
    }

    @Test
    fun `when title snapped then animated text shows the whole decoded title`() {
        controller.setTitle("Welcome back!<br/>Want to customize anything?")

        controller.snapTitle()

        assertEquals("Welcome back!\nWant to customize${NBSP}anything?", animatedText.text.toString())
    }

    @Test
    fun `when typing finished early then animated text shows the whole decoded title`() {
        controller.setTitle("Welcome back!<br/>Want to customize anything?")

        controller.typeTitle()
        controller.finishTyping()

        assertEquals("Welcome back!\nWant to customize${NBSP}anything?", animatedText.text.toString())
    }

    @Test
    fun `when typing finished early then onFinished is invoked`() {
        controller.setTitle("Protections activated!")
        var finished = false

        controller.typeTitle { finished = true }
        controller.finishTyping()

        assertEquals(true, finished)
    }

    @Test
    fun `when typing never started then finishTyping leaves the title alone`() {
        controller.setTitle("Protections activated!")

        controller.finishTyping()

        assertEquals("", animatedText.text.toString())
    }

    @Test
    fun `when title set while typing then the superseded animation paints nothing`() {
        controller.setTitle("Protections activated!")
        controller.typeTitle()

        controller.setTitle("Where should I put your address bar?")
        letTypingRun()

        assertEquals("", animatedText.text.toString())
    }

    @Test
    fun `when animation cancelled then typing paints nothing further`() {
        controller.setTitle("Protections activated!")
        controller.typeTitle()

        controller.cancelAnimation()
        letTypingRun()

        assertEquals("", animatedText.text.toString())
    }

    @Test
    fun `when typing runs to completion then animated text shows the whole decoded title`() {
        controller.setTitle("Welcome back!<br/>Want to customize anything?")

        controller.typeTitle()
        letTypingRun()

        assertEquals("Welcome back!\nWant to customize${NBSP}anything?", animatedText.text.toString())
    }

    private companion object {
        const val NBSP = '\u00A0'
    }
}
