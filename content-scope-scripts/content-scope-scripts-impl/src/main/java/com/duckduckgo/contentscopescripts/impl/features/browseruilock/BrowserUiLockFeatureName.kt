/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.contentscopescripts.impl.features.browseruilock

/**
 * Feature name enum for browser UI lock feature.
 */
enum class BrowserUiLockFeatureName(val value: String) {
    BrowserUiLock("browserUiLock"),
}

/**
 * Converts a string to BrowserUiLockFeatureName enum.
 */
fun browserUiLockFeatureValueOf(value: String): BrowserUiLockFeatureName? {
    return BrowserUiLockFeatureName.entries.find { it.value == value }
}
