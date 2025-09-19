/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.internal.features.api

import android.content.Context

interface InternalFeaturePlugin {
    /** @return the title of the internal feature */
    fun internalFeatureTitle(): String

    /** @return return the subtitle of the feature or null */
    fun internalFeatureSubtitle(): String

    /**
     * This method will be called when the user clicks on the feature
     *
     * [activityContext] is the Activity context that hosted the feature
     */
    fun onInternalFeatureClicked(activityContext: Context)

    // Every time you want to add a setting add the priority (order) to the list below and use it in the plugin
    companion object {
        const val DEVELOPER_SETTINGS_PRIO_KEY = 100
        const val FEATURE_INVENTORY_PRIO_KEY = 110
        const val SYNC_SETTINGS_PRIO_KEY = 200
        const val APPTP_SETTINGS_PRIO_KEY = 300
        const val VPN_SETTINGS_PRIO_KEY = 400
        const val SUBS_SETTINGS_PRIO_KEY = 500
        const val PIR_SETTINGS_PRIO_KEY = 550
        const val AUTOFILL_SETTINGS_PRIO_KEY = 600
        const val RMF_SETTINGS_PRIO_KEY = 650
        const val AUDIT_SETTINGS_PRIO_KEY = 700
        const val ADS_SETTINGS_PRIO_KEY = 800
        const val CRASH_ANR_SETTINGS_PRIO_KEY = 900
        const val WEB_VIEW_DEV_SETTINGS_PRIO_KEY = 1_000
    }
}
