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

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build.VERSION_CODES
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import androidx.annotation.RequiresApi
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.voice.impl.VoiceSearchAvailabilityConfigProvider
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

interface LanguageSupportChecker {
    fun isLanguageSupported(): Boolean
    fun checkLanguageSupport(languageTag: String)
}

@SuppressLint("NewApi")
@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealLanguageSupportChecker @Inject constructor(
    private val context: Context,
    configProvider: VoiceSearchAvailabilityConfigProvider,
    private val languageSupportCheckerDelegate: LanguageSupportCheckerDelegate,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
) : LanguageSupportChecker {
    private var isLanguageSupported: Boolean? = null

    init {
        val config = configProvider.get()
        if (config.sdkInt >= VERSION_CODES.TIRAMISU) {
            appCoroutineScope.launch(dispatcherProvider.main()) {
                checkLanguageSupport(config.languageTag)
            }
        }
    }

    @RequiresApi(VERSION_CODES.TIRAMISU)
    override fun checkLanguageSupport(languageTag: String) {
        languageSupportCheckerDelegate.checkRecognitionSupport(
            context,
            languageTag,
            object : RecognitionSupportCallback {
                override fun onSupportResult(recognitionSupport: RecognitionSupport) {
                    isLanguageSupported = languageTag in recognitionSupport.installedOnDeviceLanguages
                }

                override fun onError(error: Int) {
                    isLanguageSupported = false
                }
            },
        )
    }

    override fun isLanguageSupported(): Boolean {
        return isLanguageSupported ?: false
    }
}
