/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.pir.internal.settings

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.navigation.api.GlobalActivityStarter.ActivityParams
import com.duckduckgo.pir.internal.databinding.ActivityPirWebviewBinding
import com.duckduckgo.pir.internal.optout.PirOptOut
import javax.inject.Inject
import kotlinx.coroutines.launch

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(PirDebugWebViewResultsScreenNoParams::class)
class PirWebViewActivity : DuckDuckGoActivity() {
    @Inject
    lateinit var pirOptOut: PirOptOut

    @Inject
    lateinit var dispatcherProvider: DispatcherProvider

    private val binding: ActivityPirWebviewBinding by viewBinding()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        lifecycleScope.launch(dispatcherProvider.io()) {
            pirOptOut.debugExecute(listOf("Clubset"), binding.pirDebugWebView).also {
                finish()
            }
        }
    }
}

object PirDebugWebViewResultsScreenNoParams : ActivityParams
