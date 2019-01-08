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
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.DuckDuckGoActivity
import kotlinx.android.synthetic.main.activity_add_widget_instructions.*

class AddWidgetInstructionsActivity : DuckDuckGoActivity() {

    private val viewModel: AddWidgetInstructionsViewModel by bindViewModel()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_widget_instructions)
        configureAnimation()
    }

    private fun configureAnimation() {
        animation.playAnimation()
    }

    companion object {

        fun intent(context: Context): Intent {
            return Intent(context, AddWidgetInstructionsActivity::class.java)
        }

    }
}
