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

package com.duckduckgo.autofill.api.dialog

import androidx.fragment.app.FragmentManager

/**
 * This class is responsible for detecting overlapping Autofill dialog events.
 *
 * This is when an Autofill dialog is already showing and another one is triggered to show.
 * If this happens, we want to detect that scenario and fire a pixel.
 * * @param fragmentManager The FragmentManager to use to detect existing autofill dialogs
 * @param tag The tag of the new dialog that is being shown, used in the pixel
 * @param urlMatch Whether the URL of the new dialog matches the URL of the existing dialog, used in the pixel
 */
interface AutofillOverlappingDialogDetector {
    fun detectOverlappingDialogs(
        fragmentManager: FragmentManager,
        tag: String,
        urlMatch: Boolean,
    )
}
