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

package com.duckduckgo.duckchat.impl.nativeinput

import android.content.Context
import android.view.View
import com.duckduckgo.anvil.annotations.ContributesActivePluginPoint
import com.duckduckgo.common.utils.plugins.ActivePlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.nativeinput.NativeInputState

/** State a plugin appends to the prompt at send time. Returned by [NativeInputPlugin.getPromptContribution]; null if it has nothing to add. */
sealed class PromptContribution {
    /** Model selected by the user (e.g. via a model picker plugin). */
    data class ModelSelection(val modelId: String) : PromptContribution()
    data class ReasoningEffortSelection(val effort: String) : PromptContribution()
}

/**
 * Communication surface from a plugin back to the host widget. Plugins use it to act on the host
 * (e.g. [submit]) and to read the host's current [NativeInputState] when their behaviour depends on it,
 * without coupling to the widget class directly.
 */
interface NativeInputHost {
    /** Submit the current input as a chat message; opens a new chat session if the input is empty. */
    fun submit()

    fun showAttachmentChooser(showing: Boolean)

    fun attachmentChanged(hasAttachments: Boolean, limitExceeded: Boolean, supportsUpload: Boolean)

    /** Current input state of the host widget (mode, context, position). */
    fun getInputState(): NativeInputState

    /** The tab ID this widget instance is currently attached to. */
    fun getTabId(): String
}

/**
 * Contributes a view to the native-input widget and, optionally, state to be appended to the prompt at send time.
 *
 * Each plugin targets a specific [containerId] (a `FrameLayout` slot in the widget layout) and owns all click
 * behaviour for the view it returns. The plugin point is gated by `pluginPointNativeInput`, and individual plugins
 * are gated by their own `@ContributesActivePlugin` feature toggle, so only enabled plugins are emitted.
 */
interface NativeInputPlugin : ActivePlugin {

    /** ID of the `FrameLayout` slot in the widget layout this plugin renders into. */
    val containerId: Int

    /** Build the plugin view. Called once at widget setup. The [host] lets the plugin trigger a submit or read widget state. */
    fun createView(context: Context, host: NativeInputHost): View

    /**
     * State to append to the prompt when the user submits, or null if the plugin has nothing to contribute.
     *
     * @deprecated Push contributions to [MutableNativeInputStateProvider] directly instead.
     * Default implementation returns null. Will be removed once all plugins migrate.
     */
    @Deprecated("Push contributions to MutableNativeInputStateProvider instead")
    fun getPromptContribution(): PromptContribution? = null
}

@ContributesActivePluginPoint(
    scope = AppScope::class,
    boundType = NativeInputPlugin::class,
    featureName = "pluginPointNativeInput",
)
private interface NativeInputPluginPointTrigger
