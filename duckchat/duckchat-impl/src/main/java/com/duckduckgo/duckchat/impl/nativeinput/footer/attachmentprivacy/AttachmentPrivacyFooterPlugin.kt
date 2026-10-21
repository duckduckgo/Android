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

package com.duckduckgo.duckchat.impl.nativeinput.footer.attachmentprivacy

import android.content.Context
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.tabs.BrowserNav
import com.duckduckgo.codegen.annotations.ContributesActivePlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.nativeinput.NativeInputStateProvider
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.duckduckgo.duckchat.impl.nativeinput.footer.FooterCategory
import com.duckduckgo.duckchat.impl.nativeinput.footer.NativeInputFooter
import com.duckduckgo.duckchat.impl.nativeinput.footer.NativeInputFooterContext
import com.duckduckgo.duckchat.impl.nativeinput.footer.NativeInputFooterHost
import com.duckduckgo.duckchat.impl.nativeinput.footer.NativeInputFooterPlugin
import com.duckduckgo.duckchat.impl.nativeinput.footer.NativeInputFooterState
import com.duckduckgo.duckchat.impl.nativeinput.footer.attachmentprivacy.AttachmentPrivacyPixelName.FILE_LEARN_MORE_TAPPED
import com.duckduckgo.duckchat.impl.nativeinput.footer.attachmentprivacy.AttachmentPrivacyPixelName.FILE_SHOWN
import com.duckduckgo.duckchat.impl.nativeinput.footer.attachmentprivacy.AttachmentPrivacyPixelName.IMAGE_LEARN_MORE_TAPPED
import com.duckduckgo.duckchat.impl.nativeinput.footer.attachmentprivacy.AttachmentPrivacyPixelName.IMAGE_SHOWN
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelSurface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@ContributesActivePlugin(
    scope = AppScope::class,
    boundType = NativeInputFooterPlugin::class,
    featureName = "pluginAttachmentPrivacyNativeInputFooter",
    parentFeatureName = "pluginPointNativeInputFooter",
)
class AttachmentPrivacyFooterPlugin @Inject constructor(
    private val duckChatFeature: DuckChatFeature,
    private val stateProvider: NativeInputStateProvider,
    private val store: AttachmentPrivacyDisclaimerStore,
    private val pixels: AttachmentPrivacyPixels,
    private val browserNav: BrowserNav,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : NativeInputFooterPlugin {

    override val priority: Int = 60
    override val category: FooterCategory = FooterCategory.USAGE_NOTICE

    override fun createFooter(
        context: Context,
        hostContext: StateFlow<NativeInputFooterContext>,
        host: NativeInputFooterHost,
    ): NativeInputFooter {
        val footerView = AttachmentPrivacyFooterView(context)
        var shownThisPrompt = false
        var promptSubmissions = hostContext.value.promptSubmissions

        fun surface() = DuckChatPixelSurface.from(hostContext.value.inputContext)
        fun hasImages() = host.draft().hasImages

        footerView.render {
            pixels.fire(if (hasImages()) IMAGE_LEARN_MORE_TAPPED else FILE_LEARN_MORE_TAPPED, surface())
            context.startActivity(browserNav.openInNewTab(context, LEARN_MORE_URL))
        }

        val hasUploadAttachments = stateProvider.state.map { it.hasAttachments }.distinctUntilChanged()

        val state = combine(
            hostContext,
            duckChatFeature.attachmentPrivacyDisclaimer().enabled(),
            hasUploadAttachments,
            store.shown,
        ) { footerContext, enabled, hasAttachments, shownBefore ->
            if (footerContext.promptSubmissions != promptSubmissions) {
                promptSubmissions = footerContext.promptSubmissions
                shownThisPrompt = false
            }
            val active = enabled && footerContext.isDuckAiSelected && !footerContext.isEditing && footerContext.isInputFocused && hasAttachments
            NativeInputFooterState(visible = active && (shownThisPrompt || !shownBefore))
        }.distinctUntilChanged()

        return object : NativeInputFooter {
            override val view: AttachmentPrivacyFooterView = footerView
            override val state: Flow<NativeInputFooterState> = state

            override fun onDisplayed(displayed: Boolean) {
                if (!displayed || shownThisPrompt) return
                shownThisPrompt = true
                pixels.fire(if (hasImages()) IMAGE_SHOWN else FILE_SHOWN, surface())
                appCoroutineScope.launch { store.markShown() }
            }
        }
    }

    private companion object {
        const val LEARN_MORE_URL = "https://duckduckgo.com/duckduckgo-help-pages/duckai/ai-chat-privacy"
    }
}
