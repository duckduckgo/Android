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

package com.duckduckgo.duckchat.impl.inputscreen.ui.suggestions

import com.duckduckgo.anvil.annotations.ContributesActivePluginPoint
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.inputscreen.NativeInputChatTabItemPlugin

/**
 * Active plugin point for [NativeInputChatTabItemPlugin]. The generated point is gated by the
 * "pluginPointNativeInputChatTabItemPlugin" remote feature flag (default enabled); each contributed
 * plugin is additionally gated by its own `@ContributesActivePlugin` flag.
 */
@ContributesActivePluginPoint(
    scope = AppScope::class,
    boundType = NativeInputChatTabItemPlugin::class,
    featureName = "pluginPointNativeInputChatTabItemPlugin",
)
private interface NativeInputChatTabItemPluginPointTrigger
