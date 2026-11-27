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

package com.duckduckgo.pagetranslation.impl.banner

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import com.duckduckgo.pagetranslation.impl.R
import com.duckduckgo.pagetranslation.impl.databinding.ViewTranslationBannerBinding
import com.duckduckgo.pagetranslation.impl.language.DeviceLanguages
import com.duckduckgo.pagetranslation.impl.offer.ActiveOffer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.math.abs

class TranslationBannerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private var controller: BannerController? = null
    private var deviceLanguages: DeviceLanguages? = null
    private var mainDispatcher: CoroutineDispatcher? = null
    private var scope: CoroutineScope? = null

    private val binding = ViewTranslationBannerBinding.inflate(LayoutInflater.from(context), this)
    private val card: View = binding.translationBannerCard
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val minFlingVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity * FLING_FACTOR
    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var velocityTracker: VelocityTracker? = null

    init {
        visibility = GONE
        clipChildren = false
        ViewCompat.setAccessibilityLiveRegion(card, ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE)
        ViewCompat.addAccessibilityAction(card, context.getString(R.string.pageTranslationBannerDismissAction)) { _, _ ->
            controller?.onDismiss(Dismissal.AccessibilityAction)
            true
        }
    }

    fun bind(
        controller: BannerController,
        deviceLanguages: DeviceLanguages,
        mainDispatcher: CoroutineDispatcher,
    ) {
        this.controller = controller
        this.deviceLanguages = deviceLanguages
        this.mainDispatcher = mainDispatcher
        binding.translationBannerButton.setOnClickListener { controller.onTranslate() }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val controller = controller ?: return
        val dispatcher = mainDispatcher ?: return
        val newScope = CoroutineScope(SupervisorJob() + dispatcher)
        scope = newScope
        controller.state.onEach { render(it) }.launchIn(newScope)
        newScope.launch { controller.run() }
        updateOnScreen()
    }

    override fun onDetachedFromWindow() {
        controller?.setOnScreen(false)
        controller?.setInteracting(false)
        scope?.cancel()
        scope = null
        card.animate().cancel()
        velocityTracker?.recycle()
        velocityTracker = null
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val params = card.layoutParams as MarginLayoutParams
        val available = MeasureSpec.getSize(widthMeasureSpec) - params.marginStart - params.marginEnd
        val maxWidth = resources.getDimensionPixelSize(R.dimen.translationBannerMaxWidth)
        params.width = if (available > maxWidth) maxWidth else LayoutParams.MATCH_PARENT
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        updateOnScreen()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        updateOnScreen()
    }

    private fun updateOnScreen() {
        controller?.setOnScreen(isShown && windowVisibility == VISIBLE)
    }

    private fun render(state: BannerState) {
        when (state) {
            is BannerState.Showing -> show(state.offer)
            is BannerState.Hidden -> hide(state.reason)
        }
    }

    private fun show(offer: ActiveOffer) {
        val names = deviceLanguages ?: return
        val subtitle = context.getString(
            R.string.pageTranslationPromptLanguages,
            names.displayName(offer.pair.source),
            names.displayName(offer.pair.target),
        )
        binding.translationBannerLanguages.text = subtitle
        card.contentDescription = "${context.getString(R.string.pageTranslationPromptTitle)} $subtitle"
        val wasVisible = isVisible
        card.animate().cancel()
        card.translationX = 0f
        if (!wasVisible) card.alpha = 0f
        visibility = VISIBLE
        card.animate().alpha(1f).setDuration(FADE_MILLIS).start()
    }

    private fun hide(reason: HiddenReason) {
        if (!isVisible) return
        card.animate().cancel()
        val swiped = reason is HiddenReason.Dismissed && reason.by == Dismissal.Swipe
        if (swiped) {
            visibility = GONE
        } else {
            card.animate().alpha(0f).setDuration(FADE_MILLIS).withEndAction { visibility = GONE }.start()
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        track(ev)
        return dragging
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        track(ev)
        if (dragging) {
            when (ev.actionMasked) {
                MotionEvent.ACTION_MOVE -> card.translationX = ev.rawX - downX
                MotionEvent.ACTION_UP -> finishDrag(cancelled = false)
                MotionEvent.ACTION_CANCEL -> finishDrag(cancelled = true)
            }
        }
        // Claim the gesture only on the card so a swipe can start on its text, while a tap on the padding falls through.
        return if (ev.actionMasked == MotionEvent.ACTION_DOWN) isOnCard(ev) else dragging
    }

    private fun isOnCard(ev: MotionEvent) = ev.x >= card.left && ev.x <= card.right && ev.y >= card.top && ev.y <= card.bottom

    private fun track(ev: MotionEvent) {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.rawX
                downY = ev.rawY
                dragging = false
                if (isOnCard(ev)) controller?.setInteracting(true)
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain()
            }

            MotionEvent.ACTION_MOVE -> {
                if (!dragging) {
                    val dx = ev.rawX - downX
                    val dy = ev.rawY - downY
                    if (abs(dx) > slop && abs(dx) > abs(dy)) {
                        dragging = true
                        card.translationX = dx
                        parent?.requestDisallowInterceptTouchEvent(true)
                    }
                }
            }
        }
        if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) controller?.setInteracting(false)
        velocityTracker?.addMovement(ev)
    }

    private fun finishDrag(cancelled: Boolean) {
        dragging = false
        val tracker = velocityTracker
        tracker?.computeCurrentVelocity(1000)
        val velocityX = tracker?.xVelocity ?: 0f
        tracker?.recycle()
        velocityTracker = null

        val travelled = card.translationX
        val flung = abs(velocityX) > minFlingVelocity && velocityX * travelled > 0
        if (!cancelled && (abs(travelled) > width * DISMISS_FRACTION || flung)) {
            val target = if (travelled >= 0) width.toFloat() else -width.toFloat()
            card.animate().translationX(target).alpha(0f).setDuration(FADE_MILLIS)
                .withEndAction { controller?.onDismiss(Dismissal.Swipe) }.start()
        } else {
            card.animate().translationX(0f).setDuration(FADE_MILLIS).start()
        }
    }

    private companion object {
        const val FADE_MILLIS = 150L
        const val DISMISS_FRACTION = 0.35f
        const val FLING_FACTOR = 5
    }
}
