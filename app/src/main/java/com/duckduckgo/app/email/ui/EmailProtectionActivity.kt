/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.app.email.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ActivityFragmentWithToolbarBinding
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class EmailProtectionActivity : DuckDuckGoActivity() {

    private val viewModel: EmailProtectionViewModel by bindViewModel()
    private val binding: ActivityFragmentWithToolbarBinding by viewBinding()

    private val toolbar
        get() = binding.includeToolbar.toolbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel.viewState.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).onEach { render(it) }.launchIn(lifecycleScope)
        setContentView(binding.root)
        setupToolbar(toolbar)
    }

    private fun render(viewState: EmailProtectionViewModel.ViewState) {
        when (val state = viewState.emailState) {
            is EmailProtectionViewModel.EmailState.SignedIn -> launchEmailProtectionSignOut(state.emailAddress)
            is EmailProtectionViewModel.EmailState.SignedOut -> launchEmailProtectionSignIn()
            is EmailProtectionViewModel.EmailState.NotSupported -> launchEmailProtectionNotSupported()
        }
    }

    private fun launchEmailProtectionSignOut(emailAddress: String) {
        val fragment = EmailProtectionSignOutFragment.instance(emailAddress)
        updateFragment(fragment)
    }

    private fun launchEmailProtectionSignIn() {
        val fragment = EmailProtectionSignInFragment.instance()
        updateFragment(fragment)
    }

    private fun launchEmailProtectionNotSupported() {
        val fragment = EmailProtectionNotSupportedFragment.instance()
        updateFragment(fragment)
    }

    private fun updateFragment(fragment: EmailProtectionFragment) {
        val tag = fragment.javaClass.name
        if (supportFragmentManager.findFragmentByTag(tag) != null) return

        supportFragmentManager.commit {
            replace(R.id.fragmentContainer, fragment, fragment.tag)
        }
    }

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, EmailProtectionActivity::class.java)
        }
    }
}
