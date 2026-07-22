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

package com.duckduckgo.adblocking.impl

import android.content.Context
import android.content.ContextWrapper
import android.view.ViewTreeObserver
import android.webkit.WebView
import androidx.annotation.UiThread
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import kotlin.coroutines.resume

interface ContingencyMessageView {
    /**
     * Launches [block] on the WebView's lifecycle scope once its window regains focus.
     *
     * @return the [Job] driving the deferred work (so the caller can track or cancel it), or null if no
     * lifecycle owner is available.
     */
    @UiThread
    fun launchWhenFocused(webView: WebView, block: suspend () -> Unit): Job?

    /**
     * Presents the contingency message bottom sheet.
     *
     * @return true only if the sheet was actually presented; false when the fragment manager is unavailable,
     * its state is saved, or the sheet is already showing.
     */
    @UiThread
    fun show(webView: WebView): Boolean
}

@ContributesBinding(AppScope::class)
class RealContingencyMessageView @Inject constructor() : ContingencyMessageView {

    override fun launchWhenFocused(webView: WebView, block: suspend () -> Unit): Job? {
        val lifecycleOwner = webView.findViewTreeLifecycleOwner() ?: return null
        return lifecycleOwner.lifecycleScope.launch {
            webView.awaitWindowFocus()
            block()
        }
    }

    override fun show(webView: WebView): Boolean {
        val fragmentManager = webView.resolveFragmentManager() ?: return false
        if (fragmentManager.isStateSaved) return false
        if (fragmentManager.findFragmentByTag(ContingencyMessageBottomSheetFragment.TAG) != null) return false
        ContingencyMessageBottomSheetFragment.newInstance()
            .show(fragmentManager, ContingencyMessageBottomSheetFragment.TAG)
        return true
    }
}

/**
 * Resolves a [FragmentManager] able to host the bottom sheet, supporting both a fragment-hosted WebView
 * (e.g. BrowserTabFragment) or an Activity-hosted WebView. Returns null when neither is available.
 */
private fun WebView.resolveFragmentManager(): FragmentManager? {
    runCatching { FragmentManager.findFragment<Fragment>(this) }
        .getOrNull()
        ?.let { return it.childFragmentManager }
    return context.findFragmentActivity()?.supportFragmentManager
}

private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}

private suspend fun WebView.awaitWindowFocus() {
    if (hasWindowFocus()) return
    suspendCancellableCoroutine { continuation ->
        val observer = viewTreeObserver
        val listener = object : ViewTreeObserver.OnWindowFocusChangeListener {
            override fun onWindowFocusChanged(hasFocus: Boolean) {
                if (hasFocus) {
                    observer.removeOnWindowFocusChangeListener(this)
                    continuation.resume(Unit)
                }
            }
        }
        observer.addOnWindowFocusChangeListener(listener)
        continuation.invokeOnCancellation { observer.removeOnWindowFocusChangeListener(listener) }
    }
}
