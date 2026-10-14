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

package com.duckduckgo.duckchat.impl.nativeinput.footer

import android.content.Context
import android.view.View
import com.duckduckgo.anvil.annotations.ContributesActivePluginPoint
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.common.utils.plugins.ActivePlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.nativeinput.NativeInputState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

data class NativeInputFooterContext(
    val isDuckAiSelected: Boolean,
    val isEditing: Boolean,
    val browserMode: BrowserMode,
    val isInputFocused: Boolean,
    val inputContext: NativeInputState.InputContext,
    /** Grows by one per Duck.ai prompt the widget submits, so footers can attribute a prompt to the message that was up. */
    val promptSubmissions: Int = 0,
)

data class NativeInputFooterState(
    val visible: Boolean,
    val blocksComposer: Boolean = false,
)

interface NativeInputFooter {
    val view: View
    val state: Flow<NativeInputFooterState>

    /** Called by the host when [view] actually enters or leaves the screen. */
    fun onDisplayed(displayed: Boolean) {}
}

/** What the user has staged in the input right now; footers use it to offer only compatible models. */
data class NativeInputFooterDraft(
    val hasImages: Boolean,
    val fileMimeTypes: List<String>,
    val selectedTool: String?,
)

/** Actions a footer can ask from the native input. Implemented by the native input widget. */
interface NativeInputFooterHost {
    fun draft(): NativeInputFooterDraft

    /** Selects [modelId] for the next prompt and, when a chat is active, tells the page to switch too. */
    fun selectModel(modelId: String)

    /** Tells the active Duck.ai page the user opted into the weekly allowance. No-op without a page. */
    fun startUsingWeeklyLimit()

    fun openSubscriptionPurchase(origin: String)
}

/** Groups of footers of which at most one shows at a time. */
enum class FooterCategory {
    /** The usage limit and high usage notices. */
    USAGE_NOTICE,
}

interface NativeInputFooterPlugin : ActivePlugin {
    val priority: Int

    /**
     * Footers in the same category are mutually exclusive: only the visible one with the highest priority shows.
     * Null, the default, means no category: the footer never excludes another and stacks with the rest.
     */
    val category: FooterCategory?
        get() = null

    fun createFooter(
        context: Context,
        hostContext: StateFlow<NativeInputFooterContext>,
        host: NativeInputFooterHost,
    ): NativeInputFooter
}

@ContributesActivePluginPoint(
    scope = AppScope::class,
    boundType = NativeInputFooterPlugin::class,
    featureName = "pluginPointNativeInputFooter",
)
private interface NativeInputFooterPluginPointTrigger
