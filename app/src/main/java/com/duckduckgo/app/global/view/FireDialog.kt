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

package com.duckduckgo.app.global.view

import androidx.fragment.app.FragmentManager

/**
 * Common interface for Fire dialog variants (Legacy and Granular).
 *
 * To receive lifecycle events from the dialog, use FragmentManager.setFragmentResultListener
 * with REQUEST_KEY and check RESULT_KEY_EVENT for the event type.
 */
interface FireDialog {
    /**
     * Shows the Fire dialog.
     * @param fragmentManager The FragmentManager to use for showing the dialog
     * @param tag Optional tag for the fragment transaction
     */
    fun show(fragmentManager: FragmentManager, tag: String? = "fire_dialog")

    companion object {
        const val REQUEST_KEY = "FireDialogRequestKey"
        const val RESULT_KEY_EVENT = "event"
        const val EVENT_ON_SHOW = "onShow"
        const val EVENT_ON_CANCEL = "onCancel"
        const val EVENT_ON_CLEAR_STARTED = "onClearStarted"
        const val EVENT_ON_SINGLE_TAB_CLEAR_STARTED = "onSingleTabClearStarted"
    }
}
