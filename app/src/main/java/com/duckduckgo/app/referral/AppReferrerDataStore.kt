/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.app.referral

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.di.scopes.AppObjectGraph
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import dagger.SingleInstanceIn

interface AppReferrerDataStore {
    var referrerCheckedPreviously: Boolean
    var campaignSuffix: String?
    var installedFromEuAuction: Boolean
}

@ContributesBinding(AppObjectGraph::class)
@SingleInstanceIn(AppObjectGraph::class)
class AppReferenceSharePreferences @Inject constructor(private val context: Context) : AppReferrerDataStore {
    override var campaignSuffix: String?
        get() = preferences.getString(KEY_CAMPAIGN_SUFFIX, null)
        set(value) = preferences.edit(true) { putString(KEY_CAMPAIGN_SUFFIX, value) }

    override var referrerCheckedPreviously: Boolean
        get() = preferences.getBoolean(KEY_CHECKED_PREVIOUSLY, false)
        set(value) = preferences.edit(true) { putBoolean(KEY_CHECKED_PREVIOUSLY, value) }

    override var installedFromEuAuction: Boolean
        get() = preferences.getBoolean(KEY_INSTALLED_FROM_EU_AUCTION, false)
        set(value) = preferences.edit(true) { putBoolean(KEY_INSTALLED_FROM_EU_AUCTION, value) }

    private val preferences: SharedPreferences
        get() = context.getSharedPreferences(FILENAME, Context.MODE_PRIVATE)

    companion object {
        const val FILENAME = "com.duckduckgo.app.referral"
        private const val KEY_CAMPAIGN_SUFFIX = "KEY_CAMPAIGN_SUFFIX"
        private const val KEY_CHECKED_PREVIOUSLY = "KEY_CHECKED_PREVIOUSLY"
        private const val KEY_INSTALLED_FROM_EU_AUCTION = "KEY_INSTALLED_FROM_EU_AUCTION"
    }
}
