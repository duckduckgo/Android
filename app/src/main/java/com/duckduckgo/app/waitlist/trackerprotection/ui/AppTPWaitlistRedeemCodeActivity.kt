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

package com.duckduckgo.app.waitlist.trackerprotection.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ActivityAppTpWaitlistRedeemCodeBinding
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.mobile.android.ui.view.hideKeyboard
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class AppTPWaitlistRedeemCodeActivity : DuckDuckGoActivity() {

    private val viewModel: AppTPWaitlistRedeemCodeViewModel by bindViewModel()
    private val binding: ActivityAppTpWaitlistRedeemCodeBinding by viewBinding()

    private val toolbar
        get() = binding.includeToolbar.toolbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel.viewState.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).onEach { render(it) }
            .launchIn(lifecycleScope)

        setContentView(binding.root)
        setupToolbar(toolbar)
        configureUiEventHandlers()
    }

    private fun configureUiEventHandlers() {
        binding.redeemButton.setOnClickListener {
            val code = binding.redeemCode.editText?.text.toString()
            it.hideKeyboard()
            viewModel.redeemCode(code)
        }
        binding.redeemCode.editText?.doOnTextChanged { inputText, _, _, _ ->
            binding.redeemCode.error = null
            binding.redeemButton.isEnabled = !inputText.isNullOrEmpty()
        }
        binding.redeemCode.editText?.setOnEditorActionListener { textView, actionId, keyEvent ->
            if (actionId == EditorInfo.IME_ACTION_GO || keyEvent?.keyCode == KeyEvent.KEYCODE_ENTER) {
                if (binding.redeemButton.isEnabled) {
                    val code = binding.redeemCode.editText?.text.toString()
                    textView.hideKeyboard()
                    viewModel.redeemCode(code)
                }
                return@setOnEditorActionListener true
            } else {
                return@setOnEditorActionListener false
            }
        }
    }

    private fun render(viewState: AppTPWaitlistRedeemCodeViewModel.ViewState) {
        when (viewState) {
            is AppTPWaitlistRedeemCodeViewModel.ViewState.Idle -> renderIdle()
            is AppTPWaitlistRedeemCodeViewModel.ViewState.Redeeming -> renderRedeeming()
            is AppTPWaitlistRedeemCodeViewModel.ViewState.ErrorRedeeming -> renderGeneralError()
            is AppTPWaitlistRedeemCodeViewModel.ViewState.Redeemed -> renderRedeemed()
            is AppTPWaitlistRedeemCodeViewModel.ViewState.InvalidCode -> renderInvalidCode()
        }
    }

    private fun renderIdle() {
        binding.redeemCode.error = null
        binding.redeemCode.isEnabled = true
        binding.redeemButton.isEnabled = false
    }

    private fun renderRedeeming() {
        binding.redeemCode.error = null
        binding.redeemButton.isEnabled = false
    }

    private fun renderInvalidCode() {
        binding.redeemCode.error = getString(R.string.atp_WaitlistRedeemCodeError)
        binding.redeemButton.isEnabled = true
    }

    private fun renderGeneralError() {
        binding.redeemCode.error = null
        binding.redeemButton.isEnabled = true
        Snackbar.make(binding.root, R.string.atp_WaitlistErrorJoining, Snackbar.LENGTH_LONG).show()
    }

    private fun renderRedeemed() {
        setResult(RESULT_OK)
        finish()
    }

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, AppTPWaitlistRedeemCodeActivity::class.java)
        }
    }
}
