/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.sync.impl.ui.v2

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity.CENTER_HORIZONTAL
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.updateMargins
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.di.scopes.ActivityScope

@InjectWith(ActivityScope::class)
class RecoveryCodeActivity : DuckDuckGoActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        @SuppressLint("SetTextI18n")
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            setBackgroundColor(Color.WHITE)

            addView(
                TextView(context).apply {
                    layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, CENTER_HORIZONTAL).apply {
                        updateMargins(top = 60, bottom = 60)
                    }
                    setTextColor(Color.BLACK)
                    text = "Connected device: ${intent.getStringExtra("device")}"
                },
            )

            addView(
                Button(context).apply {
                    layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, CENTER_HORIZONTAL)
                    setBackgroundColor(Color.BLACK)
                    setTextColor(Color.WHITE)
                    text = "Done"
                    setOnClickListener { finish() }
                },
            )
        }

        setContentView(container)
    }
}
