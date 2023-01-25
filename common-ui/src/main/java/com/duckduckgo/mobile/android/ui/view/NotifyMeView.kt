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
import android.widget.FrameLayout
import com.duckduckgo.mobile.android.R
import com.duckduckgo.mobile.android.databinding.ViewNotifyMeViewBinding
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding

class NotifyMeView : FrameLayout {

    constructor(context: Context) : this(context, null)

    constructor(
        context: Context,
        attrs: AttributeSet?,
    ) : this(
        context,
        attrs,
        R.style.Widget_DuckDuckGo_NotifyMeView,
    )

    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyle: Int,
    ) : super(context, attrs, defStyle) {
        val attributes = context.obtainStyledAttributes(attrs, R.styleable.NotifyMeView)

        setTitle(attributes.getString(R.styleable.NotifyMeView_titleText) ?: "")
        setSubtitle(attributes.getString(R.styleable.NotifyMeView_subtitleText) ?: "")

        binding.notifyMeClose.setOnClickListener {
            onCloseButton.invoke()
        }
        binding.notifyMeButton.setOnClickListener {
            onNotifyMeButtonClicked.invoke()
        }

        attributes.recycle()
    }

    private val binding: ViewNotifyMeViewBinding by viewBinding()

    private var onCloseButton: () -> Unit = {}
    private var onNotifyMeButtonClicked: () -> Unit = {}

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
