/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.app.widget.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.view.ViewCompat
import androidx.core.view.ViewGroupCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.databinding.ActivityAddWidgetInstructionsBinding
import com.duckduckgo.app.widget.ui.AddWidgetInstructionsViewModel.Command.Close
import com.duckduckgo.app.widget.ui.AddWidgetInstructionsViewModel.Command.ShowHome
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.edgetoedge.api.EdgeToEdge
import javax.inject.Inject

@InjectWith(ActivityScope::class)
class AddWidgetInstructionsActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var edgeToEdge: EdgeToEdge

    private val binding: ActivityAddWidgetInstructionsBinding by viewBinding()

    private val viewModel: AddWidgetInstructionsViewModel by bindViewModel()

    private val instructionsButtons
        get() = binding.includeAddWidgetInstructionButtons

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        edgeToEdge.enableIfToggled(this)
        setContentView(binding.root)
        setupEdgeToEdge()
        configureListeners()
        configureCommandObserver()
    }

    private fun setupEdgeToEdge() {
        if (!edgeToEdge.isEnabled()) return
        ViewGroupCompat.installCompatInsetsDispatch(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            view.updatePadding(top = insets.top, bottom = insets.bottom)
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun configureListeners() {
        instructionsButtons.homeButton.setOnClickListener {
            viewModel.onShowHomePressed()
        }
        instructionsButtons.closeButton.setOnClickListener {
            viewModel.onClosePressed()
        }
    }

    private fun configureCommandObserver() {
        viewModel.command.observe(this) {
            when (it) {
                ShowHome -> showHome()
                Close -> close()
            }
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        viewModel.onClosePressed()
    }

    fun showHome() {
        val startMain = Intent(Intent.ACTION_MAIN)
        startMain.addCategory(Intent.CATEGORY_HOME)
        startMain.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(startMain)
    }

    fun close() {
        finishAfterTransition()
    }

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, AddWidgetInstructionsActivity::class.java)
        }
    }
}
