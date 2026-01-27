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

package com.duckduckgo.duckchat.impl.ui

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import com.duckduckgo.common.ui.view.text.DaxTextView
import com.duckduckgo.duckchat.api.DuckChatNativeSettingsNoParams
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.store.DuckChatDataStore
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@SuppressLint("NoBottomSheetDialog")
class DuckAiContextualOnboardingBottomSheetDialog(
    context: Context,
    private val coroutineScope: CoroutineScope,
    private val duckChatDataStore: DuckChatDataStore,
    private val globalActivityStarter: GlobalActivityStarter,
) : BottomSheetDialog(context) {

    var eventListener: EventListener? = null

    init {
        val view = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_duck_ai_contextual_onboarding, null)
        setContentView(view)

        setCancelable(false)
        setCanceledOnTouchOutside(false)
        behavior.isDraggable = false

        view.findViewById<DaxTextView>(R.id.duckAiContextualOnboardingTitle).text =
            context.getString(R.string.duck_chat_contextual_onboarding_title)
        view.findViewById<DaxTextView>(R.id.duckAiContextualOnboardingBody).text =
            context.getString(R.string.duck_chat_contextual_onboarding_body)

        view.findViewById<View>(R.id.duckAiContextualOnboardingPrimaryButton).setOnClickListener {
            coroutineScope.launch {
                duckChatDataStore.setContextualOnboardingDismissed(true)
            }
            dismiss()
        }

        view.findViewById<View>(R.id.duckAiContextualOnboardingSecondaryButton).setOnClickListener {
            coroutineScope.launch {
                duckChatDataStore.setContextualOnboardingDismissed(true)
            }
            globalActivityStarter.start(context, DuckChatNativeSettingsNoParams)
            dismiss()
        }

        setOnDismissListener {
            eventListener?.onDismissed()
        }
    }

    interface EventListener {
        fun onDismissed()
    }
}
