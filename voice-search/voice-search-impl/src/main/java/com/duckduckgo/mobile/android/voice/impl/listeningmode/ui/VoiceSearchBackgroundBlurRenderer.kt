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

package com.duckduckgo.mobile.android.voice.impl.listeningmode.ui

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build.VERSION_CODES
import android.view.View
import androidx.annotation.RequiresApi

@RequiresApi(VERSION_CODES.S)
fun View.addBlur() {
    this.setRenderEffect(
        RenderEffect.createBlurEffect(70f, 70f, Shader.TileMode.MIRROR)
    )
}

@RequiresApi(VERSION_CODES.S)
fun View.removeBlur() {
    this.setRenderEffect(null)
}
