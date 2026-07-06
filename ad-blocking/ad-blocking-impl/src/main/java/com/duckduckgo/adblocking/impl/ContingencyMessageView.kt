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

import android.view.ViewTreeObserver
import android.webkit.WebView
import androidx.annotation.UiThread
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import kotlin.coroutines.resume

interface ContingencyMessageView {
    @UiThread
    fun show(webView: WebView)
}

@ContributesBinding(AppScope::class)
class RealContingencyMessageView @Inject constructor() : ContingencyMessageView {

    override fun show(webView: WebView) {
        val lifecycleOwner = webView.findViewTreeLifecycleOwner() ?: return
        lifecycleOwner.lifecycleScope.launch {
            webView.awaitWindowFocus()
            val fragment: Fragment = FragmentManager.findFragment(webView)
            val fragmentManager = fragment.childFragmentManager
            if (fragmentManager.findFragmentByTag(ContingencyMessageBottomSheetFragment.TAG) == null) {
                ContingencyMessageBottomSheetFragment.newInstance()
                    .show(fragmentManager, ContingencyMessageBottomSheetFragment.TAG)
            }
        }
    }
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
