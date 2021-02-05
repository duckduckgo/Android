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
import androidx.appcompat.widget.AppCompatTextView
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

class TypeAnimationTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr), CoroutineScope {

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + Job()

    private var typingAnimationJob: Job? = null
    private var delayAfterAnimationInMs: Long = 300
    var typingDelayInMs: Long = 20

    fun startTypingAnimation(textDialog: String, isCancellable: Boolean = true, afterAnimation: () -> Unit = {}) {
        val inputText = textDialog.html(context)
        if (isCancellable) {
            setOnClickListener {
                if (typingAnimationJob?.isActive == true) {
                    finishAnimation(textDialog)
                    afterAnimation()
                }
            }
        }

        typingAnimationJob = launch {
            inputText.mapIndexed { index, _ ->
                text = inputText.subSequence(0, index + 1)
                delay(typingDelayInMs)
            }
            delay(delayAfterAnimationInMs)
            afterAnimation()
        }
    }

    fun isAnimationFinished() = typingAnimationJob?.isActive == false

    fun finishAnimation(textDialog: String) {
        typingAnimationJob?.cancel()
        text = textDialog.html(context)
    }

    fun cancelAnimation() = typingAnimationJob?.cancel()

    override fun onDetachedFromWindow() {
        cancelAnimation()
        super.onDetachedFromWindow()
    }
}
