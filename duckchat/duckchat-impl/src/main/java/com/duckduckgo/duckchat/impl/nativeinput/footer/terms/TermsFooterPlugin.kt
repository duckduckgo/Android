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
package com.duckduckgo.duckchat.impl.nativeinput.footer.terms

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.duckduckgo.anvil.annotations.ContributesActivePlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.duckduckgo.duckchat.impl.nativeinput.footer.NativeInputFooter
import com.duckduckgo.duckchat.impl.nativeinput.footer.NativeInputFooterContext
import com.duckduckgo.duckchat.impl.nativeinput.footer.NativeInputFooterHost
import com.duckduckgo.duckchat.impl.nativeinput.footer.NativeInputFooterPlugin
import com.duckduckgo.duckchat.impl.nativeinput.footer.NativeInputFooterState
import com.duckduckgo.duckchat.impl.terms.DuckAiTermsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Shows the "by sending a prompt you agree" disclaimer until the user has accepted the Duck.ai ToS. */
@ContributesActivePlugin(
    scope = AppScope::class,
    boundType = NativeInputFooterPlugin::class,
    featureName = "pluginTermsNativeInputFooter",
    parentFeatureName = "pluginPointNativeInputFooter",
)
class TermsFooterPlugin @Inject constructor(
    private val termsRepository: DuckAiTermsRepository,
    private val duckChatFeature: DuckChatFeature,
) : NativeInputFooterPlugin {

    // Ahead of the usage footers: acceptance has to come before any notice about using Duck.ai.
    override val priority: Int = 10

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun createFooter(
        context: Context,
        hostContext: StateFlow<NativeInputFooterContext>,
        host: NativeInputFooterHost,
    ): NativeInputFooter {
        val footerView = TermsFooterView(context)
        footerView.render(onLinkClick = { url -> openLink(context, url) })

        val state = combine(hostContext, duckChatFeature.nativeToSConsent().enabled()) { footerContext, enabled ->
            footerContext.takeIf { enabled && it.isDuckAiSelected && !it.isEditing && it.isInputFocused }
        }
            .distinctUntilChanged()
            .flatMapLatest { footerContext ->
                if (footerContext == null) {
                    flowOf(false)
                } else {
                    termsRepository.observeTermsAccepted(footerContext.browserMode).map { accepted -> !accepted }
                }
            }
            .map { NativeInputFooterState(visible = it) }
            .distinctUntilChanged()

        return object : NativeInputFooter {
            override val view: TermsFooterView = footerView
            override val state: Flow<NativeInputFooterState> = state
        }
    }

    // Pinned to this app so the link opens in a DuckDuckGo tab, including from the contextual sheet.
    private fun openLink(
        context: Context,
        url: String,
    ) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(context.packageName)
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
        }
    }
}
