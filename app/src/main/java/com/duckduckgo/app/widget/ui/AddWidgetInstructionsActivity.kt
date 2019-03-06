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
import androidx.lifecycle.Observer
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.widget.ui.AddWidgetInstructionsViewModel.Command.Close
import com.duckduckgo.app.widget.ui.AddWidgetInstructionsViewModel.Command.ShowHome
import kotlinx.android.synthetic.main.include_add_widget_instruction_buttons.*

class AddWidgetInstructionsActivity : DuckDuckGoActivity() {

    private val viewModel: AddWidgetInstructionsViewModel by bindViewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_widget_instructions)
        configureListeners()
        configureCommandObserver()
    }

    private fun configureListeners() {
        homeButton.setOnClickListener {
            viewModel.onShowHomePressed()
        }
        closeButton.setOnClickListener {
            viewModel.onClosePressed()
        }
    }

    private fun configureCommandObserver() {
        viewModel.command.observe(this, Observer {
            when (it) {
                ShowHome -> showHome()
                Close -> close()
            }
        })
    }

    override fun onBackPressed() {
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
