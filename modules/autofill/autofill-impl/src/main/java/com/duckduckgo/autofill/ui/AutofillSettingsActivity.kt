/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.autofill.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.autofill.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.databinding.ActivityAutofillSettingsBinding
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import kotlinx.coroutines.launch
import timber.log.Timber

@InjectWith(ActivityScope::class)
class AutofillSettingsActivity : DuckDuckGoActivity() {

    private val binding: ActivityAutofillSettingsBinding by viewBinding()
    private val viewModel: AutofillSettingsViewModel by bindViewModel()
    private val adapter = AutofillSettingsRecyclerAdapter() { onCredentialsSelected(it) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        configureRecyclerView()

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.CREATED) {
                val logins = viewModel.logins()
                adapter.updateLogins(logins)
            }
        }
    }

    fun onCredentialsSelected(loginCredentials: LoginCredentials) {
        Timber.e("credentials selected: %s", loginCredentials)
    }

    private fun configureRecyclerView() {
        binding.logins.adapter = adapter
    }

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, AutofillSettingsActivity::class.java)
        }
    }

}

@ContributesTo(AppScope::class)
@Module
class AutofillSettingsModule {

    @Provides
    fun activityLauncher(): AutofillSettingsActivityLauncher {
        return object : AutofillSettingsActivityLauncher {
            override fun intent(context: Context): Intent {
                return AutofillSettingsActivity.intent(context)
            }
        }
    }
}
