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

package com.duckduckgo.duckchat.api

/**
 * Listens for native input events raised from the app layer (the host that shows the widget and
 * receives its submit callbacks). Keeps `:app` off `duckchat-impl` internals — implementations live
 * in `duckchat-impl`. The current implementation (`MetricsNativeInputEventListener`) fans these
 * events out to the pixel, session-usage and discovery-funnel collaborators.
 */
interface NativeInputEventListener {

    /** Native input shown to the user. Fires once per presentation. */
    fun onNativeInputShown(landscape: Boolean)

    /** A search query was submitted from the native input. */
    fun onSearchSubmitted(query: String)

    /** A Duck.ai chat prompt was submitted from the native input. */
    fun onChatPromptSubmitted()
}
