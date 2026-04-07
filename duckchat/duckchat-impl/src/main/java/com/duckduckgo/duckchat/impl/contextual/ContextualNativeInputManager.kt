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

package com.duckduckgo.duckchat.impl.contextual

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.impl.helper.RealDuckChatJSHelper
import com.duckduckgo.duckchat.impl.ui.NativeInputModeWidget
import com.duckduckgo.js.messaging.api.JsMessaging
import com.duckduckgo.js.messaging.api.SubscriptionEventData
import com.google.android.material.card.MaterialCardView
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.json.JSONObject
import javax.inject.Inject

interface ContextualNativeInputManager {
    fun init(
        card: MaterialCardView,
        widget: NativeInputModeWidget,
        jsMessaging: JsMessaging,
        lifecycleOwner: LifecycleOwner,
        onSearchSubmitted: (String) -> Unit,
        onImageButtonPressed: () -> Unit = {},
    )

    fun onWebViewMode()
    fun onInputMode()
}

@ContributesBinding(FragmentScope::class)
class RealContextualNativeInputManager @Inject constructor(
    private val duckChat: DuckChat,
) : ContextualNativeInputManager {

    private var isNativeInputEnabled = false
    private var card: MaterialCardView? = null
    private var jsMessaging: JsMessaging? = null

    override fun init(
        card: MaterialCardView,
        widget: NativeInputModeWidget,
        jsMessaging: JsMessaging,
        lifecycleOwner: LifecycleOwner,
        onSearchSubmitted: (String) -> Unit,
        onImageButtonPressed: () -> Unit,
    ) {
        this.card = card
        this.jsMessaging = jsMessaging

        applyCardShape(card)
        setupWidget(widget, onSearchSubmitted, onImageButtonPressed)
        observeNativeInputSetting(lifecycleOwner)
    }

    override fun onWebViewMode() {
        if (isNativeInputEnabled) card?.show() else card?.gone()
    }

    override fun onInputMode() {
        card?.gone()
    }

    private fun applyCardShape(card: MaterialCardView) {
        val radius = card.resources.getDimension(
            com.duckduckgo.mobile.android.R.dimen.extraLargeShapeCornerRadius,
        )
        card.shapeAppearanceModel = card.shapeAppearanceModel.toBuilder()
            .setTopLeftCornerSize(radius)
            .setTopRightCornerSize(radius)
            .setBottomLeftCornerSize(0f)
            .setBottomRightCornerSize(0f)
            .build()
    }

    private fun setupWidget(widget: NativeInputModeWidget, onSearchSubmitted: (String) -> Unit, onImageButtonPressed: () -> Unit) {
        widget.selectChatTab()
        widget.hideMainButtons()
        widget.onStopTapped = ::sendStopEvent
        widget.onImageClick = onImageButtonPressed
        widget.bindInputEvents(
            onSearchTextChanged = { },
            onSearchSubmitted = { query ->
                widget.hideKeyboard()
                onSearchSubmitted(query)
            },
            onChatSubmitted = { prompt ->
                sendPrompt(prompt, widget.getSelectedModelId())
                widget.text = ""
            },
        )
    }

    private fun observeNativeInputSetting(lifecycleOwner: LifecycleOwner) {
        duckChat.observeNativeInputFieldUserSettingEnabled()
            .onEach { isEnabled -> isNativeInputEnabled = isEnabled }
            .launchIn(lifecycleOwner.lifecycleScope)
    }

    private fun sendPrompt(prompt: String, modelId: String? = null) {
        val params = JSONObject().apply {
            put("platform", "android")
            put("tool", "query")
            put(
                "query",
                JSONObject().apply {
                    put("prompt", prompt)
                    put("autoSubmit", true)
                    if (modelId != null) {
                        put("modelId", modelId)
                    }
                },
            )
        }
        jsMessaging?.sendSubscriptionEvent(
            SubscriptionEventData(
                featureName = RealDuckChatJSHelper.DUCK_CHAT_FEATURE_NAME,
                subscriptionName = "submitAIChatNativePrompt",
                params = params,
            ),
        )
    }

    private fun sendStopEvent() {
        jsMessaging?.sendSubscriptionEvent(
            SubscriptionEventData(
                featureName = RealDuckChatJSHelper.DUCK_CHAT_FEATURE_NAME,
                subscriptionName = "submitPromptInterruption",
                params = JSONObject("{}"),
            ),
        )
    }
}
