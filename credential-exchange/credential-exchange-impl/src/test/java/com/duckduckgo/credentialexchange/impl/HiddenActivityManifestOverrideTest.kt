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

package com.duckduckgo.credentialexchange.impl

import org.junit.Test

class HiddenActivityManifestOverrideTest {

    // AndroidManifest.xml overrides this activity's theme by class name. If a library update renames or moves it,
    // the override silently stops applying, so fail here instead.
    @Test
    fun whenLibraryUpdatedThenThemedHiddenActivityStillExists() {
        Class.forName(HIDDEN_ACTIVITY, false, javaClass.classLoader)
    }

    private companion object {
        const val HIDDEN_ACTIVITY = "androidx.credentials.providerevents.playservices.HiddenActivity"
    }
}
