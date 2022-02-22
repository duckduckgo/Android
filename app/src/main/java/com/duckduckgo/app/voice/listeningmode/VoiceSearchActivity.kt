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

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.duckduckgo.app.browser.databinding.ActivityVoiceSearchBinding
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.voice.listeningmode.OnDeviceSpeechRecognizer.Event.PartialResultReceived
import com.duckduckgo.app.voice.listeningmode.OnDeviceSpeechRecognizer.Event.RecognitionSuccess
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import javax.inject.Inject

class VoiceSearchActivity : DuckDuckGoActivity() {
    companion object {
        const val EXTRA_VOICE_RESULT = "extra.voice.result"
    }

    @Inject
    lateinit var speechRecognizer: OnDeviceSpeechRecognizer

    private val binding: ActivityVoiceSearchBinding by viewBinding()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
    }

    override fun onStart() {
        super.onStart()
        speechRecognizer.start {
            when (it) {
                is PartialResultReceived -> updateText(it.partialResult)
                is RecognitionSuccess -> handleSuccess(it.result)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        speechRecognizer.stop()
    }

    private fun handleSuccess(result: String) {
        updateText(result)
        Intent().apply {
            putExtra(EXTRA_VOICE_RESULT, result)
            setResult(Activity.RESULT_OK, this)
        }
        finish()
    }

    private fun updateText(result: String) {
        binding.speechResults.text = result
    }
}
