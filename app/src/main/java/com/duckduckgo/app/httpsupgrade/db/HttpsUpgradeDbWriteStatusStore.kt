/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.httpsupgrade.db

import android.content.Context
import android.content.SharedPreferences
import android.support.annotation.VisibleForTesting
import androidx.core.content.edit
import javax.inject.Inject

interface HttpsUpgradeDbWriteStatusStore {

    fun saveETag(eTag: String?)
    fun isMatchingETag(eTag: String?): Boolean
}

class HttpsUpgradeDbWriteStatusSharedPreferences @Inject constructor(private val context: Context) : HttpsUpgradeDbWriteStatusStore {

    override fun saveETag(eTag: String?) {
        preferences.edit { putString(KEY_ETAG_OF_LAST_FULL_WRITE, eTag) }
    }

    override fun isMatchingETag(eTag: String?): Boolean {
        val existingETag = preferences.getString(KEY_ETAG_OF_LAST_FULL_WRITE, null)
        return eTag == existingETag
    }

    private val preferences: SharedPreferences
        get() = context.getSharedPreferences(FILENAME, Context.MODE_PRIVATE)

    companion object {

        @VisibleForTesting
        const val FILENAME = "com.duckduckgo.app.httpsupgrade.db.HttpsUpgradeDbWriteStatus"
        const val KEY_ETAG_OF_LAST_FULL_WRITE = "KEY_ETAG_OF_LAST_FULL_WRITE"
    }
}