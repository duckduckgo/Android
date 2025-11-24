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

package com.duckduckgo.common.ui.view

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.annotation.DrawableRes
import androidx.annotation.RawRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.text.HtmlCompat
import androidx.core.view.isVisible
import com.airbnb.lottie.LottieAnimationView
import com.duckduckgo.common.ui.view.MessageCta.MessageType.REMOTE_MESSAGE
import com.duckduckgo.common.ui.view.MessageCta.MessageType.REMOTE_PROMO_MESSAGE
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.mobile.android.databinding.ViewMessageCtaBinding

class MessageCta : FrameLayout {

    private val binding: ViewMessageCtaBinding by viewBinding()
    private val remoteMessageBinding = binding.remoteMessage
    private val promoMessageBinding = binding.promoRemoteMessage

    private var onCloseButton: () -> Unit = {
        this.gone()
    }
    private var onPrimaryButtonClicked: () -> Unit = {}
    private var onSecondaryButtonClicked: () -> Unit = {}
    private var onPromoActionButtonClicked: () -> Unit = {}

    constructor(context: Context) : this(context, null)

    constructor(
        context: Context,
        attrs: AttributeSet?,
    ) : this(
        context,
        attrs,
        0,
    )

    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyle: Int,
    ) : super(context, attrs, defStyle) {

        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)

        if (Build.VERSION.SDK_INT >= 28) {
            binding.remoteMessage.remoteMessage.addBottomShadow()
            binding.promoRemoteMessage.promoMessage.addBottomShadow()
        }
    }

    fun setMessage(message: Message) {
        when (message.messageType) {
            REMOTE_MESSAGE -> setRemoteMessage(message)
            REMOTE_PROMO_MESSAGE -> setPromoMessage(message)
        }
    }

    private fun setRemoteMessage(message: Message) {
        binding.remoteMessage.root.show()
        binding.promoRemoteMessage.root.gone()

        configureTopIllustration(
            drawableRes = message.topIllustration,
            animationRes = message.topAnimation,
        )

        remoteMessageBinding.messageTitle.text = HtmlCompat.fromHtml(message.title, HtmlCompat.FROM_HTML_MODE_LEGACY)
        remoteMessageBinding.messageSubtitle.text = HtmlCompat.fromHtml(message.subtitle, HtmlCompat.FROM_HTML_MODE_LEGACY)

        if (message.action2.isEmpty()) {
            remoteMessageBinding.secondaryActionButton.gone()
        } else {
            remoteMessageBinding.secondaryActionButton.text = message.action2
            remoteMessageBinding.secondaryActionButton.show()
        }

        if (message.action.isEmpty()) {
            remoteMessageBinding.primaryActionButton.gone()
        } else {
            remoteMessageBinding.primaryActionButton.text = message.action
            message.actionIcon?.let { icon ->
                remoteMessageBinding.primaryActionButton.icon = AppCompatResources.getDrawable(context, icon)
            }
            remoteMessageBinding.primaryActionButton.show()
        }

        remoteMessageBinding.close.setOnClickListener {
            onCloseButton.invoke()
        }

        remoteMessageBinding.primaryActionButton.setOnClickListener {
            onPrimaryButtonClicked.invoke()
        }

        remoteMessageBinding.secondaryActionButton.setOnClickListener {
            onSecondaryButtonClicked.invoke()
        }
    }

    private fun configureTopIllustration(@DrawableRes drawableRes: Int?, @RawRes animationRes: Int?) {
        with(remoteMessageBinding) {
            if (animationRes != null) {
                topIllustration.gone()
                topIllustrationAnimated.setAnimation(animationRes)
                topIllustrationAnimated.show()
            } else if (drawableRes != null) {
                topIllustrationAnimated.gone()
                topIllustration.setImageDrawable(AppCompatResources.getDrawable(context, drawableRes))
                topIllustration.show()
            } else {
                topIllustration.gone()
                topIllustrationAnimated.gone()
            }
        }
    }

    private fun setPromoMessage(message: Message) {
        binding.promoRemoteMessage.root.show()
        binding.remoteMessage.root.gone()

        promoMessageBinding.messageTitle.text = message.title
        promoMessageBinding.messageSubtitle.text = HtmlCompat.fromHtml(message.subtitle, 0)
        promoMessageBinding.actionButton.text = message.promoAction

        if (message.middleIllustration == null) {
            promoMessageBinding.illustration.gone()
        } else {
            val drawable = AppCompatResources.getDrawable(context, message.middleIllustration)
            promoMessageBinding.illustration.setImageDrawable(drawable)
            promoMessageBinding.illustration.show()
        }

        promoMessageBinding.close.setOnClickListener {
            onCloseButton.invoke()
        }

        promoMessageBinding.actionButton.setOnClickListener {
            onPromoActionButtonClicked.invoke()
        }
    }

    fun onPrimaryActionClicked(onPrimaryButtonClicked: () -> Unit) {
        this.onPrimaryButtonClicked = onPrimaryButtonClicked
    }

    fun onSecondaryActionClicked(onSecondaryButtonClicked: () -> Unit) {
        this.onSecondaryButtonClicked = onSecondaryButtonClicked
    }

    fun onPromoActionClicked(onActionClicked: () -> Unit) {
        this.onPromoActionButtonClicked = onActionClicked
    }

    fun onCloseButtonClicked(onDismiss: () -> Unit) {
        this.onCloseButton = onDismiss
    }

    fun onTopAnimationConfigured(configure: (LottieAnimationView) -> Unit) {
        val view = binding.remoteMessage.topIllustrationAnimated
        if (view.isVisible) {
            configure(view)
        }
    }

    data class Message(
        @DrawableRes val topIllustration: Int? = null,
        @DrawableRes val middleIllustration: Int? = null,
        @RawRes val topAnimation: Int? = null,
        val title: String = "",
        val subtitle: String = "",
        val action: String = "",
        val actionIcon: Int? = null,
        val action2: String = "",
        val promoAction: String = "",
        val messageType: MessageType = REMOTE_MESSAGE,
    )

    enum class MessageType {
        REMOTE_MESSAGE,
        REMOTE_PROMO_MESSAGE,
    }
}
