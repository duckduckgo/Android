/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.app.global.view

import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext


class TypeAnimationTextView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : TextView(context, attrs, defStyleAttr), CoroutineScope {

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + animationJob

    private val animationJob: Job = Job()
    private var typingAnimationJob: Job? = null

    var typingDelayInMs: Long = 20
    var delayAfterAnimationInMs: Long = 300
    private lateinit var inputText: CharSequence

    fun startTypingAnimation(inputText: CharSequence, isCancellable: Boolean = true, afterAnimation: () -> Unit = {}) {
        this.inputText = inputText

        if (isCancellable) {
            setOnClickListener {
                if ((typingAnimationJob as Job).isActive) {
                    cancelAnimation()
                    text = inputText
                    afterAnimation()
                }
            }
        }

        typingAnimationJob = launch {
            inputText.mapIndexed { index, _ ->
                text = inputText.subSequence(0, index)
                delay(typingDelayInMs)
            }
            delay(delayAfterAnimationInMs)
            afterAnimation()
        }
    }

    fun cancelAnimation() = typingAnimationJob?.cancel()
}