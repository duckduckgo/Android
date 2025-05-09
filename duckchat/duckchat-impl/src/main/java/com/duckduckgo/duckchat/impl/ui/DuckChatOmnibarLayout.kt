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

package com.duckduckgo.duckchat.impl.ui

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.duckchat.impl.R

@InjectWith(ActivityScope::class)
class DuckChatOmnibarLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : ConstraintLayout(context, attrs, defStyle) {

    val duckChatFireButton: View
    val duckChatInput: EditText
    val duckChatSend: View
    val duckChatNewChat: View
    val duckChatStop: View
    val duckChatControls: View

    private var originalStartMargin: Int = 0

    var onFire: (() -> Unit)? = null
    var onSend: ((String) -> Unit)? = null
    var onNewChat: (() -> Unit)? = null
    var onStop: (() -> Unit)? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.view_duck_chat_omnibar, this, true)

        duckChatFireButton = findViewById(R.id.duckChatFireButton)
        duckChatInput = findViewById(R.id.duckChatInput)
        duckChatSend = findViewById(R.id.duckChatSend)
        duckChatNewChat = findViewById(R.id.duckChatNewChat)
        duckChatStop = findViewById(R.id.duckChatStop)
        duckChatControls = findViewById(R.id.duckChatControls)

        (duckChatInput.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
            originalStartMargin = params.marginStart
        }

        duckChatFireButton.setOnClickListener { onFire?.invoke() }
        duckChatNewChat.setOnClickListener { onNewChat?.invoke() }
        duckChatSend.setOnClickListener { submitMessage() }
        duckChatStop.setOnClickListener {
            onStop?.invoke()
            hideStopButton()
        }

        duckChatInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                duckChatFireButton.isVisible = false
                duckChatNewChat.isVisible = false
            }
            (duckChatInput.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
                params.marginStart = originalStartMargin
                duckChatInput.layoutParams = params
            }
        }

        duckChatInput.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    duckChatSend.isVisible = !s.isNullOrEmpty()
                    duckChatFireButton.isVisible = false
                    duckChatNewChat.isVisible = false
                }
                override fun afterTextChanged(s: Editable?) = Unit
            },
        )

        duckChatFireButton.isVisible = false
        duckChatNewChat.isVisible = false
    }

    private fun submitMessage() {
        val msg = duckChatInput.text.toString().trim()
        if (msg.isNotEmpty()) {
            onSend?.invoke(msg)
            duckChatInput.text.clear()
            duckChatInput.clearFocus()
            duckChatFireButton.isVisible = true
            duckChatNewChat.isVisible = true
            (duckChatInput.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
                params.marginStart = 0
                duckChatInput.layoutParams = params
            }
        }
    }

    fun showStopButton() {
        duckChatControls.visibility = View.INVISIBLE
        duckChatStop.isVisible = true
    }

    fun hideStopButton() {
        duckChatStop.isVisible = false
        duckChatControls.visibility = View.VISIBLE
    }
}
