/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.dispatchers

import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.browser.customtabs.CustomTabActivity
import com.duckduckgo.app.dispatchers.IntentDispatcherViewModel.ViewState
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.di.scopes.ActivityScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import logcat.logcat

@InjectWith(ActivityScope::class)
class IntentDispatcherActivity : DuckDuckGoActivity() {

    private val viewModel: IntentDispatcherViewModel by bindViewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        logcat { "onCreate called with intent $intent" }

        viewModel.viewState.flowWithLifecycle(lifecycle, Lifecycle.State.CREATED).onEach {
            dispatch(it)
        }.launchIn(lifecycleScope)

        val surfaceColor = getColorFromAttr(com.duckduckgo.mobile.android.R.attr.daxColorToolbar)
        viewModel.onIntentReceived(intent, surfaceColor, isExternal = true)
    }

    private fun dispatch(viewState: ViewState) {
        if (viewState.customTabRequested) {
            showCustomTab(viewState.intentText, viewState.toolbarColor, viewState.isExternal)
        } else {
            showBrowserActivity(viewState.intentText, viewState.isExternal)
        }
    }

    private fun showCustomTab(intentText: String?, toolbarColor: Int, isExternal: Boolean) {
        // As customizations we only support the toolbar color at the moment.
        startActivity(
            CustomTabActivity.intent(
                context = this,
                flags = Intent.FLAG_ACTIVITY_NEW_TASK and Intent.FLAG_ACTIVITY_CLEAR_TASK and Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS,
                text = intentText,
                toolbarColor = toolbarColor,
                isExternal = isExternal,
            ),
        )

        finish()
    }

    private fun showBrowserActivity(intentText: String?, isExternal: Boolean) {
        startActivity(
            BrowserActivity.intent(
                context = this,
                queryExtra = intentText,
                isExternal = isExternal,
            ),
        )

        overridePendingTransition(0, 0)
        finish()
    }
}
