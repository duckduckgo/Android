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

package com.duckduckgo.common.utils.edgetoedge

/**
 * Single source of truth for whether edge-to-edge should be applied to a given [EdgeToEdgeBucket].
 *
 * Activities check this in [android.app.Activity.onCreate] before calling `enableEdgeToEdge()` and
 * before applying insets
 */
interface EdgeToEdgeProvider {

    /** @return true only if the master toggle AND the [bucket]'s sub-toggle are both enabled. */
    fun isEnabled(bucket: EdgeToEdgeBucket): Boolean
}
