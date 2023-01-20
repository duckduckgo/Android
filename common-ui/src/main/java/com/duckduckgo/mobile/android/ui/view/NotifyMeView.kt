/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.mobile.android.ui.view

import android.content.Context
import android.util.AttributeSet
import androidx.constraintlayout.widget.ConstraintLayout
import com.duckduckgo.mobile.android.databinding.ViewNotifyMeViewBinding
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding

class NotifyMeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : ConstraintLayout(context, attrs) {

    private val binding: ViewNotifyMeViewBinding by viewBinding()

    private var onCloseButton: () -> Unit = {
        this.gone()
    }

    private var onNotifyMeButtonClicked: () -> Unit = {}

    init {
        binding.notifyMeClose.setOnClickListener {
            onCloseButton.invoke()
        }

        binding.notifyMeButton.setOnClickListener {
            onNotifyMeButtonClicked.invoke()
        }
    }

    fun setTitle(title: String) {
        binding.notifyMeMessageTitle.text = title
    }

    fun setSubtitle(subtitle: String) {
        binding.notifyMeMessageSubtitle.text = subtitle
    }

    fun onNotifyMeButtonClicked(onNotifyMeButtonClicked: () -> Unit) {
        this.onNotifyMeButtonClicked = onNotifyMeButtonClicked
    }

    fun onCloseButtonClicked(onDismiss: () -> Unit) {
        this.onCloseButton = onDismiss
    }
}
