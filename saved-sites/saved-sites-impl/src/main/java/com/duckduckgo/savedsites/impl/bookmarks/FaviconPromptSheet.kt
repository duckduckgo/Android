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

package com.duckduckgo.savedsites.impl.bookmarks

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import com.duckduckgo.saved.sites.impl.databinding.BottomSheetFaviconsPromptBinding
import com.google.android.material.bottomsheet.BottomSheetDialog

@SuppressLint("NoBottomSheetDialog")
class FaviconPromptSheet(
    builder: Builder,
) : BottomSheetDialog(builder.context) {

    private val binding = BottomSheetFaviconsPromptBinding.inflate(LayoutInflater.from(context))
    internal class DefaultEventListener : EventListener()

    abstract class EventListener {
        open fun onFaviconsFetchingPromptDismissed(fetchingEnabled: Boolean = false) {}
    }

    init {
        setContentView(binding.root)
        binding.faviconsPromptPrimaryCta.setOnClickListener {
            builder.listener.onFaviconsFetchingPromptDismissed(true)
            dismiss()
        }
        binding.faviconsPromptSecondaryCta.setOnClickListener {
            builder.listener.onFaviconsFetchingPromptDismissed()
            dismiss()
        }
        setOnCancelListener {
            builder.listener.onFaviconsFetchingPromptDismissed()
        }
    }

    /**
     * Creates a builder for an action bottom sheet dialog that uses
     * the default bottom sheet dialog theme.
     *
     * @param context the parent context
     */
    class Builder(val context: Context) {
        var dialog: BottomSheetDialog? = null
        var listener: EventListener = DefaultEventListener()

        fun addEventListener(eventListener: EventListener): Builder {
            listener = eventListener
            return this
        }

        fun show() {
            dialog = FaviconPromptSheet(this)
            dialog?.show()
        }
    }
}
