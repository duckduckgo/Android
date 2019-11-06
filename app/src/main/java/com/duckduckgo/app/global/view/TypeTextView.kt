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
import android.os.Handler
import android.util.AttributeSet
import android.widget.TextView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class TypeWriter @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : TextView(context, attrs, defStyleAttr) {

    private var textEntry: CharSequence = ""
    private var index: Int = 0
    private var myDelay: Long = 20
    private val textHandler = Handler()

    fun runAnimation(afterAnimation: () -> Unit) = object : Runnable {
        override fun run() {
            text = textEntry.subSequence(0, index++)
            if (index <= textEntry.length) {
                handler.postDelayed(this, myDelay)
            } else {
                afterAnimation()
            }
        }
    }

    fun animate(text: CharSequence, afterAnimation: () -> Unit = {}) {
        textEntry = textEntry.toString() + text
        index = this.text.length
        textHandler.removeCallbacks(runAnimation(afterAnimation))
        textHandler.postDelayed(runAnimation(afterAnimation), myDelay)
    }

    suspend fun animate2(myText: CharSequence, afterAnimation: () -> Unit = {}) {
        textEntry = myText
        index = 0
        withContext(Dispatchers.Main) {
            launch {
                while(index < textEntry.length) {
                    text = textEntry.subSequence(0, index++)
                    delay(myDelay)
                }
                afterAnimation()
            }
        }

        textHandler.removeCallbacks(runAnimation(afterAnimation))
        textHandler.postDelayed(runAnimation(afterAnimation), myDelay)
    }

    fun setDelay(millis: Long) {
        myDelay = millis
    }
}
