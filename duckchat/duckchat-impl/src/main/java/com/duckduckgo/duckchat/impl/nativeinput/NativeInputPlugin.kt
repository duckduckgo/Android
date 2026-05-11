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

sealed class PromptContribution {
    data class ModelSelection(val modelId: String) : PromptContribution()
}

sealed class Action {
    data object StartChat : Action()
    data class ShowAttachmentChooser(val showing: Boolean) : Action()
    data class AttachmentStateChanged(val hasAttachments: Boolean, val limitExceeded: Boolean, val supportsUpload: Boolean) : Action()
}

interface NativeInputPlugin : ActivePlugin {

    val containerId: Int

    fun createView(context: Context, onAction: (Action) -> Unit): View

    fun getPromptContribution(): PromptContribution?
}

@ContributesActivePluginPoint(
    scope = AppScope::class,
    boundType = NativeInputPlugin::class,
    featureName = "pluginPointNativeInput",
)
private interface NativeInputPluginPointTrigger
