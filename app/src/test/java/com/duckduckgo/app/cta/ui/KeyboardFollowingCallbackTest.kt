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

import android.app.Activity
import android.view.View
import android.widget.FrameLayout
import androidx.core.graphics.Insets
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

@RunWith(AndroidJUnit4::class)
class KeyboardFollowingCallbackTest {

    private val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    private val parent = FrameLayout(activity)
    private val dax = View(activity)

    init {
        parent.addView(dax)
        activity.setContentView(parent)
        dax.layout(0, 0, 100, 200)
        dax.translationY = 10f
    }

    private var layoutInset = 1000
    private var endedCount = 0

    private val callback = KeyboardFollowingCallback(
        views = { listOf(dax) },
        layoutBottomInset = { layoutInset },
        onStarted = {},
        onEnded = { endedCount++ },
    )

    private fun imeAnimation() = WindowInsetsAnimationCompat(WindowInsetsCompat.Type.ime(), null, 300)

    private fun progress(animation: WindowInsetsAnimationCompat, imeBottom: Int) {
        val insets = WindowInsetsCompat.Builder().setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, imeBottom)).build()
        callback.onProgress(insets, mutableListOf(animation))
    }

    @Test
    fun whenAnimating_thenFollowsKeyboard() {
        val animation = imeAnimation()
        callback.onPrepare(animation)
        progress(animation, imeBottom = 400)

        assertEquals(610f, dax.translationY)
    }

    @Test
    fun whenRestoredMidAnimation_thenEverythingIsPutBack() {
        val animation = imeAnimation()
        callback.onPrepare(animation)
        progress(animation, imeBottom = 400)

        callback.restore()

        assertEquals(10f, dax.translationY)
        assertEquals(1, endedCount)
    }

    @Test
    fun whenAnimationsOverlap_thenRestoresTheFirstBaselineOnlyAfterTheLastEnds() {
        val first = imeAnimation()
        callback.onPrepare(first)
        progress(first, imeBottom = 400)
        val second = imeAnimation()
        callback.onPrepare(second)

        callback.onEnd(first)
        assertEquals(0, endedCount)

        callback.onEnd(second)
        assertEquals(10f, dax.translationY)
        assertEquals(1, endedCount)
    }

    @Test
    fun whenRestoredWithoutAnimation_thenNothingHappens() {
        callback.restore()

        assertEquals(10f, dax.translationY)
        assertEquals(0, endedCount)
    }
}
