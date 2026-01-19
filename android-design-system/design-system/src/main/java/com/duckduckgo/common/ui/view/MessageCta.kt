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
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.AttributeSet
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.annotation.RawRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.text.HtmlCompat
import androidx.core.view.isVisible
import com.airbnb.lottie.LottieAnimationView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.duckduckgo.common.ui.view.MessageCta.MessageType.REMOTE_MESSAGE
import com.duckduckgo.common.ui.view.MessageCta.MessageType.REMOTE_PROMO_MESSAGE
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.mobile.android.databinding.ViewMessageCtaBinding
import java.io.File

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

    private var onRemoteImageLoadFailed: () -> Unit = {}
    private var onRemoteImageLoadSuccess: () -> Unit = {}

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
            imageUrl = message.imageUrl,
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

    private fun configureTopIllustration(
        imageUrl: String?,
        @DrawableRes drawableRes: Int?,
        @RawRes animationRes: Int?,
    ) {
        with(remoteMessageBinding) {
            when {
                imageUrl.orEmpty().isNotEmpty() -> {
                    loadImageUrl(topImage, imageUrl.orEmpty(), drawableRes)
                    topImage.show()
                    topIllustration.gone()
                    topIllustrationAnimated.gone()
                }

                animationRes != null -> {
                    topIllustration.gone()
                    topImage.gone()
                    topIllustrationAnimated.setAnimation(animationRes)
                    topIllustrationAnimated.show()
                }

                drawableRes != null -> {
                    topImage.gone()
                    topIllustrationAnimated.gone()
                    loadImageDrawable(topIllustration, drawableRes)
                }

                else -> {
                    topImage.gone()
                    topIllustration.gone()
                    topIllustrationAnimated.gone()
                }
            }
        }
    }

    private fun loadImageUrl(
        imageView: ImageView,
        imageUrl: String,
        @DrawableRes drawableRes: Int?,
    ) {
        // Check if imageUrl is a local file path
        val imageSource: Any = if (imageUrl.startsWith("/")) {
            File(imageUrl)
        } else {
            imageUrl
        }

        Glide
            .with(imageView)
            .load(imageSource)
            .apply {
                if (drawableRes != null) {
                    error(AppCompatResources.getDrawable(context, drawableRes))
                }
            }
            .addListener(
                object : RequestListener<Drawable> {
                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: Target<Drawable?>,
                        isFirstResource: Boolean,
                    ): Boolean {
                        onRemoteImageLoadFailed()
                        if (drawableRes == null) {
                            imageView.gone()
                        }
                        return false
                    }

                    override fun onResourceReady(
                        resource: Drawable,
                        model: Any,
                        target: Target<Drawable?>?,
                        dataSource: DataSource,
                        isFirstResource: Boolean,
                    ): Boolean {
                        onRemoteImageLoadSuccess()
                        return false
                    }
                },
            )
            .centerCrop()
            .transition(withCrossFade())
            .into(imageView)
    }

    private fun loadImageDrawable(
        topIllustration: ImageView,
        @DrawableRes drawableRes: Int,
    ) {
        topIllustration.setImageDrawable(AppCompatResources.getDrawable(context, drawableRes))
        topIllustration.show()
    }

    private fun setPromoMessage(message: Message) {
        binding.promoRemoteMessage.root.show()
        binding.remoteMessage.root.gone()

        promoMessageBinding.messageTitle.text = message.title
        promoMessageBinding.messageSubtitle.text = HtmlCompat.fromHtml(message.subtitle, 0)
        promoMessageBinding.actionButton.text = message.promoAction

        if (message.imageUrl.orEmpty().isNotEmpty()) {
            loadImageUrl(promoMessageBinding.remoteImage, message.imageUrl.orEmpty(), message.middleIllustration)
            promoMessageBinding.remoteImage.show()
            promoMessageBinding.illustration.gone()
        } else if (message.middleIllustration == null) {
            promoMessageBinding.illustration.gone()
            promoMessageBinding.remoteImage.gone()
        } else {
            val drawable = AppCompatResources.getDrawable(context, message.middleIllustration)
            promoMessageBinding.illustration.setImageDrawable(drawable)
            promoMessageBinding.remoteImage.gone()
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

    fun onRemoteImageLoadSuccess(onRemoteImageLoadSuccess: () -> Unit) {
        this.onRemoteImageLoadSuccess = onRemoteImageLoadSuccess
    }

    fun onRemoteImageLoadFailed(onRemoteImageLoadFailed: () -> Unit) {
        this.onRemoteImageLoadFailed = onRemoteImageLoadFailed
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
        val imageUrl: String? = null,
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
