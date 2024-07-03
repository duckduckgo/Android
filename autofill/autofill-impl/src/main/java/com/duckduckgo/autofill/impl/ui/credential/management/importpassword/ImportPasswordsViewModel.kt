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

package com.duckduckgo.autofill.impl.ui.credential.management.importpassword

import androidx.lifecycle.ViewModel
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_IMPORT_PASSWORDS_GET_DESKTOP_BROWSER
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_IMPORT_PASSWORDS_SYNC_WITH_DESKTOP
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_IMPORT_PASSWORDS_USER_TOOK_NO_ACTION
import com.duckduckgo.di.scopes.AppScope
import javax.inject.Inject

@ContributesViewModel(AppScope::class)
class ImportPasswordsViewModel @Inject constructor(
    private val pixel: Pixel,
) : ViewModel() {

    private var userTookAction = false

    fun userLeavingScreen() {
        if (!userTookAction) {
            pixel.fire(AUTOFILL_IMPORT_PASSWORDS_USER_TOOK_NO_ACTION)
        }
    }

    fun onUserClickedGetDesktopAppButton() {
        pixel.fire(AUTOFILL_IMPORT_PASSWORDS_GET_DESKTOP_BROWSER)
        userTookAction = true
    }

    fun onUserClickedSyncWithDesktopButton() {
        pixel.fire(AUTOFILL_IMPORT_PASSWORDS_SYNC_WITH_DESKTOP)
        userTookAction = true
    }
}
