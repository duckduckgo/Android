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

import android.content.Context
import android.content.Intent
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.RequiresApi
import com.duckduckgo.app.voice.listeningmode.OnDeviceSpeechRecognizer.Event
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import timber.log.Timber
import javax.inject.Inject

interface OnDeviceSpeechRecognizer {
    companion object {
        const val MAX_VOLUME = 10f
        const val MIN_VOLUME = 0f
    }

    fun start(eventHandler: (Event) -> Unit)
    fun stop()

    sealed class Event {
        data class PartialResultReceived(val partialResult: String) : Event()
        data class RecognitionSuccess(val result: String) : Event()
        data class VolumeUpdateReceived(val normalizedVolume: Float) : Event()
    }
}

@RequiresApi(VERSION_CODES.S)
@ContributesBinding(AppScope::class)
class DefaultOnDeviceSpeechRecognizer @Inject constructor(
    private val context: Context
) : OnDeviceSpeechRecognizer {

    private var speechRecognizer: SpeechRecognizer? = null

    private val speechRecognizerIntent: Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).run {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            // Do nothing. Speech recognizer is ready to detect speech
        }

        override fun onBeginningOfSpeech() {
            // Do nothing. User has started speaking
        }

        override fun onRmsChanged(rmsdB: Float) {
            _eventHandler(
                Event.VolumeUpdateReceived(rmsdB.clean())
            )
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            // Do nothing. User has finished speaking.
        }

        override fun onError(error: Int) {
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH -> speechRecognizer?.restartListening()
                else -> Timber.e("SpeechRecognizer error: $error")
            }
        }

        override fun onResults(results: Bundle?) {
            _eventHandler(
                Event.RecognitionSuccess(
                    results?.extractResult() ?: ""
                )
            )
        }

        override fun onPartialResults(partialResults: Bundle?) {
            _eventHandler(
                Event.PartialResultReceived(
                    partialResults?.extractResult() ?: ""
                )
            )
        }

        override fun onEvent(
            eventType: Int,
            params: Bundle?
        ) {
        }
    }

    private var _eventHandler: (Event) -> Unit = {}

    override fun start(eventHandler: (Event) -> Unit) {
        _eventHandler = eventHandler
        speechRecognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        speechRecognizer?.setRecognitionListener(recognitionListener)
        speechRecognizer?.startListening(speechRecognizerIntent)
    }

    override fun stop() {
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
    }

    private fun SpeechRecognizer.restartListening() {
        stopListening()
        startListening(speechRecognizerIntent)
    }

    private fun Bundle.extractResult(): String {
        val result = getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.toList()
        return if (result.isNullOrEmpty()) "" else result[0]
    }

    private fun Float.clean(): Float {
        // We disregard any negative value since they tend to be emitted even if the user is not speaking
        return when {
            this <= OnDeviceSpeechRecognizer.MIN_VOLUME -> OnDeviceSpeechRecognizer.MIN_VOLUME
            this > OnDeviceSpeechRecognizer.MAX_VOLUME -> OnDeviceSpeechRecognizer.MAX_VOLUME
            else -> this
        }
    }
}
