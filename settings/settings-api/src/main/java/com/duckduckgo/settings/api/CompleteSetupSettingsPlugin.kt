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

package com.duckduckgo.settings.api

import android.view.View
import androidx.appcompat.app.AppCompatActivity

/**
 * This is the plugin for the complete setup settings
 * Complete your setup will not show up in the settings if the user has already completed all steps
 *
 * Each setting that needs to appear in the "Complete your setup" section should implement this interface.
 */
interface CompleteSetupSettingsPlugin {
    /**
     * The settings plugin should determine if it can be shown or not.
     */
    suspend fun canShow(): Boolean

    /**
     * Instantiates the view for this particular setting.
     * @param activity
     * @param onStateChanged callback that can be invoked when the state of the setting changes suggesting the section should be re-evaluated.
     */
    fun getView(activity: AppCompatActivity, onStateChanged: () -> Unit): View?
}
