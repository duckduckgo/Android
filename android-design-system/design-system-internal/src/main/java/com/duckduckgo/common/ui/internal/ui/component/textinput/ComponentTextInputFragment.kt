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

package com.duckduckgo.common.ui.internal.ui.component.textinput

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.duckduckgo.common.ui.internal.databinding.ComponentTextInputViewBinding
import com.duckduckgo.common.ui.view.text.TextInput.Action
import com.duckduckgo.mobile.android.R
import com.google.android.material.snackbar.Snackbar

@SuppressLint("NoFragment") // we don't use DI here
class ComponentTextInputFragment : Fragment() {

    private lateinit var binding: ComponentTextInputViewBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        binding = ComponentTextInputViewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        binding.outlinedinputtext4.apply {
            setEndIcon(R.drawable.ic_copy_24)
            onAction { toastOnClick(it) }
        }
        binding.outlinedinputtext6.onAction { toastOnClick(it) }
        binding.outlinedinputtext8.onAction { toastOnClick(it) }
        binding.outlinedinputtext20.onAction { toastOnClick(it) }
        binding.outlinedinputtext30.onAction { toastOnClick(it) }
        binding.outlinedinputtext31.onAction { toastOnClick(it) }
        binding.outlinedinputtext32.onAction { toastOnClick(it) }
        binding.outlinedinputtext33.onAction { toastOnClick(it) }
        binding.outlinedinputtext21.error = "This is an error"
    }

    private fun toastOnClick(action: Action) = when (action) {
        is Action.PerformEndAction -> {
            Snackbar.make(binding.root, "Element clicked", Snackbar.LENGTH_SHORT).show()
        }
    }
}
