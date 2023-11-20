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

package com.duckduckgo.networkprotection.impl.waitlist

import android.os.Bundle
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.hideKeyboard
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.navigation.api.GlobalActivityStarter.ActivityParams
import com.duckduckgo.networkprotection.impl.R
import com.duckduckgo.networkprotection.impl.databinding.ActivityNetpWaitlistRedeemCodeBinding
import com.duckduckgo.networkprotection.impl.waitlist.NetPWaitlistRedeemCodeActivity.Launch.NetPWaitlistRedeemCodeScreenNoParams
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(NetPWaitlistRedeemCodeScreenNoParams::class)
class NetPWaitlistRedeemCodeActivity : DuckDuckGoActivity() {

    private val viewModel: NetPWaitlistRedeemCodeViewModel by bindViewModel()
    private val binding: ActivityNetpWaitlistRedeemCodeBinding by viewBinding()

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

    private fun render(viewState: NetPWaitlistRedeemCodeViewModel.ViewState) {
        when (viewState) {
            is NetPWaitlistRedeemCodeViewModel.ViewState.Idle -> renderIdle()
            is NetPWaitlistRedeemCodeViewModel.ViewState.Redeeming -> renderRedeeming()
            is NetPWaitlistRedeemCodeViewModel.ViewState.ErrorRedeeming -> renderGeneralError()
            is NetPWaitlistRedeemCodeViewModel.ViewState.Redeemed -> renderRedeemed()
            is NetPWaitlistRedeemCodeViewModel.ViewState.InvalidCode -> renderInvalidCode()
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
        binding.redeemCode.error = getString(R.string.netpWaitlistRedeemCodeError)
        binding.redeemButton.isEnabled = true
    }

    private fun renderGeneralError() {
        binding.redeemCode.error = null
        binding.redeemButton.isEnabled = true
        Snackbar.make(binding.root, R.string.netpWaitlistRedeemCodeGeneralError, Snackbar.LENGTH_LONG).show()
    }

    private fun renderRedeemed() {
        setResult(RESULT_OK)
        finish()
    }

    companion object Launch {
        // This activity is not part of the NetP public API so its model to launch it remains here
        internal object NetPWaitlistRedeemCodeScreenNoParams : ActivityParams
    }
}
