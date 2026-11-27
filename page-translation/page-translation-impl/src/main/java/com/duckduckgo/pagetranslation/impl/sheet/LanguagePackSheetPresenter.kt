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

package com.duckduckgo.pagetranslation.impl.sheet

import android.webkit.WebView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.pagetranslation.impl.AttachedWebView
import com.duckduckgo.pagetranslation.impl.language.LanguagePair
import com.duckduckgo.pagetranslation.impl.languagepack.LanguagePackDownload
import com.duckduckgo.pagetranslation.impl.languagepack.isDownloaded
import com.duckduckgo.pagetranslation.impl.manifest.ModelsManifestStore
import com.duckduckgo.pagetranslation.impl.offer.TranslationOfferSource
import com.duckduckgo.pagetranslation.impl.translate.LanguagePackDownloadPrompt
import com.duckduckgo.pagetranslation.impl.translate.TranslateAction
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Asks for confirmation in a [LanguagePackSheetFragment] shown in the host's child fragment manager, then hands the
 * confirmed pair to [LanguagePackDownload], then translates the tab if it still offers that pair once it is installed.
 */
@SingleIn(FragmentScope::class)
@ContributesBinding(FragmentScope::class, binding = binding<LanguagePackDownloadPrompt>())
class LanguagePackSheetPresenter @Inject constructor(
    private val attachedWebView: AttachedWebView,
    private val manifestStore: ModelsManifestStore,
    private val offerSource: TranslationOfferSource,
    private val download: LanguagePackDownload,
    private val translateAction: TranslateAction,
) : LanguagePackDownloadPrompt {

    private val pendingTranslate = ConflatedJob()

    /**
     * Listens for the sheet's confirmation, including one from a sheet restored after a configuration change.
     * Call it on every attach, because the listener lives as long as the host's view.
     */
    fun listen(webView: WebView) {
        val fragment = hostFragment(webView) ?: return
        fragment.childFragmentManager.setFragmentResultListener(LanguagePackSheetFragment.RESULT_KEY, fragment.viewLifecycleOwner) { _, result ->
            val pair = LanguagePackSheetFragment.pairFromResult(result)
            val offer = offerSource.offer.value?.takeIf { it.pair == pair }
            pendingTranslate += fragment.viewLifecycleOwner.lifecycleScope.launch {
                val state = download.download(pair)
                if (state.isDownloaded() && offer != null && offerSource.offer.value == offer) {
                    translateAction.translate(pair)
                }
            }
        }
    }

    override fun requestDownload(pair: LanguagePair) {
        val fragment = attachedWebView.webView?.let(::hostFragment) ?: return
        val fragmentManager = fragment.childFragmentManager
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            val sizeBytes = manifestStore.readManifest()?.models?.get(pair.key)?.totalBytes
            val shown = fragment.isResumed && !fragmentManager.isStateSaved &&
                fragmentManager.findFragmentByTag(LanguagePackSheetFragment.TAG) == null
            if (!shown) return@launch
            LanguagePackSheetFragment.newInstance(pair, sizeBytes).show(fragmentManager, LanguagePackSheetFragment.TAG)
        }
    }

    private fun hostFragment(webView: WebView): Fragment? = try {
        FragmentManager.findFragment<Fragment>(webView)
    } catch (e: IllegalStateException) {
        null
    }
}
