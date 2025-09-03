/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.app.tabs.ui

import android.view.View
import android.widget.TextView
import com.google.android.material.R as materialR
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar

class DefaultSnackbar(
    parentView: View,
    message: String,
    private val anchor: View? = null,
    private val action: String? = null,
    private val showAction: Boolean = false,
    private val onAction: () -> Unit = {},
    private val onDismiss: () -> Unit = {},
) {
    companion object {
        private const val SNACKBAR_DISPLAY_TIME_MS = 3500
    }

    private val snackbar = Snackbar.make(parentView, message, Snackbar.LENGTH_LONG)
        .setDuration(SNACKBAR_DISPLAY_TIME_MS)
        .apply {
            if (showAction) {
                setAction(action) {
                    // noop, handled in onDismissed callback
                }
            }
        }
        .addCallback(
            object : Snackbar.Callback() {
                override fun onDismissed(
                    transientBottomBar: Snackbar?,
                    event: Int,
                ) {
                    when (event) {
                        // handle the UNDO action here as we only have one
                        BaseTransientBottomBar.BaseCallback.DISMISS_EVENT_ACTION -> onAction()
                        BaseTransientBottomBar.BaseCallback.DISMISS_EVENT_SWIPE,
                        BaseTransientBottomBar.BaseCallback.DISMISS_EVENT_TIMEOUT,
                        -> onDismiss()
                        BaseTransientBottomBar.BaseCallback.DISMISS_EVENT_CONSECUTIVE,
                        BaseTransientBottomBar.BaseCallback.DISMISS_EVENT_MANUAL,
                        -> { /* noop */ }
                    }
                }
            },
        )
        .apply {
            if (anchor != null) {
                setAnchorView(anchor)
            }
        }
        .setAnimationMode(BaseTransientBottomBar.ANIMATION_MODE_SLIDE)
        .apply { view.findViewById<TextView>(materialR.id.snackbar_text).maxLines = 1 }

    fun show() {
        snackbar.show()
    }
}
