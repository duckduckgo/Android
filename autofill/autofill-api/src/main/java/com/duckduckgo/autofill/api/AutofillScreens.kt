/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.autofill.api

import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.navigation.api.GlobalActivityStarter.ActivityParams

sealed interface AutofillScreens {

    /**
     * Launch the Autofill management activity, which will show the full list of available credentials
     * @param source is used to indicate from where in the app Autofill management activity was launched
     */
    data class AutofillSettingsScreen(val source: AutofillSettingsLaunchSource) : ActivityParams

    /**
     * Launch the Autofill management activity, which will show suggestions for the current url and the full list of available credentials
     * @param currentUrl The current URL the user is viewing. This is used to show suggestions for the current site if available.
     * @param source is used to indicate from where in the app Autofill management activity was launched
     */
    data class AutofillSettingsScreenShowSuggestionsForSiteParams(
        val currentUrl: String?,
        val source: AutofillSettingsLaunchSource,
    ) : ActivityParams

    /**
     * Launch the Autofill management activity, directly showing particular credentials
     * @param loginCredentials jump directly into viewing mode for these credentials
     * @param source is used to indicate from where in the app Autofill management activity was launched
     */
    data class AutofillSettingsScreenDirectlyViewCredentialsParams(
        val loginCredentials: LoginCredentials,
        val source: AutofillSettingsLaunchSource,
    ) : ActivityParams
}

enum class AutofillSettingsLaunchSource {
    SettingsActivity,
    BrowserOverflow,
    Sync,
    BrowserSnackbar,
    InternalDevSettings,
    Unknown,
}
