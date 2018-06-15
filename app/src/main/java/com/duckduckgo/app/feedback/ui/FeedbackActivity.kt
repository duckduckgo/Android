/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.feedback.ui

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.core.view.isVisible
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.global.ViewModelFactory
import com.duckduckgo.app.global.view.TextChangedWatcher
import kotlinx.android.synthetic.main.activity_feedback.*
import javax.inject.Inject


class FeedbackActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var viewModelFactory: ViewModelFactory


    private val viewModel: FeedbackViewModel by lazy {
        ViewModelProviders.of(this, viewModelFactory).get(FeedbackViewModel::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_feedback)
        configureListeners()

        viewModel.viewState.observe(this, Observer<FeedbackViewModel.ViewState> {
            it?.let { render(it) }
        })
    }

    private fun configureListeners() {
        brokenSiteSwitch.setOnCheckedChangeListener { _, isChecked ->
            viewModel.onBrokenSiteChanged(isChecked)
        }
        feedbackMessage.addTextChangedListener(object : TextChangedWatcher() {
            override fun afterTextChanged(editable: Editable) {
                viewModel.onFeedbackMessageChanged(editable.toString())
            }
        })
        brokenSiteUrl.addTextChangedListener(object : TextChangedWatcher() {
            override fun afterTextChanged(editable: Editable) {
                viewModel.onBrokenSiteUrlChanged(editable.toString())
            }
        })
        submitButton.setOnClickListener {  _ ->
            viewModel.onSubmitPressed()
            finish()
        }
    }

    private fun render(viewState: FeedbackViewModel.ViewState) {
        val brokenSiteInitiallyHidden = !brokenSiteUrl.isVisible
        brokenSiteSwitch.isActivated = viewState.isBrokenSite
        brokenSiteUrl.isVisible = viewState.showUrl
        brokenSiteConfirmation.isVisible = viewState.showDomainAdded
        submitButton.isEnabled = viewState.submitAllowed

        if(brokenSiteInitiallyHidden && brokenSiteUrl.isVisible) {
            brokenSiteUrl.requestFocus()
        }
    }

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, FeedbackActivity::class.java)
        }
    }
}
