/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.voice.impl.language

import android.content.Context
import android.content.Intent
import android.os.Build.VERSION_CODES
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer.createOnDeviceSpeechRecognizer
import androidx.annotation.RequiresApi
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import java.util.concurrent.Executors
import javax.inject.Inject
import logcat.LogPriority.ERROR
import logcat.asLog
import logcat.logcat

interface LanguageSupportCheckerDelegate {
    fun checkRecognitionSupport(context: Context, languageTag: String, callback: RecognitionSupportCallback)
}

@ContributesBinding(AppScope::class)
class RealLanguageSupportCheckerDelegate @Inject constructor() : LanguageSupportCheckerDelegate {
    @RequiresApi(VERSION_CODES.TIRAMISU)
    override fun checkRecognitionSupport(context: Context, languageTag: String, callback: RecognitionSupportCallback) {
        runCatching {
            createOnDeviceSpeechRecognizer(context).checkRecognitionSupport(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH),
                Executors.newSingleThreadExecutor(),
                callback,
            )
        }.onFailure {
            logcat(ERROR) { "Failed to check voice recognition support: ${it.asLog()}" }
        }
    }
}
