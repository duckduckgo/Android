/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.app.global

import android.view.Window

class WindowDelegateCallback constructor(
    private val delegate: Window.Callback
) : Window.Callback by delegate {

    val onContentChangedCallbacks = mutableListOf<() -> Boolean>()

    override fun onContentChanged() {
        onContentChangedCallbacks.removeAll { callback ->
            !callback()
        }
        delegate.onContentChanged()
    }

    companion object {
        fun Window.onDecorViewReady(callback: () -> Unit) {
            if (peekDecorView() == null) {
                onContentChanged {
                    callback()
                    return@onContentChanged false
                }
            } else {
                callback()
            }
        }

        fun Window.onContentChanged(block: () -> Boolean) {
            val callback = wrapCallback()
            callback.onContentChangedCallbacks += block
        }

        private fun Window.wrapCallback(): WindowDelegateCallback {
            val currentCallback = callback
            return if (currentCallback is WindowDelegateCallback) {
                currentCallback
            } else {
                val newCallback = WindowDelegateCallback(currentCallback)
                callback = newCallback
                newCallback
            }
        }
    }
}