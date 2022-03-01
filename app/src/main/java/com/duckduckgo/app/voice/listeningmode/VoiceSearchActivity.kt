/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.app.voice.listeningmode

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND
import android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.app.browser.databinding.ActivityVoiceSearchBinding
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.voice.listeningmode.VoiceSearchViewModel.Command
import com.duckduckgo.app.voice.listeningmode.ui.VoiceRecognizingIndicator.Model
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.util.Locale

class VoiceSearchActivity : DuckDuckGoActivity() {
    companion object {
        const val EXTRA_VOICE_RESULT = "extra.voice.result"
    }

    private val viewModel: VoiceSearchViewModel by bindViewModel()
    private val binding: ActivityVoiceSearchBinding by viewBinding()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addBackgroundBlur()
        setContentView(binding.root)
    }

    private fun addBackgroundBlur() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // It is important to set FLAG_DIM_BEHIND and dimAmount since without it there will be a lag on the blur effect
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window.addFlags(FLAG_BLUR_BEHIND)
            window.addFlags(FLAG_DIM_BEHIND)
            window.attributes.blurBehindRadius = 70
            window.attributes.dimAmount = 0.001f
            setTranslucent(true)
        }
    }

    override fun onStart() {
        super.onStart()
        observeViewModel()
        viewModel.start()
    }

    override fun onStop() {
        super.onStop()
        viewModel.stop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.indicator.destroy()
    }

    private fun observeViewModel() {
        viewModel.viewState()
            .flowWithLifecycle(lifecycle, Lifecycle.State.RESUMED)
            .onEach {
                if (it.result.isNotEmpty()) updateText(it.result)
            }.launchIn(lifecycleScope)

        viewModel.commands()
            .flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
            .onEach {
                when (it) {
                    is Command.UpdateVoiceIndicator -> handleVolume(it.volume)
                    is Command.HandleSpeechRecognitionSuccess -> handleSuccess(it.result)
                }
            }
            .launchIn(lifecycleScope)
    }

    private fun handleVolume(normalizedVolume: Float) {
        if (normalizedVolume != 0f) binding.indicator.bind(Model(normalizedVolume))
    }

    private fun handleSuccess(result: String) {
        updateText(result)
        Intent().apply {
            putExtra(EXTRA_VOICE_RESULT, result.capitalizeFirstLetter())
            setResult(Activity.RESULT_OK, this)
        }
        finish()
    }

    @SuppressLint("SetTextI18n")
    private fun updateText(result: String) {
        binding.speechResults.text = "\"${result.capitalizeFirstLetter()}\""
    }

    private fun String.capitalizeFirstLetter() = this.replaceFirstChar {
        if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
    }
}
