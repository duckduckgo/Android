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

package com.duckduckgo.app.featureusage.db

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.app.dev.settings.db.DevSettingsSharedPreferences
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface FeatureSegmentsDataStore {
    var bookmarksImported: Boolean
    var favouriteSet: Boolean
    var setAsDefault: Boolean
    var loginSaved: Boolean
    var fireButtonUsed: Boolean
    var appTpEnabled: Boolean
    var emailProtectionSet: Boolean
    var twoSearchesMade: Boolean
    var fiveSearchesMade: Boolean
    var tenSearchesMade: Boolean

    var dailySearchesCount: Int
}

@ContributesBinding(AppScope::class)
class FeatureSegmentsDataStoreSharedPreferences @Inject constructor(
    private val context: Context,
) : FeatureSegmentsDataStore {

    private val preferences: SharedPreferences
        get() = context.getSharedPreferences(DevSettingsSharedPreferences.FILENAME, Context.MODE_PRIVATE)

    override var bookmarksImported: Boolean
        get() = preferences.getBoolean(KEY_BOOKMARKS_IMPORTED, false)
        set(enabled) = preferences.edit { putBoolean(KEY_BOOKMARKS_IMPORTED, enabled) }
    override var favouriteSet: Boolean
        get() = preferences.getBoolean(KEY_FAVOURITE_SET, false)
        set(enabled) = preferences.edit { putBoolean(KEY_FAVOURITE_SET, enabled) }
    override var setAsDefault: Boolean
        get() = preferences.getBoolean(KEY_SET_AS_DEFAULT, false)
        set(enabled) = preferences.edit { putBoolean(KEY_SET_AS_DEFAULT, enabled) }
    override var loginSaved: Boolean
        get() = preferences.getBoolean(KEY_LOGIN_SAVED, false)
        set(enabled) = preferences.edit { putBoolean(KEY_LOGIN_SAVED, enabled) }
    override var fireButtonUsed: Boolean
        get() = preferences.getBoolean(KEY_FIRE_BUTTON_USED, false)
        set(enabled) = preferences.edit { putBoolean(KEY_FIRE_BUTTON_USED, enabled) }
    override var appTpEnabled: Boolean
        get() = preferences.getBoolean(KEY_APP_TP_ENABLED, false)
        set(enabled) = preferences.edit { putBoolean(KEY_APP_TP_ENABLED, enabled) }
    override var emailProtectionSet: Boolean
        get() = preferences.getBoolean(KEY_EMAIL_PROTECTION_SET, false)
        set(enabled) = preferences.edit { putBoolean(KEY_EMAIL_PROTECTION_SET, enabled) }
    override var twoSearchesMade: Boolean
        get() = preferences.getBoolean(KEY_TWO_SEARCHES_MADE, false)
        set(enabled) = preferences.edit { putBoolean(KEY_TWO_SEARCHES_MADE, enabled) }
    override var fiveSearchesMade: Boolean
        get() = preferences.getBoolean(KEY_FIVE_SEARCHES_MADE, false)
        set(enabled) = preferences.edit { putBoolean(KEY_FIVE_SEARCHES_MADE, enabled) }
    override var tenSearchesMade: Boolean
        get() = preferences.getBoolean(KEY_TEN_SEARCHES_MADE, false)
        set(enabled) = preferences.edit { putBoolean(KEY_TEN_SEARCHES_MADE, enabled) }
    override var dailySearchesCount: Int
        get() = preferences.getInt(KEY_SEARCHES_COUNT, 0)
        set(count) = preferences.edit { putInt(KEY_SEARCHES_COUNT, count) }

    companion object {
        const val FILENAME = "com.duckduckgo.app.feature_segments.settings"
        const val KEY_BOOKMARKS_IMPORTED = "BOOKMARKS_IMPORTED"
        const val KEY_FAVOURITE_SET = "FAVOURITE_SET"
        const val KEY_SET_AS_DEFAULT = "SET_AS_DEFAULT"
        const val KEY_LOGIN_SAVED = "LOGIN_SAVED"
        const val KEY_FIRE_BUTTON_USED = "FIRE_BUTTON_USED"
        const val KEY_APP_TP_ENABLED = "APP_TP_ENABLED"
        const val KEY_EMAIL_PROTECTION_SET = "EMAIL_PROTECTION_SET"
        const val KEY_TWO_SEARCHES_MADE = "TWO_SEARCHES_MADE"
        const val KEY_FIVE_SEARCHES_MADE = "FIVE_SEARCHES_MADE"
        const val KEY_TEN_SEARCHES_MADE = "TEN_SEARCHES_MADE"
        const val KEY_SEARCHES_COUNT = "SEARCHES_COUNT"
        const val KEY_USER_ADDED_TO_SEGMENTS_EVENTS = "USER_ADDED_TO_SEGMENTS_EVENTS"
    }
}
