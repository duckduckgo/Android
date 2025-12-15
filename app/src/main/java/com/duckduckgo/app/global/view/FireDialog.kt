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
 */
interface FireDialog {
    /**
     * Shows the Fire dialog.
     * @param fragmentManager The FragmentManager to use for showing the dialog
     * @param tag Optional tag for the fragment transaction
     */
    fun show(fragmentManager: FragmentManager, tag: String? = "fire_dialog")
}
