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

package com.duckduckgo.app.dev.settings.modals

import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ActivityModalCoordinatorDevSettingsBinding
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeHandler
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.promptscoordinator.impl.RealPromptsCoordinator
import com.duckduckgo.promptscoordinator.impl.store.ModalEvaluatorCompletionStore
import kotlinx.coroutines.launch
import javax.inject.Inject

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(ModalCoordinatorDevSettingsScreen::class)
class ModalCoordinatorDevSettingsActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var edgeToEdgeHandler: EdgeToEdgeHandler

    @Inject
    lateinit var completionStore: ModalEvaluatorCompletionStore

    @Inject
    lateinit var promptsCoordinator: RealPromptsCoordinator

    private val binding: ActivityModalCoordinatorDevSettingsBinding by viewBinding()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableTransparentEdgeToEdge()
        setContentView(binding.root)
        configureEdgeToEdgeInsets()
        setupToolbar(binding.includeToolbar.toolbar)

        binding.resetCooldownButton.setOnClickListener {
            lifecycleScope.launch {
                completionStore.resetCooldown()
                promptsCoordinator.resetGap()
                Toast.makeText(
                    this@ModalCoordinatorDevSettingsActivity,
                    R.string.modalCoordinatorDevSettingsResetCooldownDone,
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    private fun configureEdgeToEdgeInsets() {
        edgeToEdgeHandler.applyHorizontalSystemBarInsets(binding.root)
        edgeToEdgeHandler.applyStatusBarInsets(binding.includeToolbar.appBarLayout)
        edgeToEdgeHandler.applyScrollableNavigationBarInsets(binding.contentScrollView)
    }
}

data object ModalCoordinatorDevSettingsScreen : GlobalActivityStarter.ActivityParams {
    private fun readResolve(): Any = ModalCoordinatorDevSettingsScreen
}
