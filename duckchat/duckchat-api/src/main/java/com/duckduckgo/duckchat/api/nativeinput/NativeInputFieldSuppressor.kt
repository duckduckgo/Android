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

package com.duckduckgo.duckchat.api.nativeinput

/**
 * Lets other modules force-disable the native (unified) input field regardless of the
 * `nativeInputField` user setting. This is the single gate read by `RealDuckChat`, so every
 * consumer of `observeNativeInputFieldUserSettingEnabled()` sees a consistent value.
 *
 * Temporary: added only to exclude users in the active Duck.ai onboarding experiment, removed once
 * it's cleaned up. Not reactive — suppressors are read only when `RealDuckChat.cacheConfig()` runs
 * (app init and privacy-config download), so use this only for state fixed before app start.
 */
interface NativeInputFieldSuppressor {
    /** Return true to suppress the native input field for the current user. */
    suspend fun isNativeInputFieldSuppressed(): Boolean
}
