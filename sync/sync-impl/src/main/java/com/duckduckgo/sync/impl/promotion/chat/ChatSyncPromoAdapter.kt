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

package com.duckduckgo.sync.impl.promotion.chat

import android.animation.ValueAnimator
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import androidx.core.animation.doOnEnd
import androidx.core.view.doOnLayout
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.recyclerview.widget.RecyclerView.Adapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.duckduckgo.common.ui.view.MessageCta
import com.duckduckgo.sync.impl.R.drawable
import com.duckduckgo.sync.impl.R.string
import com.duckduckgo.sync.impl.databinding.ItemChatSyncPromoBinding
import kotlin.math.roundToInt

internal class ChatSyncPromoAdapter(
    private val listener: Listener,
) : Adapter<ChatSyncPromoViewHolder>() {
    interface Listener {
        fun onSyncWithDeviceClicked(adapter: ChatSyncPromoAdapter)

        fun onDismissClicked(adapter: ChatSyncPromoAdapter)

        fun onBannerShown(adapter: ChatSyncPromoAdapter)
    }

    private enum class State {
        Dismissed,
        Dismissing,
        Shown,
    }

    private var state = State.Dismissed

    private val mainHandler = Handler(Looper.getMainLooper())

    fun show() {
        when (state) {
            State.Dismissed -> {
                state = State.Shown
                notifyItemInserted(0)
            }
            State.Dismissing -> {
                state = State.Shown
                notifyItemChanged(0)
            }
            State.Shown -> Unit
        }
    }

    fun dismiss(shouldAnimate: Boolean) {
        when (state) {
            State.Shown -> {
                if (shouldAnimate) {
                    state = State.Dismissing
                    notifyItemChanged(0)
                } else {
                    state = State.Dismissed
                    notifyItemRemoved(0)
                }
            }
            State.Dismissed, State.Dismissing -> Unit
        }
    }

    override fun getItemCount(): Int = when (state) {
        State.Dismissed -> 0
        State.Dismissing -> 1
        State.Shown -> 1
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ChatSyncPromoViewHolder {
        val binding = ItemChatSyncPromoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ChatSyncPromoViewHolder(
            binding = binding,
            onSyncClicked = { listener.onSyncWithDeviceClicked(this) },
            onDismissClicked = { listener.onDismissClicked(this) },
            onShown = { listener.onBannerShown(this) },
        )
    }

    override fun onBindViewHolder(
        holder: ChatSyncPromoViewHolder,
        position: Int,
    ) {
        when (state) {
            State.Dismissed -> holder.hide()
            State.Dismissing -> holder.animateOut {
                // Defer the removal a frame: notifyItemRemoved() can't run during the RecyclerView
                // layout/animation pass. By the time this runs the banner may have been re-shown, so
                // re-check the state before removing.
                mainHandler.post {
                    if (state != State.Dismissing) return@post
                    state = State.Dismissed
                    notifyItemRemoved(0)
                }
            }
            State.Shown -> holder.show()
        }
    }

    override fun onViewRecycled(holder: ChatSyncPromoViewHolder) {
        holder.cancelAnimation()
    }
}

internal class ChatSyncPromoViewHolder(
    private val binding: ItemChatSyncPromoBinding,
    private val onSyncClicked: () -> Unit,
    private val onDismissClicked: () -> Unit,
    private val onShown: () -> Unit,
) : ViewHolder(binding.root) {
    private var knownHeight: Int? = null
    private var dismissAnimator: ValueAnimator? = null

    init {
        binding.syncPromotion.apply {
            setMessage(
                MessageCta.Message(
                    topIllustration = drawable.ic_chat_sync_72,
                    title = context.getString(string.sync_chat_promo_banner_title),
                    action = context.getString(string.sync_chat_promo_banner_cta_title),
                ),
            )
            onPrimaryActionClicked(onSyncClicked)
            onCloseButtonClicked(onDismissClicked)
        }
    }

    fun show() {
        cancelAnimation()
        binding.root.apply {
            alpha = 1f
            isVisible = true
            updateLayoutParams { height = LayoutParams.WRAP_CONTENT }
            doOnLayout { view -> knownHeight = view.height }
            doOnPreDraw { onShown() }
        }
    }

    fun hide() {
        cancelAnimation()
        binding.root.isVisible = false
    }

    // We collapse the row (height + alpha) ourselves instead of letting RecyclerView's
    // ItemAnimator animate the removal: the lists that host this banner disable their
    // ItemAnimator (e.g. autoCompleteList.itemAnimator = null in NativeInputManager), so a
    // plain notifyItemRemoved() would just make the row disappear with no animation.
    fun animateOut(onComplete: () -> Unit) {
        if (dismissAnimator != null) return
        val startHeight = knownHeight ?: return

        cancelAnimation()
        dismissAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = EXIT_ANIM_DURATION
            interpolator = FastOutSlowInInterpolator()
            addUpdateListener { animation ->
                val progress = animation.animatedValue as Float
                binding.root.alpha = progress
                binding.root.updateLayoutParams { height = (startHeight * progress).roundToInt() }
            }
            doOnEnd {
                dismissAnimator = null
                onComplete()
            }
            start()
        }
    }

    fun cancelAnimation() {
        dismissAnimator?.cancel()
    }

    private companion object {
        const val EXIT_ANIM_DURATION = 300L
    }
}
