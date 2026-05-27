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

package com.duckduckgo.autoconsent.impl.store

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.autoconsent.api.CookiePopUpPreference
import com.duckduckgo.autoconsent.impl.remoteconfig.AutoconsentFeature
import com.duckduckgo.common.utils.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

interface AutoconsentSettingsDataStore {
    var cookiePopUpPreference: CookiePopUpPreference
    var userSetting: Boolean
    var firstPopupHandled: Boolean
    fun invalidateCache()
}

class RealAutoconsentSettingsDataStore constructor(
    private val context: Context,
    private val autoconsentFeature: AutoconsentFeature,
    private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) : AutoconsentSettingsDataStore {

    private val preferences: SharedPreferences by lazy { context.getSharedPreferences(FILENAME, Context.MODE_PRIVATE) }
    private var cachedLegacyUserSetting: Boolean? = null
    private var cachedCookiePopUpPreference: CookiePopUpPreference? = null

    private var _defaultValue: Boolean? = null
    private val defaultValue: Boolean
        get() {
            if (_defaultValue == null) {
                _defaultValue = autoconsentFeature.onByDefault().isEnabled()
            }
            return _defaultValue!!
        }

    init {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            cachedLegacyUserSetting = readLegacyUserSetting()
            cachedCookiePopUpPreference = readCookiePopUpPreference()
        }
    }

    override var cookiePopUpPreference: CookiePopUpPreference
        get() {
            return cachedCookiePopUpPreference ?: readCookiePopUpPreference().also {
                cachedCookiePopUpPreference = it
            }
        }
        set(value) {
            preferences.edit(commit = true) {
                putString(AUTOCONSENT_COOKIE_POP_UP_PREFERENCE, value.name)
            }.also {
                cachedCookiePopUpPreference = value
            }
        }

    override var userSetting: Boolean
        get() = cachedLegacyUserSetting ?: readLegacyUserSetting().also { cachedLegacyUserSetting = it }
        set(value) {
            writeLegacyUserSetting(value)
            cachedLegacyUserSetting = value
        }

    override var firstPopupHandled: Boolean
        get() = preferences.getBoolean(AUTOCONSENT_FIRST_POPUP_HANDLED, false)
        set(value) {
            preferences.edit(commit = true) {
                putBoolean(AUTOCONSENT_FIRST_POPUP_HANDLED, value)
            }
        }

    override fun invalidateCache() {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            _defaultValue = autoconsentFeature.onByDefault().isEnabled()
            cachedLegacyUserSetting = null
            cachedCookiePopUpPreference = null
        }
    }

    private fun readCookiePopUpPreference(): CookiePopUpPreference {
        if (preferences.contains(AUTOCONSENT_COOKIE_POP_UP_PREFERENCE)) {
            return parsePreference(preferences.getString(AUTOCONSENT_COOKIE_POP_UP_PREFERENCE, null))
        }
        val migrated = migrateFromLegacySetting()
        preferences.edit(commit = true) {
            putString(AUTOCONSENT_COOKIE_POP_UP_PREFERENCE, migrated.name)
        }
        return migrated
    }

    private fun migrateFromLegacySetting(): CookiePopUpPreference {
        return if (preferences.contains(AUTOCONSENT_USER_SETTING)) {
            if (preferences.getBoolean(AUTOCONSENT_USER_SETTING, false)) {
                CookiePopUpPreference.BLOCK_STANDARD
            } else {
                CookiePopUpPreference.DO_NOT_BLOCK
            }
        } else if (defaultValue) {
            CookiePopUpPreference.BLOCK_STANDARD
        } else {
            CookiePopUpPreference.DO_NOT_BLOCK
        }
    }

    private fun readLegacyUserSetting(): Boolean {
        return preferences.getBoolean(AUTOCONSENT_USER_SETTING, defaultValue)
    }

    private fun writeLegacyUserSetting(value: Boolean) {
        preferences.edit(commit = true) {
            putBoolean(AUTOCONSENT_USER_SETTING, value)
        }
    }

    private fun parsePreference(value: String?): CookiePopUpPreference {
        return try {
            CookiePopUpPreference.valueOf(value!!)
        } catch (_: Exception) {
            CookiePopUpPreference.BLOCK_STANDARD
        }
    }

    companion object {
        private const val FILENAME = "com.duckduckgo.autoconsent.store.settings"
        private const val AUTOCONSENT_USER_SETTING = "AutoconsentUserSetting"
        private const val AUTOCONSENT_COOKIE_POP_UP_PREFERENCE = "AutoconsentCookiePopUpPreference"
        private const val AUTOCONSENT_FIRST_POPUP_HANDLED = "AutoconsentFirstPopupHandled"
    }
}
