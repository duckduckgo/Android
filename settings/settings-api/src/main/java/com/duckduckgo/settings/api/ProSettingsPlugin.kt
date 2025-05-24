/*
 * Copyright (c) 2023 DuckDuckGo
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

import android.content.Context
import android.view.View

/**
 * Use this interface to create a new plugin that will be used to display a specific settings section
 */
interface SettingsPlugin {
    /**
     * This method returns a [View] that will be used as a setting item
     * @return [View]
     */
    fun getView(context: Context): View
}

/**
 * This is the plugin for the subs settings
 */
interface ProSettingsPlugin : SettingsPlugin

/**
 * This is the plugin for Duck Player settings
 */
interface DuckPlayerSettingsPlugin : SettingsPlugin

interface ThreatProtectionSettingsPlugin : SettingsPlugin
