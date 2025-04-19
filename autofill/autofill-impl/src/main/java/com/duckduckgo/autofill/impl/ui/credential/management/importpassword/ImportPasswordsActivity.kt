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

package com.duckduckgo.autofill.impl.ui.credential.management.importpassword

import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autofill.impl.R
import com.duckduckgo.autofill.impl.databinding.ActivityImportPasswordsBinding
import com.duckduckgo.autofill.impl.ui.credential.management.importpassword.desktopapp.GetDesktopAppParams
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.text.DaxTextView
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.extensions.html
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.navigation.api.GlobalActivityStarter.ActivityParams
import com.duckduckgo.sync.api.SyncActivityWithEmptyParams
import javax.inject.Inject

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(ImportPasswordActivityParams::class)
class ImportPasswordsActivity : DuckDuckGoActivity() {

    private val viewModel: ImportPasswordsViewModel by bindViewModel()
    private val binding: ActivityImportPasswordsBinding by viewBinding()

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    @Inject
    lateinit var pixel: Pixel

    val syncActivityLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.userReturnedFromSyncSettings()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)
        setupToolbar(binding.includeToolbar.toolbar)
        configureEventHandlers()
        configureNumberedInstructions()
        if (savedInstanceState == null) {
            viewModel.userLaunchedScreen()
        }
    }

    private fun configureEventHandlers() {
        binding.getDesktopBrowserButton.setOnClickListener {
            globalActivityStarter.start(this, GetDesktopAppParams)
            viewModel.onUserClickedGetDesktopAppButton()
        }
        binding.syncWithDesktopButton.setOnClickListener {
            val intent = globalActivityStarter.startIntent(this, SyncActivityWithEmptyParams)
            syncActivityLauncher.launch(intent)
            viewModel.onUserClickedSyncWithDesktopButton()
        }
    }

    private fun configureNumberedInstructions() {
        with(binding) {
            importFromDesktopInstructions1.applyHtml(R.string.autofillManagementImportPasswordsImportFromDesktopInstructionOne)
            importFromDesktopInstructions2.applyHtml(R.string.autofillManagementImportPasswordsImportFromDesktopInstructionTwo)
            importFromDesktopInstructions3.applyHtml(R.string.autofillManagementImportPasswordsImportFromDesktopInstructionThree)
            importFromDesktopInstructions4.applyHtml(R.string.autofillManagementImportPasswordsImportFromDesktopInstructionFour)
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // if user is choosing to leave the screen
        if (!isChangingConfigurations) {
            viewModel.userLeavingScreen()
        }
    }

    private fun DaxTextView.applyHtml(@StringRes resId: Int) {
        text = getString(resId).html(this@ImportPasswordsActivity)
    }
}

data object ImportPasswordActivityParams : ActivityParams {
    private fun readResolve(): Any = ImportPasswordActivityParams
}
