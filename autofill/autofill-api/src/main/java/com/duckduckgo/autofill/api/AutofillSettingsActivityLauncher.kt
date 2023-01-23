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

import android.content.Context
import android.content.Intent
import com.duckduckgo.autofill.api.domain.app.LoginCredentials

/**
 * Used to access an Intent which will launch the autofill settings activity
 * The activity is implemented in the impl module and is otherwise inaccessible from outside this module.
 */
interface AutofillSettingsActivityLauncher {

    /**
     * Launch the Autofill management activity, which will show the full list of available credentials
     */
    fun intent(context: Context): Intent

    /**
     * Launch the Autofill management activity, which will show suggestions for the current url and the full list of available credentials
     */
    fun intentAlsoShowSuggestionsForSite(context: Context, currentUrl: String?): Intent

    /**
     * Launch the Autofill management activity, directly showing particular credentials
     * @param context
     * @param loginCredentials jump directly into viewing mode for these credentials
     */
    fun intentDirectlyViewCredentials(context: Context, loginCredentials: LoginCredentials): Intent
}
