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
package com.duckduckgo.feature.toggles.api

@Deprecated(message = "Not needed anymore. Settings is now supported in top-leve and sub-features and Toggle#getSettings returns it")
object FeatureSettings {
    @Deprecated(message = "Not needed anymore. Settings is now supported in top-leve and sub-features and Toggle#getSettings returns it")
    interface Store {
        fun store(
            jsonString: String,
        )
    }

    val EMPTY_STORE = object : Store {
        override fun store(jsonString: String) {}
    }
}
