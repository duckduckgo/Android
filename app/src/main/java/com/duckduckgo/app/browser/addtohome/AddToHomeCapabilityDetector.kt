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

package com.duckduckgo.app.browser.addtohome

import android.content.Context
import androidx.core.content.pm.ShortcutManagerCompat
import timber.log.Timber
import javax.inject.Inject

interface AddToHomeCapabilityDetector {
    fun isAddToHomeSupported(): Boolean
}

class AddToHomeSystemCapabilityDetector @Inject constructor(val context: Context) : AddToHomeCapabilityDetector {

    override fun isAddToHomeSupported(): Boolean {
        val supported = ShortcutManagerCompat.isRequestPinShortcutSupported(context)
        Timber.v("Add to home is %ssupported", if (supported) "" else "not ")
        return supported
    }
}
