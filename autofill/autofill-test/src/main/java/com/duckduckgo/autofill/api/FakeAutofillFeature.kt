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

package com.duckduckgo.autofill.api

import com.duckduckgo.feature.toggles.api.FeatureToggles
import com.duckduckgo.feature.toggles.api.Toggle

class FakeAutofillFeature private constructor() {

    companion object {
        fun create(): AutofillFeature {
            return FeatureToggles.Builder()
                .store(
                    object : Toggle.Store {
                        private val map = mutableMapOf<String, Toggle.State>()

                        override fun set(key: String, state: Toggle.State) {
                            map[key] = state
                        }

                        override fun get(key: String): Toggle.State? {
                            return map[key]
                        }
                    },
                )
                .featureName("fakeAutofill")
                .build()
                .create(AutofillFeature::class.java)
        }
    }
}
