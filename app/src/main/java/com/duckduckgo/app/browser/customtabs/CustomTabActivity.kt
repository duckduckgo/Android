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

package com.duckduckgo.app.browser.customtabs

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Message
import androidx.activity.OnBackPressedCallback
import androidx.browser.customtabs.CustomTabsIntent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.BrowserTabFragment
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.customtabs.CustomTabViewModel.ViewState
import com.duckduckgo.app.browser.databinding.ActivityCustomTabBinding
import com.duckduckgo.app.global.intentText
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import java.util.UUID
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import logcat.logcat

@InjectWith(ActivityScope::class)
class CustomTabActivity : DuckDuckGoActivity() {

    private val viewModel: CustomTabViewModel by bindViewModel()
    private val binding: ActivityCustomTabBinding by viewBinding()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.onShowCustomTab()

        setContentView(binding.root)

        val url = intent.intentText
        val toolbarColor = intent.getIntExtra(CustomTabsIntent.EXTRA_TOOLBAR_COLOR, 0)

        logcat { "onCreate called with url=$url and toolbar color=$toolbarColor" }

        viewModel.viewState.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .distinctUntilChanged()
            .onEach {
                renderView(it)
            }.launchIn(lifecycleScope)

        viewModel.onCustomTabCreated(url, toolbarColor)

        configureOnBackPressedListener()
    }

    fun openMessageInNewFragmentInCustomTab(
        message: Message,
        currentFragment: BrowserTabFragment,
        toolbarColor: Int,
        isExternal: Boolean,
    ) {
        val tabId = "${CustomTabViewModel.CUSTOM_TAB_NAME_PREFIX}${UUID.randomUUID()}"
        val newFragment = BrowserTabFragment.newInstanceForCustomTab(tabId, null, true, toolbarColor, isExternal)
        val transaction = supportFragmentManager.beginTransaction()
        transaction.hide(currentFragment)
        transaction.add(R.id.fragmentTabContainer, newFragment, tabId)
        transaction.addToBackStack(tabId)
        transaction.commit()
        newFragment.messageFromPreviousTab = message
    }

    override fun onStart() {
        super.onStart()
        viewModel.onShowCustomTab()
    }

    override fun onStop() {
        super.onStop()
        viewModel.onCloseCustomTab()
    }

    private fun renderView(viewState: ViewState) {
        val fragment = BrowserTabFragment.newInstanceForCustomTab(
            tabId = viewState.tabId,
            query = viewState.url,
            skipHome = true,
            toolbarColor = viewState.toolbarColor,
            isExternal = intent.getBooleanExtra(LAUNCH_FROM_EXTERNAL_EXTRA, false),
        )
        val transaction = supportFragmentManager.beginTransaction()
        transaction.replace(R.id.fragmentTabContainer, fragment, viewState.tabId)
        transaction.commit()
    }

    companion object {
        fun intent(context: Context, flags: Int, text: String?, toolbarColor: Int, isExternal: Boolean): Intent {
            return Intent(context, CustomTabActivity::class.java).apply {
                addFlags(flags)
                putExtra(CustomTabsIntent.EXTRA_TOOLBAR_COLOR, toolbarColor)
                putExtra(Intent.EXTRA_TEXT, text)
                putExtra(LAUNCH_FROM_EXTERNAL_EXTRA, isExternal)
            }
        }
        private const val LAUNCH_FROM_EXTERNAL_EXTRA = "LAUNCH_FROM_EXTERNAL_EXTRA"
    }

    private fun configureOnBackPressedListener() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val currentFragment = supportFragmentManager.findFragmentById(R.id.fragmentTabContainer) as? BrowserTabFragment
                    if (currentFragment != null && currentFragment.onBackPressed(isCustomTab = true)) {
                        return
                    }
                    if (supportFragmentManager.backStackEntryCount > 0) {
                        supportFragmentManager.popBackStack()
                    } else {
                        isEnabled = false
                        finish()
                    }
                }
            },
        )
    }
}
