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

package com.duckduckgo.app.global

import android.content.Intent
import android.os.BadParcelableException
import android.os.Bundle
import timber.log.Timber

val Intent.intentText: String?
    get() {
        return data?.toString() ?: getStringExtra(Intent.EXTRA_TEXT)
    }

fun Intent.sanitize() {

    try {
        // The strings are empty to force unparcel() call in BaseBundle
        getStringExtra("")
        getBooleanExtra("", false)
    } catch (e: BadParcelableException) {
        Timber.e(e, "Failed to read Parcelable from intent")
        replaceExtras(Bundle())

    } catch (e: RuntimeException) {
        Timber.e(e, "Failed to receive extras from intent")
        replaceExtras(Bundle())
    }
}
