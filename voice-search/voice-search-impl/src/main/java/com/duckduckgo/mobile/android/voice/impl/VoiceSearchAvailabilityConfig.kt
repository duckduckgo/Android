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

package com.duckduckgo.mobile.android.voice.impl

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build.VERSION_CODES
import android.speech.SpeechRecognizer
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

data class VoiceSearchAvailabilityConfig(
    val deviceModel: String,
    val sdkInt: Int,
    val languageTag: String,
    val isOnDeviceSpeechRecognitionSupported: Boolean,
)

interface VoiceSearchAvailabilityConfigProvider {
    fun get(): VoiceSearchAvailabilityConfig
}

@ContributesBinding(AppScope::class)
class DefaultVoiceSearchAvailabilityConfigProvider @Inject constructor(
    private val context: Context,
    private val appBuildConfig: AppBuildConfig
) : VoiceSearchAvailabilityConfigProvider {

    @SuppressLint("NewApi")
    override fun get(): VoiceSearchAvailabilityConfig = VoiceSearchAvailabilityConfig(
        deviceModel = appBuildConfig.model,
        sdkInt = appBuildConfig.sdkInt,
        languageTag = appBuildConfig.deviceLocale.toLanguageTag(),
        isOnDeviceSpeechRecognitionSupported = if (appBuildConfig.sdkInt >= VERSION_CODES.S) {
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        } else false
    )
}
