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
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.pagetranslation.impl.AttachedWebView
import com.duckduckgo.pagetranslation.impl.language.LanguagePacks
import com.duckduckgo.pagetranslation.impl.language.LanguagePair
import com.duckduckgo.pagetranslation.impl.translate.LanguagePackDownloadPrompt
import com.duckduckgo.pagetranslation.impl.translate.TranslateAction
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import javax.inject.Inject

/**
 * Asks for confirmation in a [LanguagePackSheetFragment] shown in the host's child fragment manager, then hands the
 * confirmed pair to [TranslateAction], which owns the download and the translation.
 */
@SingleIn(FragmentScope::class)
@ContributesBinding(FragmentScope::class, binding = binding<LanguagePackDownloadPrompt>())
class LanguagePackSheetPresenter @Inject constructor(
    private val attachedWebView: AttachedWebView,
    private val languagePacks: LanguagePacks,
    private val translateAction: TranslateAction,
) : LanguagePackDownloadPrompt {

    /**
     * Listens for the sheet's confirmation, including one from a sheet restored after a configuration change.
     * Call it on every attach, because the listener lives as long as the host's view.
     */
    fun listen(webView: WebView) {
        val fragment = hostFragment(webView) ?: return
        fragment.childFragmentManager.setFragmentResultListener(LanguagePackSheetFragment.RESULT_KEY, fragment.viewLifecycleOwner) { _, result ->
            translateAction.translate(LanguagePackSheetFragment.pairFromResult(result))
        }
    }

    override fun requestDownload(pair: LanguagePair) {
        val fragment = attachedWebView.webView?.let(::hostFragment) ?: return
        val fragmentManager = fragment.childFragmentManager
        val shown = fragment.isResumed && !fragmentManager.isStateSaved &&
            fragmentManager.findFragmentByTag(LanguagePackSheetFragment.TAG) == null
        if (!shown) return
        LanguagePackSheetFragment.newInstance(pair, languagePacks.sizeBytes(pair)).show(fragmentManager, LanguagePackSheetFragment.TAG)
    }

    private fun hostFragment(webView: WebView): Fragment? = try {
        FragmentManager.findFragment<Fragment>(webView)
    } catch (e: IllegalStateException) {
        null
    }
}
