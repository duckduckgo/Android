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

package com.duckduckgo.autofill.impl

import com.duckduckgo.autofill.api.AutofillScreenLaunchSource
import com.duckduckgo.autofill.api.AutofillScreenLaunchSource.AutofillSettings
import com.duckduckgo.autofill.api.AutofillScreenLaunchSource.BrowserOverflow
import com.duckduckgo.autofill.api.AutofillScreenLaunchSource.BrowserSnackbar
import com.duckduckgo.autofill.api.AutofillScreenLaunchSource.DisableInSettingsPrompt
import com.duckduckgo.autofill.api.AutofillScreenLaunchSource.InternalDevSettings
import com.duckduckgo.autofill.api.AutofillScreenLaunchSource.NewTabShortcut
import com.duckduckgo.autofill.api.AutofillScreenLaunchSource.SettingsActivity
import com.duckduckgo.autofill.api.AutofillScreenLaunchSource.Sync
import com.duckduckgo.autofill.api.AutofillScreenLaunchSource.Unknown

fun AutofillScreenLaunchSource.asString(): String {
    return when (this) {
        SettingsActivity -> "settings"
        BrowserOverflow -> "overflow_menu"
        Sync -> "sync"
        DisableInSettingsPrompt -> "save_login_disable_prompt"
        NewTabShortcut -> "new_tab_page_shortcut"
        BrowserSnackbar -> "browser_snackbar"
        InternalDevSettings -> "internal_dev_settings"
        Unknown -> "unknown"
        AutofillSettings -> "autofill_settings"
    }
}
