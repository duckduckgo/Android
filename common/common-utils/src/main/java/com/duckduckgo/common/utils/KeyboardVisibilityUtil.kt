/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.common.utils

import android.graphics.Rect
import android.view.View
import android.view.ViewTreeObserver
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import logcat.logcat

class KeyboardVisibilityUtil(private val rootView: View) {

    fun addKeyboardVisibilityListener(onKeyboardVisible: () -> Unit) {
        rootView.viewTreeObserver.addOnGlobalLayoutListener(
            object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    if (rootView.isKeyboardVisible()) {
                        rootView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                        onKeyboardVisible()
                    }
                }
            },
        )
    }

}

fun View.keyboardVisibilityFlow(): Flow<Boolean> = callbackFlow {
    val layoutObserver = ViewTreeObserver.OnGlobalLayoutListener {
        val isVisible = isKeyboardVisible()
        trySend(isVisible)
    }

    viewTreeObserver.addOnGlobalLayoutListener(layoutObserver)

    awaitClose {
        viewTreeObserver.removeOnGlobalLayoutListener(layoutObserver)
    }
}

private fun View.isKeyboardVisible(): Boolean {
    val rect = Rect()
    getWindowVisibleDisplayFrame(rect)
    val screenHeight = height
    val keypadHeight = screenHeight - rect.bottom
    return keypadHeight > screenHeight * KEYBOARD_VISIBILITY_THRESHOLD
}

private const val KEYBOARD_VISIBILITY_THRESHOLD = 0.15
