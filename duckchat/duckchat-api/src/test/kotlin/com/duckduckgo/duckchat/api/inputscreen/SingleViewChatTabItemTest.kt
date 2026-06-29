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

package com.duckduckgo.duckchat.api.inputscreen

import android.view.View
import android.view.ViewGroup
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SingleViewChatTabItemTest {

    private class TestItem(visible: Flow<Boolean>, scope: CoroutineScope) : SingleViewChatTabItem(visible, scope) {
        override fun onCreateView(parent: ViewGroup): View = View(parent.context)
        fun dismiss() = hide()
    }

    private fun NativeInputChatTabItem.rows(): Int = adapters.single().itemCount

    @Test
    fun whenVisibleTrueThenRowShown() = runTest {
        val item = TestItem(MutableStateFlow(true), CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

        assertEquals(1, item.rows())
    }

    @Test
    fun whenVisibleFalseThenNoRow() = runTest {
        val item = TestItem(MutableStateFlow(false), CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

        assertEquals(0, item.rows())
    }

    @Test
    fun whenVisibleTogglesThenRowToggles() = runTest {
        val visible = MutableStateFlow(true)
        val item = TestItem(visible, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        assertEquals(1, item.rows())

        visible.value = false
        assertEquals(0, item.rows())

        visible.value = true
        assertEquals(1, item.rows())
    }

    @Test
    fun whenDismissedThenStaysHiddenEvenWhenVisibleTrue() = runTest {
        val visible = MutableStateFlow(true)
        val item = TestItem(visible, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        assertEquals(1, item.rows())

        item.dismiss()
        assertEquals(0, item.rows())

        // A later visible=true must not bring it back.
        visible.value = false
        visible.value = true
        assertEquals(0, item.rows())
    }
}
