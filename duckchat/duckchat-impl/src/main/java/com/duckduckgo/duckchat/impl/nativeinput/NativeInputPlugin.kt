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
import com.duckduckgo.duckchat.impl.ui.NativeInputState

sealed class PromptContribution {
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
}

interface NativeInputPlugin : ActivePlugin {

    val containerId: Int

    fun createView(context: Context, host: NativeInputHost): View

    fun getPromptContribution(): PromptContribution?
}

@ContributesActivePluginPoint(
    scope = AppScope::class,
    boundType = NativeInputPlugin::class,
    featureName = "pluginPointNativeInput",
)
private interface NativeInputPluginPointTrigger
