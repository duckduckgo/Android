/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.privacy.config.internal

import android.util.Log
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

/** Emits the machine-readable result lines that adb tooling waits on. */
interface PrivacyConfigOverrideMarker {
    fun emit(line: String)
}

/**
 * Uses android.util.Log rather than logcat because the logcat logger is only installed on debuggable builds,
 * and internalRelease builds must still report results. This module ships only in the internal flavor.
 */
@ContributesBinding(AppScope::class)
class RealPrivacyConfigOverrideMarker @Inject constructor() : PrivacyConfigOverrideMarker {
    override fun emit(line: String) {
        Log.i(TAG, line)
    }

    companion object {
        const val TAG = "PrivacyConfigOverride"
    }
}
