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
import android.view.View
import android.view.ViewGroup
import com.duckduckgo.anvil.annotations.ContributesActivePlugin
import com.duckduckgo.common.ui.view.MessageCta
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.DuckChatInputModeState
import com.duckduckgo.duckchat.api.inputscreen.NativeInputChatTabItem
import com.duckduckgo.duckchat.api.inputscreen.NativeInputChatTabItemPlugin
import com.duckduckgo.duckchat.api.inputscreen.SingleViewChatTabItem
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.feature.toggles.api.Toggle.DefaultFeatureValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Worked example of contributing to the native-input Chat tab — a dismissible message card pinned to
 * the top. It's the simplest possible contribution and shows the recommended path:
 *
 * - implement [NativeInputChatTabItemPlugin] in your own module, gated by [ContributesActivePlugin]
 *   (here `INTERNAL`, so it only appears in internal/dev builds),
 * - return a [SingleViewChatTabItem] for a single static view — no adapter to write,
 * - drive visibility from the shared input state: here `inputQuery.map { it.isEmpty() }` makes it a
 *   zero-state card (hidden once the user types). A card that should stay while typing would simply
 *   not include `inputQuery` in its `visible` flow.
 * - `priority` picks the slot from [NativeInputChatTabItemPlugin]'s constants.
 *
 * This is a showcase — delete it (and its strings) once real consumers exist.
 */
@ContributesActivePlugin(
    scope = AppScope::class,
    boundType = NativeInputChatTabItemPlugin::class,
    defaultActiveValue = DefaultFeatureValue.INTERNAL,
    priority = NativeInputChatTabItemPlugin.PRIORITY_PROMO,
    featureName = "pluginExampleMessageCardPlugin",
    parentFeatureName = "pluginPointNativeInputChatTabItemPlugin",
)
class ExampleMessageCardPlugin @Inject constructor(
    private val inputModeState: DuckChatInputModeState,
) : NativeInputChatTabItemPlugin {

    override fun create(context: Context, scope: CoroutineScope): NativeInputChatTabItem =
        ExampleMessageCardItem(inputModeState, scope)
}

private class ExampleMessageCardItem(
    inputModeState: DuckChatInputModeState,
    scope: CoroutineScope,
) : SingleViewChatTabItem(
    visible = inputModeState.inputQuery.map { it.isEmpty() }, // zero-state: shown only on empty query
    scope = scope,
) {

    override fun onCreateView(parent: ViewGroup): View {
        val context = parent.context
        return MessageCta(context).apply {
            disableStateSaving()
            setMessage(
                MessageCta.Message(
                    title = context.getString(R.string.duckChatExampleMessageCardTitle),
                    subtitle = context.getString(R.string.duckChatExampleMessageCardSubtitle),
                    action = context.getString(R.string.duckChatExampleMessageCardAction),
                ),
            )
            onCloseButtonClicked { hide() }
            onPrimaryActionClicked {
                // Example only: a real consumer would navigate, open a chat, fire a pixel, etc.
            }
        }
    }
}
