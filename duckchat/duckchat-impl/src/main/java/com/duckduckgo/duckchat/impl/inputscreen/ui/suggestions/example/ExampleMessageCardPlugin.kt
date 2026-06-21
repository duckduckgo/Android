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

package com.duckduckgo.duckchat.impl.inputscreen.ui.suggestions.example

import android.content.Context
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.anvil.annotations.ContributesActivePlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.inputscreen.NativeInputChatTabItem
import com.duckduckgo.duckchat.api.inputscreen.NativeInputChatTabItemPlugin
import com.duckduckgo.feature.toggles.api.Toggle.DefaultFeatureValue
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

/**
 * Example contribution to the native-input Chat tab: a dismissible message card pinned to the top.
 *
 * It exercises the [NativeInputChatTabItemPlugin] contract end to end:
 * - the plugin is a DI-graph singleton acting as a factory; [create] returns a fresh
 *   [NativeInputChatTabItem] for each Chat-tab binding,
 * - the item is query-independent ([NativeInputChatTabItem.supportsQuery] = false), so the host never
 *   calls `onQueryChanged` and the card is unaffected by what the user types,
 * - the item owns its content: dismissing the card empties its adapter (see [ExampleMessageCardAdapter]).
 *
 * Gated `INTERNAL`, so it only appears in internal/dev builds. This is a showcase — delete it (and its
 * strings) once a real consumer exists.
 *
 * A real, data-driven card would use the [CoroutineScope] passed to [create] to collect its content
 * (e.g. from a remote-message store) into its adapter; this example is static and ignores it.
 */
@ContributesActivePlugin(
    scope = AppScope::class,
    boundType = NativeInputChatTabItemPlugin::class,
    defaultActiveValue = DefaultFeatureValue.INTERNAL,
    // Lowest priority value → pinned above any other contribution and the built-in sections.
    priority = 0,
    featureName = "pluginExampleMessageCardPlugin",
    parentFeatureName = "pluginPointNativeInputChatTabItemPlugin",
)
class ExampleMessageCardPlugin @Inject constructor() : NativeInputChatTabItemPlugin {

    override fun create(context: Context, scope: CoroutineScope): NativeInputChatTabItem =
        ExampleMessageCardItem()
}

private class ExampleMessageCardItem : NativeInputChatTabItem {

    override val adapter: RecyclerView.Adapter<*> = ExampleMessageCardAdapter(
        onPrimaryAction = {
            // Example only: a real consumer would navigate, open a chat, fire a pixel, etc.
        },
    )

    override val supportsQuery: Boolean = false
}
